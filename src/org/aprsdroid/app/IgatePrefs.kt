package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.aprsdroid.app.ui.PrefItem
import org.aprsdroid.app.ui.PreferenceScreen

/**
 * Kotlin/Compose port of `IgatePrefs`.
 *
 * Igate preferences. Disables the "p.igating" toggle while the
 * APRS service is running.
 */
class IgatePrefs : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        val prefs = PrefsWrapper(this)
        val isServiceRunning = prefs.getBoolean("service_running", false)
        setContent {
            PreferenceScreen(
                title = getString(R.string.p_igating),
                onBack = { finish() },
                items = igatePrefItems(isServiceRunning),
            )
        }
    }

    private fun igatePrefItems(isServiceRunning: Boolean): List<PrefItem> = listOf(
        PrefItem.Switch(
            key = "p.igating",
            title = getString(R.string.p_igating_entry),
            summary = if (isServiceRunning)
                "Setting disabled while the service is running."
            else getString(R.string.p_igating_summary),
            default = false,
            enabled = !isServiceRunning,
        ),
        PrefItem.Switch(
            key = "p.aprsistraffic",
            title = getString(R.string.p_aprsistraffic_entry),
            summary = getString(R.string.p_aprsistraffic_summary),
            default = false,
        ),
        PrefItem.Category(title = getString(R.string.p__igateserver)),
        PrefItem.EditText(
            key = "p.igserver",
            title = getString(R.string.p_igserver),
            summary = getString(R.string.p_igserver_summary),
            dialogTitle = getString(R.string.p_igserver_entry),
            hint = getString(R.string.p_igserver_hint),
            default = "aprs.hamradio.my",
        ),
        PrefItem.EditText(
            key = "p.igfilter",
            title = getString(R.string.p_igfilter),
            summary = getString(R.string.p_igfilter_summary),
            dialogTitle = getString(R.string.p_igfilter_entry),
            hint = getString(R.string.p_igfilter_hint),
        ),
        PrefItem.EditText(
            key = "p.igsotimeout",
            title = getString(R.string.p_igsotimeout),
            summary = getString(R.string.p_igsotimeout_summary),
            dialogTitle = getString(R.string.p_igsotimeout_entry),
            default = "120",
            isNumeric = true,
        ),
        PrefItem.EditText(
            key = "p.igconnectretry",
            title = getString(R.string.p_igconnectretry),
            summary = getString(R.string.p_igconnectretry_summary),
            dialogTitle = getString(R.string.p_igconnectretry_entry),
            default = "30",
            isNumeric = true,
        ),
        PrefItem.Category(title = getString(R.string.p__igaterf)),
        PrefItem.Switch(
            key = "p.aprsistorf",
            title = getString(R.string.p_aprsistorf_entry),
            summary = getString(R.string.p_aprsistorf_summary),
            default = false,
        ),
        PrefItem.EditText(
            key = "p.timelastheard",
            title = getString(R.string.p_timelastheard),
            summary = getString(R.string.p_timelastheard_summary),
            dialogTitle = getString(R.string.p_timelastheard_entry),
            default = "30",
            isNumeric = true,
        ),
        PrefItem.EditText(
            key = "igpath",
            title = getString(R.string.p_igpath),
            summary = getString(R.string.p_igpath_summary),
            dialogTitle = getString(R.string.p_igpath_entry),
            default = "WIDE1-1,WIDE2-1",
            hint = "WIDE1-1,WIDE2-1",
            digits = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ,-",
        ),
        PrefItem.Category(title = getString(R.string.p__ratelimit)),
        PrefItem.EditText(
            key = "p.ratelimit1",
            title = getString(R.string.p_ratelimit1),
            summary = getString(R.string.p_ratelimit1_summary),
            dialogTitle = getString(R.string.p_ratelimit1_entry),
            default = "6",
            isNumeric = true,
        ),
        PrefItem.EditText(
            key = "p.ratelimit5",
            title = getString(R.string.p_ratelimit5),
            summary = getString(R.string.p_ratelimit5_summary),
            dialogTitle = getString(R.string.p_ratelimit5_entry),
            default = "10",
            isNumeric = true,
        ),
    )
}
