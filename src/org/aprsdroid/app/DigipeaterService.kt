package org.aprsdroid.app

import android.util.Log
import net.ab0oo.aprs.parser.Parser
import java.util.Date

/**
 * Kotlin port of the Scala `DigipeaterService`.
 *
 * Handles digipeating of incoming RF packets: deduplicates recent
 * digipeats, regenerates packets (if enabled), and rewrites the
 * digipeater path according to the configured path and rules.
 *
 * @param prefs preference wrapper
 * @param tag log tag (passed in so logs match the caller's context)
 * @param sendDigipeatedPacket callback that transmits the rewritten
 *        packet string (typically `AprsService.sendDigipeatedPacket`)
 */
class DigipeaterService(
    private val prefs: PrefsWrapper,
    private val tag: String,
    private val sendDigipeatedPacket: (String) -> Unit,
) {
    private val recentDigipeats = mutableMapOf<String, Date>()

    private val dedupeTime: Int
        get() = prefs.getStringInt("p.dedupe", 30)

    private val digipeaterPath: String
        get() = prefs.getString("digipeater_path", "WIDE1,WIDE2")

    /** Record a digipeat so it can be deduplicated within [dedupeTime] seconds. */
    private fun storeDigipeat(sourceCall: String, destinationCall: String, payload: String) {
        val key = "$sourceCall>$destinationCall:$payload"
        recentDigipeats[key] = Date()
    }

    /** True if this exact packet was digipeated within [dedupeTime] seconds. */
    private fun isDigipeatRecent(sourceCall: String, destinationCall: String, payload: String): Boolean {
        val key = "$sourceCall>$destinationCall:$payload"
        val timestamp = recentDigipeats[key] ?: return false
        val now = Date()
        return now.time - timestamp.time < dedupeTime * 1000L
    }

    /** Remove digipeat entries older than [dedupeTime] seconds. */
    private fun cleanupOldDigipeats() {
        val now = Date()
        recentDigipeats.entries.removeIf { (_, timestamp) ->
            now.time - timestamp.time >= dedupeTime * 1000L
        }
    }

    /**
     * Process an incoming RF packet string. If digipeating or
     * regeneration is enabled (and the backend is KISS/AFSK), rewrite
     * the digipeater path and transmit via [sendDigipeatedPacket].
     */
    fun processIncomingPost(post: String) {
        Log.d(tag, "POST STRING TEST: $post")

        // Only digipeat over KISS or AFSK backends
        val backendName = prefs.getBackendName()
        if (!backendName.contains("KISS") && !backendName.contains("AFSK")) {
            Log.d("PrefsAct", "Backend does not contain KISS or AFSK")
            return
        }
        Log.d("PrefsAct", "Backend contains KISS or AFSK")

        // Parse the incoming packet
        val packet = try {
            Parser.parse(post)
        } catch (e: Exception) {
            Log.e("Parsing FAILED!", "Failed to parse packet: $post", e)
            return
        }

        // Regeneration mode: re-encode and send without digipeating
        if (!prefs.isDigipeaterEnabled() && prefs.isRegenerateEnabled()) {
            Log.d("APRSdroid.Service", "Regen enabled")
            sendDigipeatedPacket(packet.toString())
            return
        }

        if (!prefs.isDigipeaterEnabled()) {
            Log.d("APRSdroid.Service", "Digipeating is disabled; skipping processing.")
            return
        }

        cleanupOldDigipeats()

        try {
            val callssid = prefs.getCallSsid()
            val sourceCall = packet.sourceCall
            val destinationCall = packet.destinationCall
            val lastUsedDigi = packet.digiString
            val payload = packet.aprsInformation
            val payloadString = packet.aprsInformation?.toString() ?: ""

            // Don't digipeat our own packets
            if (callssid == sourceCall) {
                Log.d("APRSdroid.Service", "No digipeat: callssid ($callssid) matches source call ($sourceCall).")
                return
            }

            // Skip recently digipeated packets
            if (isDigipeatRecent(sourceCall, destinationCall, payloadString)) {
                Log.d(
                    "APRSdroid.Service",
                    "Packet from $sourceCall to $destinationCall and $payload has been heard recently, skipping digipeating.",
                )
                return
            }

            val (modifiedDigiPath, digipeatOccurred) = processDigiPath(lastUsedDigi, callssid)

            Log.d("APRSdroid.Service", "Source: $sourceCall")
            Log.d("APRSdroid.Service", "Destination: $destinationCall")
            Log.d("APRSdroid.Service", "Digi: $lastUsedDigi")
            Log.d("APRSdroid.Service", "Modified Digi Path: $modifiedDigiPath")
            Log.d("APRSdroid.Service", "Payload: $payload")

            val digipeatedPacket = "$sourceCall>$destinationCall,$modifiedDigiPath:$payload"

            if (digipeatOccurred) {
                sendDigipeatedPacket(digipeatedPacket)
                storeDigipeat(sourceCall, destinationCall, payloadString)
            } else {
                Log.d("APRSdroid.Service", "No digipeat occurred, not sending a test packet.")
            }
        } catch (e: Exception) {
            Log.e("APRSdroid.Service", "Failed to parse packet: $post", e)
        }
    }

    /**
     * Rewrite the digipeater path: find the first unused hop matching
     * the configured [digipeaterPath], decrement its N suffix (or
     * mark it used with `callssid*`).
     *
     * Returns `(newPath, digipeatOccurred)`. If no modification is
     * needed, returns the original path and `false`.
     */
    private fun processDigiPath(lastUsedDigi: String, callssid: String): Pair<String, Boolean> {
        Log.d("APRSdroid.Service", "Original Digi Path: '$lastUsedDigi'")

        if (lastUsedDigi.trim().isEmpty()) {
            Log.d("APRSdroid.Service", "LastUsedDigi is empty, returning unchanged.")
            return lastUsedDigi to false
        }

        val trimmedPath = lastUsedDigi.removePrefix(",")
        val pathComponents = trimmedPath.split(",").filter { it.isNotEmpty() }
        val digipeaterPaths = digipeaterPath.split(",").filter { it.isNotEmpty() }

        var modified = false
        val modifiedPath = mutableListOf<String>()

        for (component in pathComponents) {
            // Direct-only mode: bail if any used hop is present
            if (prefs.getBoolean("p.directonly", false) && component.contains("*")) {
                return lastUsedDigi to false
            }

            // Skip if our call already appears as a used digi
            if (component == "$callssid*") {
                return lastUsedDigi to false
            }

            val isFirstUnused = modifiedPath.isEmpty() || modifiedPath.last().endsWith("*")
            val matchesDigiPath = !modified &&
                (digipeaterPaths.any { component.split("-")[0] == it } ||
                    digipeaterPaths.contains(component) || component == callssid)

            if (isFirstUnused && matchesDigiPath) {
                val w = component
                if (w.matches(Regex(".*-(\\d+)$"))) {
                    val number = w.split("-").last().toInt()
                    val newNumber = number - 1
                    if (newNumber == 0 || w == callssid) {
                        modifiedPath.add("$callssid*")
                        modified = true
                    } else {
                        modifiedPath.add("$callssid*")
                        modifiedPath.add(w.removeSuffix("-$number") + "-$newNumber")
                        modified = true
                    }
                } else {
                    // No -N suffix — leave unchanged
                    modifiedPath.add(component)
                }
            } else {
                modifiedPath.add(component)
            }
        }

        val resultPath = modifiedPath.joinToString(",")
        Log.d("APRSdroid.Service", "Modified Digi Path: '$resultPath'")

        if (resultPath == trimmedPath) {
            Log.d("APRSdroid.Service", "No modifications were made; returning the original path.")
            return lastUsedDigi to false
        }

        return resultPath to true
    }
}
