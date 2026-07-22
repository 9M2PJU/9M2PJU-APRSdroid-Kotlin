package org.aprsdroid.app

import android.hardware.usb.UsbManager
import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket

/**
 * Kotlin stub for the Scala `UsbTnc`.
 *
 * USB-serial TNC support (via `usbserial` library) will be implemented
 * in a later batch; for now this is a placeholder so the backend
 * dispatcher compiles.
 */
class UsbTnc(private val service: AprsService, prefs: PrefsWrapper) :
    AprsBackendAbstract(prefs) {

    private val TAG = "APRSdroid.UsbTnc"

    override fun start(): Boolean {
        Log.w(TAG, "USB TNC not yet implemented")
        val usbMan = service.getSystemService(android.content.Context.USB_SERVICE) as UsbManager
        val devices = usbMan.deviceList
        if (devices.isEmpty()) {
            service.postAbort("No USB devices")
            return false
        }
        service.postAbort("USB TNC not yet implemented")
        return false
    }

    override fun update(packet: APRSPacket): String = "USB not implemented"

    override fun stop() {}

    companion object {
        /**
         * Stub for the USB device handle check. The full implementation
         * will be wired in a later batch; for now, always returns false
         * so the launcher does not try to auto-start the service.
         */
        @JvmStatic
        fun checkDeviceHandle(
            prefs: android.content.SharedPreferences,
            device: android.os.Parcelable?,
        ): Boolean = false
    }
}
