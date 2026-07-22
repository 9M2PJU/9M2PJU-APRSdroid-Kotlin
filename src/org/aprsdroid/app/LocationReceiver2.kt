package org.aprsdroid.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Kotlin port of the Scala `LocationReceiver2[Result]`.
 *
 * A BroadcastReceiver that runs a background task on each received
 * intent, then calls a finish callback on the UI thread. If multiple
 * updates arrive while the task is running, the task is re-run once
 * more after completion (debounce + coalescing).
 *
 * The Scala version used `MyAsyncTask` (an `AsyncTask` subclass);
 * this Kotlin port uses coroutines instead, since `AsyncTask` is
 * deprecated.
 */
class LocationReceiver2<Result>(
    private val bgTask: (Intent?) -> Result,
    private val finishTask: (Result) -> Unit,
    private val cancelTask: (Result) -> Unit,
) : BroadcastReceiver() {

    private var pending = 0
    private var lastIntent: Intent? = null
    private val scope: CoroutineScope = MainScope()
    private var currentJob: Job? = null

    fun startTask(i: Intent?) {
        pending += 1
        lastIntent = i
        if (pending == 1) {
            runTask(i)
        }
    }

    private fun runTask(i: Intent?) {
        currentJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                bgTask(i)
            }
            finishTask(result)
            if (pending > 1) {
                // something happened, we need to rerun
                Log.d("LocationReceiver2", "rerunning...")
                pending = 0
                lastIntent?.let { startTask(it) }
            } else {
                pending = 0
            }
        }
    }

    override fun onReceive(ctx: Context?, i: Intent?) {
        if (i != null) startTask(i)
    }

    /**
     * Cancel any pending task. Call from the activity's onDestroy.
     */
    fun cancel() {
        currentJob?.cancel()
        scope.cancel()
    }
}
