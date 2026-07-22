package org.aprsdroid.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Kotlin port of the Scala `SystemEventReceiver`.
 *
 * Starts the APRS service on boot (or other system events) if the user
 * previously left it running.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, i: Intent) {
        Log.d(TAG, "onReceive: $i")
        val prefs = PrefsWrapper(ctx)
        if (prefs.getBoolean("service_running", false)) {
            val svc = AprsService.intent(ctx, AprsService.SERVICE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(svc)
            } else {
                ctx.startService(svc)
            }
        }
    }

    companion object {
        private const val TAG = "APRSdroid.SystemEventReceiver"
    }
}
