package org.aprsdroid.app

import android.content.Context
import android.location.Criteria
import android.location.LocationManager

/**
 * Stub for the `PeriodicGPS` companion object.
 *
 * The full GPS location source will be ported in Batch 5; this provides
 * the `bestProvider` helpers needed by `PrefsWrapper.getFilterString`.
 */
object PeriodicGPS {

    @JvmStatic
    fun bestProvider(locman: LocationManager): String? {
        val cr = Criteria()
        cr.accuracy = Criteria.ACCURACY_FINE
        return locman.getBestProvider(cr, false)
    }

    @JvmStatic
    fun bestProvider(context: Context): String? {
        val locman = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return bestProvider(locman)
    }
}
