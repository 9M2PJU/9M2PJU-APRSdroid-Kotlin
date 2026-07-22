package org.aprsdroid.app

import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket

/**
 * Kotlin stub for the Scala `DigiRig`.
 *
 * DigiRig mobile audio interface support (RECORD_AUDIO + AFSK) will be
 * implemented in a later batch; for now this is a placeholder so the
 * backend dispatcher compiles.
 */
class DigiRig(private val service: AprsService, prefs: PrefsWrapper) :
    AprsBackendAbstract(prefs) {

    private val TAG = "APRSdroid.DigiRig"

    override fun start(): Boolean {
        Log.w(TAG, "DigiRig not yet implemented")
        service.postAbort("DigiRig not yet implemented")
        return false
    }

    override fun update(packet: APRSPacket): String = "DigiRig not implemented"

    override fun stop() {}
}
