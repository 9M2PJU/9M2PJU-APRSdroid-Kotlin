package org.aprsdroid.app

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max

/**
 * Kotlin port of the Scala `SmartBeaconing`.
 *
 * Adaptive beaconing algorithm: transmits position reports at a rate
 * that depends on the current speed (faster when moving fast, slower
 * when stationary), with corner-pegging when the bearing changes
 * significantly.
 */
class SmartBeaconing(private val service: AprsService, private val prefs: PrefsWrapper) :
    LocationSource(), LocationListener {

    private val TAG = "APRSdroid.SmartBeaconing"

    private val locMan: LocationManager =
        service.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var lastLoc: Location? = null
    private var started = false

    override fun start(singleShot: Boolean): String {
        lastLoc = null
        if (!started) {
            try {
                locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, this)
                started = true
            } catch (e: IllegalArgumentException) {
                service.postAbort(service.getString(R.string.service_sm_no_gps) + "\n" + e.message)
            } catch (e: SecurityException) {
                service.postAbort(service.getString(R.string.service_sm_no_gps) + "\n" + e.message)
            }
        }
        return service.getString(R.string.p_source_smart)
    }

    override fun stop() {
        if (started) {
            locMan.removeUpdates(this)
        }
        started = false
    }

    private fun smartBeaconSpeedRate(speed: Float): Int {
        val sbFastSpeed = prefs.getStringInt("sb.fastspeed", 100) / 3.6 // [m/s]
        val sbFastRate = prefs.getStringInt("sb.fastrate", 60)
        val sbSlowSpeed = prefs.getStringInt("sb.slowspeed", 5) / 3.6 // [m/s]
        val sbSlowRate = prefs.getStringInt("sb.slowrate", 1200)
        return when {
            speed <= sbSlowSpeed -> sbSlowRate
            speed >= sbFastSpeed -> sbFastRate
            else -> (sbFastRate + (sbSlowRate - sbFastRate) * (sbFastSpeed - speed) /
                (sbFastSpeed - sbSlowSpeed)).toInt()
        }
    }

    // returns the angle between two bearings
    private fun getBearingAngle(alpha: Float, beta: Float): Float {
        val delta = abs(alpha - beta) % 360
        return if (delta <= 180) delta else (360 - delta)
    }

    // obtain max speed in [m/s] from moved distance, last and current location
    private fun getSpeed(location: Location): Float {
        val last = lastLoc ?: return location.speed
        val dist = location.distanceTo(last)
        val tDiff = location.time - last.time
        return max(max(dist * 1000f / tDiff, location.speed), last.speed)
    }

    private fun smartBeaconCornerPeg(location: Location): Boolean {
        val sbTurnTime = prefs.getStringInt("sb.turntime", 15)
        val sbTurnMin = prefs.getStringInt("sb.turnmin", 10)
        val sbTurnSlope = prefs.getStringInt("sb.turnslope", 240).toDouble()

        val last = lastLoc ?: return false
        val speed = location.speed
        val tDiff = location.time - last.time
        val turn = getBearingAngle(location.bearing, last.bearing)

        // no bearing / stillstand -> no corner pegging
        if (!location.hasBearing() || speed == 0f) {
            return false
        }

        // if last bearing unknown, deploy turn_time
        if (!last.hasBearing()) {
            return (tDiff / 1000 >= sbTurnTime)
        }

        // threshold depends on slope/speed [mph]
        val threshold = sbTurnMin + sbTurnSlope / (speed * 2.23693629f)

        Log.d(
            TAG,
            "smartBeaconCornerPeg: %.0f < %.0f %d/%d".format(turn, threshold, tDiff / 1000, sbTurnTime),
        )
        // need to corner peg if turn time reached and turn > threshold
        return (tDiff / 1000 >= sbTurnTime && turn > threshold)
    }

    // return true if current position is "new enough" vs. lastLoc
    private fun smartBeaconCheck(location: Location): Boolean {
        if (lastLoc == null) return true
        if (smartBeaconCornerPeg(location)) return true
        val last = lastLoc!!
        val dist = location.distanceTo(last)
        val tDiff = location.time - last.time
        val speed = getSpeed(location)
        val speedRate = smartBeaconSpeedRate(speed)
        Log.d(
            TAG,
            "smartBeaconCheck: %.0fm, %.2fm/s -> %d/%ds - %s".format(
                dist, speed, tDiff / 1000, speedRate, (tDiff / 1000 >= speedRate).toString(),
            ),
        )
        return tDiff / 1000 >= speedRate
    }

    // ---- LocationListener ----

    override fun onLocationChanged(location: Location) {
        if (smartBeaconCheck(location)) {
            postLocation(location)
        }
    }

    override fun onProviderDisabled(provider: String) {
        Log.d(TAG, "onProviderDisabled: $provider")
        if (provider == LocationManager.GPS_PROVIDER) {
            Toast.makeText(service, R.string.service_sm_no_gps, Toast.LENGTH_LONG).show()
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
}
