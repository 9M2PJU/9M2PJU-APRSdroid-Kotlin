package org.aprsdroid.app

import android.Manifest

/**
 * Kotlin port of the Scala `LocationSource` abstract class.
 *
 * Subclasses provide position updates to [AprsService.postLocation]:
 *   - [SmartBeaconing] — adaptive beaconing based on speed / turning
 *   - [PeriodicGPS] — fixed interval / distance GPS updates
 *   - [FixedPosition] — manual coordinates, no GPS
 */
abstract class LocationSource {
    /** The start function might be called multiple times! */
    abstract fun start(singleShot: Boolean): String

    abstract fun stop()
}

/**
 * Kotlin port of the Scala `LocationSource` companion object.
 *
 * Named `LocationSources` (plural) because Kotlin does not allow a
 * class and an object to share the same name.
 */
object LocationSources {

    const val DEFAULT_CONNTYPE = "smartbeaconing"

    @JvmStatic
    fun instanciateLocation(service: AprsService, prefs: PrefsWrapper): LocationSource =
        when (prefs.getString("loc_source", DEFAULT_CONNTYPE)) {
            "smartbeaconing" -> SmartBeaconing(service, prefs)
            "periodic" -> PeriodicGPS(service, prefs)
            "manual" -> FixedPosition(service, prefs)
            else -> SmartBeaconing(service, prefs)
        }

    @JvmStatic
    fun getPermissions(prefs: PrefsWrapper): Set<String> =
        when (prefs.getString("loc_source", DEFAULT_CONNTYPE)) {
            "smartbeaconing", "periodic" -> setOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            else -> emptySet()
        }
}
