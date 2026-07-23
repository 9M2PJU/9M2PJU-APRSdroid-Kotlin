package org.aprsdroid.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Global crash guard for APRSdroid.
 *
 * Installs a [Thread.UncaughtExceptionHandler] that catches unhandled
 * exceptions, logs them, shows a user-friendly toast/dialog, and
 * prevents the app from silently crashing. The original exception is
 * still passed to the default handler after notification so the normal
 * crash dialog appears (but the user at least sees what happened).
 *
 * Register in [Application.onCreate] via [CrashGuard.install].
 */
object CrashGuard {

    private const val TAG = "APRSdroid.CrashGuard"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on ${thread.name}", throwable)

            // Build a readable error message
            val sw = StringWriter()
            sw.write("APRSdroid crashed:\n\n")
            sw.write(throwable.javaClass.simpleName)
            sw.write(": ")
            sw.write(throwable.message ?: "(no message)")
            sw.write("\n\n")
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()

            // Log to the APRSdroid database if possible
            try {
                logToDatabase(app, stackTrace)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log crash to database", e)
            }

            // Show a toast on the main thread
            try {
                val msg = "APRSdroid error: ${throwable.message ?: throwable.javaClass.simpleName}"
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                // ignore
            }

            // Give the toast time to show
            try {
                Thread.sleep(500)
            } catch (_: InterruptedException) {}

            // Pass to the original handler (shows the system crash dialog)
            previous?.uncaughtException(thread, throwable)
        }
        Log.d(TAG, "Crash guard installed")
    }

    private fun logToDatabase(app: Application, stackTrace: String) {
        // Use a background thread to avoid blocking the crash handler
        Thread {
            try {
                val db = org.aprsdroid.app.data.AprsDatabase.get(app)
                kotlinx.coroutines.runBlocking {
                    db.postDao().addPost(
                        System.currentTimeMillis(),
                        org.aprsdroid.app.data.PostEntity.TYPE_ERROR,
                        "Crash",
                        stackTrace.take(2000), // limit length
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not write crash to DB", e)
            }
        }.start()
    }
}
