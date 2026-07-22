package org.aprsdroid.app

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.ab0oo.aprs.parser.APRSPacket
import net.ab0oo.aprs.parser.MessagePacket
import org.aprsdroid.app.data.MessageEntity

/**
 * Kotlin port of the Scala `MessageService`.
 *
 * Handles incoming APRS text messages (storing, notifying, ACKing)
 * and drives the outgoing-message retry loop. Retries use an
 * exponential backoff: `RETRY_INTERVAL * 2^(retry-1)` seconds,
 * capped at `NUM_OF_RETRIES` attempts.
 */
class MessageService(private val s: AprsService) {

    private val TAG = "APRSdroid.MsgService"

    private val numOfRetries = s.prefs.getStringInt("p.messaging", 7)
    private val retryInterval = s.prefs.getStringInt("p.retry", 30)

    // Map<(sourceCall, messageNumber), lastAckTimestampMs>
    private val lastAckTimestamps = mutableMapOf<Pair<String, String>, Long>()

    private val pendingSender = Runnable { sendPendingMessages() }

    // ---- incoming ----

    /**
     * Store an incoming message, notify the user, and broadcast it.
     * Returns true if the message was new (not a duplicate).
     */
    private suspend fun storeNotifyMessage(ts: Long, srccall: String, msg: MessagePacket): Boolean {
        val isDupe = s.db.messageDao().isDuplicate(
            srccall, msg.messageNumber, msg.messageBody,
            if (s.prefs.isMsgDupeEnabled()) {
                val dupeTime = s.prefs.getStringInt("p.msgdupetime", 30)
                if (dupeTime == 0) return@storeNotifyMessage false
                ts - dupeTime * 1000L
            } else null,
        ) != null

        if (isDupe) {
            Log.i(TAG, "received duplicate message from $srccall: $msg")
            return false
        }

        s.db.messageDao().insert(
            MessageEntity(
                ts = ts,
                retrycnt = 0,
                call = srccall,
                msgid = msg.messageNumber,
                type = MessageEntity.TYPE_INCOMING,
                text = msg.messageBody,
            )
        )
        ServiceNotifier.instance.notifyMessage(s, s.prefs, srccall, msg.messageBody)
        s.sendBroadcast(
            Intent(AprsService.MESSAGE)
                .putExtra(AprsService.SOURCE, srccall)
                .putExtra(AprsService.DEST, msg.targetCallsign)
                .putExtra(AprsService.BODY, msg.messageBody)
        )
        return true
    }

    /**
     * Handle an incoming [MessagePacket] parsed from [ap].
     * [LastUsedDigi] is the digipeater path string from the packet.
     */
    fun handleMessage(ts: Long, ap: APRSPacket, msg: MessagePacket, lastUsedDigi: String) {
        val callssid = s.prefs.getCallSsid()

        // ACK duplicate suppression window (seconds -> ms)
        val lastAckDupeTime = s.prefs.getStringInt("p.ackdupe", 0) * 1000L
        val currentTime = System.currentTimeMillis()
        val messageNumber = msg.messageNumber
        val lastAckTime = lastAckTimestamps[Pair(ap.sourceCall, messageNumber)]

        if (msg.targetCallsign.equals(callssid, ignoreCase = true)) {
            if (msg.isAck || msg.isRej) {
                val newType = if (msg.isAck) {
                    MessageEntity.TYPE_OUT_ACKED
                } else {
                    MessageEntity.TYPE_OUT_REJECTED
                }
                runBlocking {
                    s.db.messageDao().updateMessageAcked(ap.sourceCall, messageNumber, newType)
                }
                s.sendBroadcast(msgPrivIntent())
            } else {
                runBlocking { storeNotifyMessage(ts, ap.sourceCall, msg) }

                // Only suppress duplicate ACKs if the feature is enabled
                if (s.prefs.isAckDupeEnabled()) {
                    if ((lastAckTime != null && (currentTime - lastAckTime) < lastAckDupeTime) ||
                        lastAckDupeTime == 0L
                    ) {
                        Log.d(
                            TAG,
                            "Duplicate msg or ack disabled, skipping ack for ${ap.sourceCall} messageNumber: $messageNumber",
                        )
                        return
                    }
                }

                // Send ACK if messageNumber is non-empty and we did not
                // digipeat our own call (avoid ACKing our own digipeated copy).
                if (messageNumber.isNotEmpty() &&
                    !lastUsedDigi.split(",").contains("${s.prefs.getCallSsid()}*") &&
                    !ap.digiString.split(",").contains("${s.prefs.getCallSsid()}*")
                ) {
                    Log.d(
                        TAG,
                        "Sending ACK: msgNumber=$messageNumber, digiString=${ap.digiString}, callsign=${s.prefs.getCallSsid()}",
                    )
                    val ack = s.newPacket(MessagePacket(ap.sourceCall, "ack", messageNumber))
                    s.sendPacket(ack)
                    lastAckTimestamps[Pair(ap.sourceCall, messageNumber)] = System.currentTimeMillis()
                }
            }
        } else if (
            msg.targetCallsign.split("-").firstOrNull()
                ?.equals(s.prefs.getCallsign(), ignoreCase = true) == true &&
            !msg.isAck && !msg.isRej
        ) {
            // Incoming message for a different SSID of our callsign
            if (ap.sourceCall.equals(callssid, ignoreCase = true)) return // ignore self, fix #283
            Log.d(TAG, "incoming message for ${msg.targetCallsign}")
            runBlocking { storeNotifyMessage(ts, ap.sourceCall, msg) }
        }
    }

