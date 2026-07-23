package org.aprsdroid.app

import android.app.Activity
import android.content.Intent

/**
 * Shared navigation helper for switching between main activities.
 *
 * Used by all activities that show the bottom navigation bar.
 */
object AprsNavigation {

    fun navigateTo(activity: Activity, target: NavTarget, prefs: PrefsWrapper) {
        val cls = when (target) {
            NavTarget.HUB -> HubActivity::class.java
            NavTarget.LOG -> LogActivity::class.java
            NavTarget.MAP -> {
                MapModes.startMap(activity, prefs, "")
                return
            }
            NavTarget.MESSAGES -> ConversationsActivity::class.java
        }
        activity.startActivity(
            Intent(activity, cls)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
        activity.overridePendingTransition(0, 0)
    }

    fun openPreferences(activity: Activity) {
        activity.startActivity(Intent(activity, PrefsAct::class.java))
    }
}
