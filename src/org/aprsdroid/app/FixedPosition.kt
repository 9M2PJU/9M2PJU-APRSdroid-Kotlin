package org.aprsdroid.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.util.Log

/**
 * Kotlin port of the Scala `FixedPosition`.
 *
 * Manual location source — transmits a fixed position from preferences,
 * optionally on a periodic timer. No GPS is used.
 */
class FixedPosition(private val service: AprsService, private val prefs: PrefsWrapper) :
    LocationSource() {

    private val TAG = "APRSdroid.FixedPosition"
    private val ALARM_ACTION = "org.aprsdroid.app.FixedPosition.ALARM"

    private val intent = Intent(ALARM_ACTION)
    private val pendingIntent = PendingIntent.getBroadcast(
        service, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            Log.d(TAG, "onReceive: $i")
            postPosition()
            postRefresh()
        }
    }

    private val manager = service.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private var alreadyRunning = false

    override fun start(singleShot: Boolean): String {
        stop()

        UIHelper.safeRegisterReceiver(service, receiver, IntentFilter(ALARM_ACTION))
        val periodic = prefs.getBoolean("periodicposition", true)
        Log.d(TAG, "start: periodic=$periodic single=$singleShot")

        if (singleShot || alreadyRunning || periodic) {
            postPosition()
        }

        alreadyRunning = true

        if (periodic && !singleShot) {
            postRefresh()
        }

        return service.getString(R.string.p_source_manual)
    }

    override fun stop() {
        manager.cancel(pendingIntent)
        if (alreadyRunning) {
            service.unregisterReceiver(receiver)
        }
    }

    private fun postRefresh() {
        val updInt = prefs.getStringInt("interval", 10)
        Log.d(TAG, "postRefresh(): $updInt min")
        manager.set(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + updInt * 60 * 1000L,
            pendingIntent,
        )
    }

    private fun postPosition() {
        val location = Location("manual")
        location.latitude = prefs.getStringFloat("manual_lat", 0f).toDouble()
        location.longitude = prefs.getStringFloat("manual_lon", 0f).toDouble()
        service.postLocation(location)
    }
}
