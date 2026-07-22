package org.aprsdroid.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler

/**
 * Kotlin port of the Scala `LocationReceiver`.
 *
 * A BroadcastReceiver that debounces location-update broadcasts: when
 * an update arrives, it removes any pending callback and posts a new
 * one after 100ms, so rapid bursts of updates only trigger one reload.
 */
class LocationReceiver(
    private val handler: Handler,
    private val callback: () -> Unit,
) : BroadcastReceiver() {

    private val runnable = Runnable { callback() }

    override fun onReceive(ctx: Context?, i: Intent?) {
        handler.removeCallbacks(runnable)
        handler.postDelayed(runnable, 100)
    }
}
