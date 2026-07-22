package org.aprsdroid.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.view.View
import android.view.WindowInsets
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import java.io.File

object UIHelper {

    /**
     * Get the directory for exporting files (profiles, logs, etc.).
     * Uses Documents directory on KitKat+ (API 19+), external storage root on older.
     */
    @JvmStatic
    fun getExportDirectory(ctx: Context): File {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        } else {
            Environment.getExternalStorageDirectory()
        }
        return File(base, "APRSdroid")
    }

    /**
     * Share a file via Android's share sheet (ACTION_SEND).
     */
    @JvmStatic
    fun shareFile(ctx: Context, file: File, filename: String) {
        ctx.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM,
                        FileProvider.getUriForFile(ctx, "org.aprsdroid.fileprovider", file))
                    .putExtra(Intent.EXTRA_SUBJECT, filename),
                file.toString(),
            ),
        )
    }

    /**
     * Register a BroadcastReceiver with the appropriate flags for Android 14+
     * (API 34+). On older Android, the flags are ignored.
     * Uses RECEIVER_NOT_EXPORTED since all our receivers are for internal broadcasts.
     */
    @JvmStatic
    fun safeRegisterReceiver(ctx: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 34) { // Android 14 (UPSIDE_DOWN_CAKE)
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ctx.registerReceiver(receiver, filter)
        }
    }

    /**
     * Opt out of edge-to-edge on Android 15+ (targetSdk 35).
     *
     * Applies status-bar and navigation-bar insets to the root content view
     * and the bottom navigation bar if present.
     */
    @JvmStatic
    fun applySystemBarInsets(act: Activity) {
        if (Build.VERSION.SDK_INT >= 30) {
            act.window.setDecorFitsSystemWindows(true)
        } else {
            WindowCompat.setDecorFitsSystemWindows(act.window, true)
        }

        val root = act.window.decorView.findViewById<View>(android.R.id.content)
        root?.setOnApplyWindowInsetsListener { v, insets ->
            val topPad: Int
            val bottomPad: Int
            val leftPad: Int
            val rightPad: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val status = insets.getInsets(WindowInsets.Type.statusBars())
                val nav = insets.getInsets(WindowInsets.Type.navigationBars())
                topPad = status.top
                leftPad = status.left
                rightPad = status.right
                bottomPad = nav.bottom
            } else {
                @Suppress("DEPRECATION")
                topPad = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                leftPad = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                rightPad = insets.systemWindowInsetRight
                @Suppress("DEPRECATION")
                bottomPad = insets.systemWindowInsetBottom
            }

            v.setPadding(leftPad, topPad, rightPad, 0)

            val bottomNav = act.findViewById<View>(R.id.bottom_nav)
            bottomNav?.setPadding(0, 0, 0, bottomPad)

            insets
        }
    }
}
