package org.aprsdroid.app

import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket

/**
 * Kotlin stub for the Scala `BluetoothLETnc`.
 *
 * BLE TNC support will be implemented in a later batch; for now this
 * is a placeholder so the backend dispatcher compiles and the BLE
 * preference can be selected without crashing.
 */
class BluetoothLETnc(private val service: AprsService, prefs: PrefsWrapper) :
    AprsBackendAbstract(prefs) {

    private val TAG = "APRSdroid.BluetoothLETnc"

    override fun start(): Boolean {
        Log.w(TAG, "BLE TNC not yet implemented")
        service.postAbort(service.getString(R.string.bt_error_no_tnc))
        return false
    }

    override fun update(packet: APRSPacket): String = "BLE not implemented"

    override fun stop() {}
}
