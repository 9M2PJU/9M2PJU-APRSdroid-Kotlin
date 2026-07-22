package org.aprsdroid.app

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast

/**
 * Kotlin port of the Scala `PeriodicGPS`.
 *
 * Periodic GPS location source — requests location updates at a fixed
 * interval (in minutes) and minimum distance (in meters). When
 * `gps_activation` is "med", switches to a "fast lane" for a short
 * window after each fix to capture moving positions more accurately.
 */
class PeriodicGPS(private val service: AprsService, private val prefs: PrefsWrapper) :
    LocationSource(), LocationListener {

    private val TAG = "APRSdroid.PeriodicGPS"

    private val FAST_LANE_ACT = 30_000L

    private val locMan: LocationManager =
        service.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var lastLoc: Location? = null
    private var fastLaneLoc: Location? = null

    override fun start(singleShot: Boolean): String {
        fastLaneLoc = null
        lastLoc = null
        stop()
        requestLocations(singleShot)
        return service.getString(R.string.p_source_periodic)
    }

    private fun requestLocations(stayOn: Boolean) {
        val updInt = prefs.getStringInt("interval", 10)
        val updDist = prefs.getStringInt("distance", 10)
        val gpsAct = prefs.getString("gps_activation", "med")
        try {
            val provider = PeriodicGPS.bestProvider(locMan) ?: LocationManager.GPS_PROVIDER
            if (stayOn || gpsAct == "always") {
                locMan.requestLocationUpdates(provider, 0L, 0f, this)
            } else {
                // for GPS precision == medium, we use getGpsInterval()
                locMan.requestLocationUpdates(
                    provider,
                    updInt * 60_000L - getGpsInterval(),
                    updDist * 1000f,
                    this
                )
            }
            if (prefs.getBoolean("netloc", false)) {
                locMan.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    updInt * 60_000L,
                    updDist * 1000f,
                    this
                )
            }
        } catch (e: IllegalArgumentException) {
            service.postAbort(service.getString(R.string.service_no_location) + "\n" + e.message)
        } catch (e: SecurityException) {
            service.postAbort(service.getString(R.string.service_no_location) + "\n" + e.message)
        }
    }

    override fun stop() {
        locMan.removeUpdates(this)
    }

    private fun getGpsInterval(): Long {
        val gpsAct = prefs.getString("gps_activation", "med")
        return if (gpsAct == "med") FAST_LANE_ACT else 0L
    }

    private fun startFastLane() {
        Log.d(TAG, "switching to fast lane")
        locMan.removeUpdates(this)
        requestLocations(true)
        service.handler.postDelayed({ stopFastLane(true) }, FAST_LANE_ACT)
    }

    private fun stopFastLane(post: Boolean) {
        if (!AprsService.running) return
        Log.d(TAG, "switching to slow lane")
        if (post && fastLaneLoc != null) {
            Log.d(TAG, "stopFastLane: posting $fastLaneLoc")
            postLocation(fastLaneLoc!!)
        }
        fastLaneLoc = null
        locMan.removeUpdates(this)
        requestLocations(false)
    }

    private fun goingFastLane(location: Location): Boolean {
        if (fastLaneLoc == null) {
            fastLaneLoc = location
            startFastLane()
        } else {
            fastLaneLoc = location
        }
        return true
    }

    // ---- LocationListener ----

    override fun onLocationChanged(location: Location) {
        val updInt = prefs.getStringInt("interval", 10) * 60_000L
        val updDist = prefs.getStringInt("distance", 10) * 1000f
        val last = lastLoc
        if (last != null &&
            (location.time - last.time < (updInt - getGpsInterval()) ||
                location.distanceTo(last) < updDist)
        ) {
            return
        }
        val gpsAct = prefs.getString("gps_activation", "med")
        if (gpsAct == "med" && location.provider == LocationManager.GPS_PROVIDER) {
            if (goingFastLane(location)) return
        }
        postLocation(location)
    }

    override fun onProviderDisabled(provider: String) {
        Log.d(TAG, "onProviderDisabled: $provider")
        val netlocAvailable = locMan.getProviders(true).contains(LocationManager.NETWORK_PROVIDER)
        val netlocUsable = netlocAvailable && prefs.getBoolean("netloc", false)
        if (provider == LocationManager.GPS_PROVIDER && !netlocUsable) {
            Toast.makeText(service, R.string.service_no_location, Toast.LENGTH_LONG).show()
        }
    }

    override fun onProviderEnabled(provider: String) {
        Log.d(TAG, "onProviderEnabled: $provider")
    }

    @Deprecated("deprecated in API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        Log.d(TAG, "onStatusChanged: $provider")
    }

    private fun postLocation(location: Location) {
        lastLoc = location
        service.postLocation(location)
    }

    companion object {
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
}
