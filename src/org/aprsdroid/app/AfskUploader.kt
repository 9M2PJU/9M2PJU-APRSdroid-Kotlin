package org.aprsdroid.app

import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket

/**
 * Kotlin stub for the Scala `AfskUploader`.
 *
 * AFSK modulation / demodulation via the native PacketDroid JNI module
 * will be wired in a later batch; for now this is a placeholder so the
 * backend dispatcher compiles.
 */
class AfskUploader(private val service: AprsService, prefs: PrefsWrapper) :
    AprsBackendAbstract(prefs) {

    private val TAG = "APRSdroid.AfskUploader"

    override fun start(): Boolean {
        Log.w(TAG, "AFSK not yet implemented")
        service.postAbort("AFSK not yet implemented")
        return false
    }

    override fun update(packet: APRSPacket): String = "AFSK not implemented"

    override fun stop() {}
}
