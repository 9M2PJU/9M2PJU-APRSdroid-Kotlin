package org.aprsdroid.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Vibrator

/**
 * Kotlin port of the Scala `ServiceNotifier`.
 *
 * Builds and posts the foreground-service notification and per-call
 * message notifications.
 */
class ServiceNotifier {

    val SERVICE_NOTIFICATION: Int = 1
    var CALL_NOTIFICATION: Int = SERVICE_NOTIFICATION + 1
    private val callIdMap = mutableMapOf<String, Int>()

    fun setupChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    "status",
                    ctx.getString(R.string.aprsservice),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    "msg",
                    ctx.getString(R.string.p_msg),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
    }

    fun newNotificationBuilder(ctx: Service, channel: String): Notification.Builder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(ctx, channel)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(ctx)
        }
    }

    fun newNotification(ctx: Service, status: String): Notification {
        val i = Intent(ctx, APRSdroid::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val appname = ctx.resources.getString(R.string.app_name)
        val nb = newNotificationBuilder(ctx, "status")
            .setContentTitle(appname)
            .setContentText(status)
            .setContentIntent(
                PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE)
            )
            .setSmallIcon(R.drawable.ic_status)
            .setWhen(System.currentTimeMillis())
            .setOngoing(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            nb.setShowWhen(true)
        }
        return nb.build()
    }

    fun getCallNumber(call: String): Int {
        return callIdMap.getOrPut(call) { CALL_NOTIFICATION++ }
    }

    fun newMessageNotification(ctx: Service, call: String, message: String): Notification {
        // MessageActivity is not yet ported — launch the conversations list as a fallback.
        val targetClass = try {
            Class.forName("org.aprsdroid.app.MessageActivity")
        } catch (_: ClassNotFoundException) {
            try {
                Class.forName("org.aprsdroid.app.ConversationsActivity")
            } catch (_: ClassNotFoundException) {
                APRSdroid::class.java
            }
        }
        val i = Intent(ctx, targetClass)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.data = Uri.parse(call)
        return newNotificationBuilder(ctx, "msg")
            .setContentTitle(call)
            .setContentText(message)
            .setContentIntent(
                PendingIntent.getActivity(
                    ctx, 0, i,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .setSmallIcon(R.drawable.icon)
            .setTicker("$call: $message")
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(true)
            .build()
    }

    fun getNotificationMgr(ctx: Context): NotificationManager =
        ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun setupNotification(
        n: Notification,
        ctx: Context,
        prefs: PrefsWrapper,
        default: Boolean,
        prefix: String,
    ) {
        // set notification LED
        if (prefs.getBoolean(prefix + "notify_led", default)) {
            n.ledARGB = Color.YELLOW
            n.ledOnMS = 300
            n.ledOffMS = 1000
            n.flags = n.flags or Notification.FLAG_SHOW_LIGHTS
        }
        if (prefs.getBoolean(prefix + "notify_vibr", default)) {
            (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                .vibrate(longArrayOf(0, 200, 200), -1)
        }
        val sound = prefs.getString(prefix + "notify_ringtone", "")
        if (sound.isNotEmpty()) {
            n.sound = Uri.parse(sound)
        }
    }

    fun notifyMessage(ctx: Service, prefs: PrefsWrapper, call: String, message: String) {
        val n = newMessageNotification(ctx, call, message)
        setupNotification(n, ctx, prefs, true, "")
        getNotificationMgr(ctx).notify(getCallNumber(call), n)
    }

    fun cancelMessage(ctx: Context, call: String) {
        getNotificationMgr(ctx).cancel(getCallNumber(call))
    }

    @JvmOverloads
    fun notifyPosition(
        ctx: Service,
        prefs: PrefsWrapper,
        status: String,
        prefix: String = "pos_",
    ) {
        val n = newNotification(ctx, status)
        setupNotification(n, ctx, prefs, false, prefix)
        getNotificationMgr(ctx).notify(SERVICE_NOTIFICATION, n)
    }

    fun start(ctx: Service, status: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // On Android 10+ (API 29+), pass an explicit foreground service
            // type so that Android 14+ (API 34+) per-type permission checks
            // pass. We always need "location"; add "microphone" when
            // RECORD_AUDIO is held (AFSK / DigiRig backends) and
            // "connectedDevice" when BLUETOOTH_CONNECT is held (BT/BLE TNCs).
            var fgsType = 8 // FOREGROUND_SERVICE_TYPE_LOCATION = 1 << 3
            if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                fgsType = fgsType or 128 // FOREGROUND_SERVICE_TYPE_MICROPHONE (API 34+)
            }
            if (ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                fgsType = fgsType or 16 // FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE (API 34+)
            }
            ctx.startForeground(SERVICE_NOTIFICATION, newNotification(ctx, status), fgsType)
        } else {
            ctx.startForeground(SERVICE_NOTIFICATION, newNotification(ctx, status))
        }
    }

    fun stop(ctx: Service) {
        ctx.stopForeground(true)
    }

    companion object {
        @JvmField
        val instance: ServiceNotifier = ServiceNotifier()
    }
}