    // ---- outgoing retry loop ----

    /**
     * Exponential backoff: `retryInterval * 2^(retry-1)` seconds,
     * capped at `2^numOfRetries` so the max delay is ~32 min for defaults.
     */
    private fun getRetryDelayMs(retrycnt: Int): Long =
        retryInterval * 1000L * (1L shl minOf(retrycnt - 1, numOfRetries))

    private fun scheduleNextSend(delay: Long) {
        // Round up to whole seconds to avoid tight loops
        val rounded = (delay + 999) / 1000 * 1000
        Log.d(TAG, "scheduling TX in ${rounded / 1000}s")
        s.handler.postDelayed(pendingSender, rounded)
    }

    /** Called when the service is terminated — clean up timers. */
    fun stop() {
        s.handler.removeCallbacks(pendingSender)
    }

    /**
     * Walk all pending outgoing messages, transmit those whose retry
     * window has elapsed, abort those that exceeded the retry limit,
     * and reschedule the next run.
     */
    fun sendPendingMessages() {
        s.handler.removeCallbacks(pendingSender)

        CoroutineScope(Dispatchers.IO).launch {
            var nextRun = Long.MAX_VALUE
            val pending = s.db.messageDao().getPendingMessagesList(numOfRetries)

            for (m in pending) {
                val tSend = m.ts + getRetryDelayMs(m.retrycnt) - System.currentTimeMillis()
                Log.d(
                    TAG,
                    "pending message: ${m.retrycnt}/$numOfRetries (${tSend / 1000}s) ->${m.call} '${m.text}'",
                )
                when {
                    m.retrycnt == numOfRetries && tSend <= 0 -> {
                        // Timed out — mark aborted
                        s.db.messageDao().updateMessageType(m.id, MessageEntity.TYPE_OUT_ABORTED)
                        s.sendBroadcast(msgPrivIntent())
                    }
                    m.retrycnt < numOfRetries && tSend <= 0 -> {
                        // Transmit now
                        val msg = s.newPacket(MessagePacket(m.call, m.text ?: "", m.msgid))
                        s.sendPacket(msg)
                        s.db.messageDao().updateMessageRetry(m.id, m.retrycnt + 1, System.currentTimeMillis())
                        s.sendBroadcast(msgPrivIntent())
                        nextRun = minOf(nextRun, getRetryDelayMs(m.retrycnt + 1))
                    }
                    m.retrycnt < numOfRetries -> {
                        // Schedule future transmission
                        nextRun = minOf(nextRun, tSend)
                    }
                }
            }

            if (nextRun != Long.MAX_VALUE) {
                scheduleNextSend(nextRun)
            }
        }
    }

    private fun msgPrivIntent() =
        Intent(AprsService.MESSAGE).setPackage(AprsService.PACKAGE)
}
