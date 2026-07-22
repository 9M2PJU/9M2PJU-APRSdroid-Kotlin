package org.aprsdroid.app

import net.ab0oo.aprs.parser.APRSPacket

/**
 * Kotlin port of the Scala `AprsBackend` abstract class.
 *
 * Common base for all APRS backends (TCP, UDP, HTTP, AFSK, Bluetooth,
 * USB, DigiRig). Holds the login string and defines the lifecycle
 * methods (`start`, `update`, `stop`).
 */
abstract class AprsBackendAbstract(protected val prefs: PrefsWrapper) : AprsBackendInterface {
    override val login: String = prefs.getLoginString()
}
