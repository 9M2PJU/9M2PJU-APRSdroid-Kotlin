package org.aprsdroid.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.jazzido.PacketDroid.PacketCallback
import com.nogy.afu.soundmodem.APRSFrame
import com.nogy.afu.soundmodem.Message
import com.nogy.afu.soundmodem.Afsk
import net.ab0oo.aprs.parser.APRSPacket
import net.ab0oo.aprs.parser.Digipeater
import net.ab0oo.aprs.parser.Parser
import sivantoledo.ax25.PacketHandler

/**
 * Kotlin port of the Scala `AfskUploader`.
 *
 * AFSK (Audio Frequency-Shift Keying) backend: modulates outgoing
 * packets as 1200 baud AFSK audio and demodulates incoming audio.
 * Supports both the high-quality software demodulator (jsoundmodem)
 * and the native JNI demodulator (multimon via AudioBufferProcessor).
 *
 * Implements [PacketHandler] for incoming packets from the
 * Afsk1200Demodulator and [PacketCallback] for incoming packets from
 * the native AudioBufferProcessor.
 */
class AfskUploader(
    private val service: AprsService,
    prefs: PrefsWrapper,
) : AprsBackendAbstract(prefs), PacketHandler, PacketCallback {

    // frame prefix: bytes = milliseconds * baudrate / 8 / 1000
    private val frameLength = prefs.getStringInt("afsk.prefix", 200) * 1200 / 8 / 1000
    private val digis = prefs.getString("digi_path", "WIDE1-1")
    private val useHq = prefs.getAfskHQ()
    private val useBt = prefs.getAfskBluetooth()
    private val samplerate = if (useBt) 16000 else 22050
    private val outType = prefs.getAfskOutput()
    private val inType = 1 // MIC or VOICE_CALL, both = 1

    private val output = Afsk(outType, samplerate)
    private val aw = AfskInWrapper(useHq, this, inType, samplerate / 2) // 8000 / 11025

    private val btScoReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, i: Intent?) {
            val state = i?.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1) ?: -1
            Log.d(TAG, "AudioManager SCO event: $state")
            if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                // we are connected, perform actual start
                log(service.getString(R.string.afsk_info_sco_est))
                aw.start()
                service.unregisterReceiver(this)
                service.postPosterStarted()
            }
        }
    }

    init {
        output.setVolume(AudioTrack.getMaxVolume())
    }

    private fun isCallsignAX25Valid(): Boolean {
        if (prefs.getCallsign().length > 6) {
            service.postAbort(service.getString(R.string.e_toolong_callsign))
            return false
        }
        return true
    }

    override fun start(): Boolean {
        if (!isCallsignAX25Valid()) return false
        if (useBt) {
            log(service.getString(R.string.afsk_info_sco_req))
            (service.getSystemService(Context.AUDIO_SERVICE) as AudioManager).startBluetoothSco()
            UIHelper.safeRegisterReceiver(
                service, btScoReceiver,
                IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED),
            )
            return false
        } else {
            aw.start()
            return true
        }
    }

    private fun sendMessage(msg: Message): Boolean {
        return output.sendMessage(msg)
    }

    override fun update(packet: APRSPacket): String {
        // Need to "parse" the packet in order to replace the Digipeaters
        packet.setDigipeaters(Digipeater.parseList(digis, true))
        val from = packet.getSourceCall()
        val to = packet.getDestinationCall()
        val data = packet.getAprsInformation().toString()
        val msg = APRSFrame(from, to, digis, data, frameLength).getMessage()
        Log.d(TAG, "update(): From: $from To: $to Via: $digis telling $data")
        return if (sendMessage(msg)) "AFSK OK" else "AFSK busy"
    }

    override fun stop() {
        aw.close()
        if (useBt) {
            (service.getSystemService(Context.AUDIO_SERVICE) as AudioManager).stopBluetoothSco()
            try {
                service.unregisterReceiver(btScoReceiver)
            } catch (_: RuntimeException) {
                // ignore, receiver already unregistered
            }
        }
    }

    // PacketCallback interface
    override fun received(data: ByteArray) = handlePacket(data)

    // PacketHandler interface
    override fun handlePacket(data: ByteArray) {
        try {
            service.postSubmit(Parser.parseAX25(data).toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "bad packet: ${data.joinToString(" ") { "%02x".format(it) }}")
            e.printStackTrace()
        }
    }

    override fun peak(peakValue: Short) {
        notifyMicLevel(peakValue / 330)
    }

    fun notifyMicLevel(level: Int) {
        val i = Intent(AprsService.MICLEVEL)
        i.putExtra("level", level)
        service.sendBroadcast(i)
    }

    private fun log(s: String) {
        Log.i(TAG, s)
        service.postAddPost(PostEntity.TYPE_INFO, R.string.post_info, s)
    }

    fun postAbort(s: String) {
        service.postAbort(s)
    }

    companion object {
        private const val TAG = "APRSdroid.Afsk"
    }
}
