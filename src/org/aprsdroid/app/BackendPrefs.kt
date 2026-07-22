package org.aprsdroid.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.aprsdroid.app.ui.PrefItem
import org.aprsdroid.app.ui.PreferenceScreen
import org.aprsdroid.app.ui.stringArray

/**
 * Kotlin/Compose port of `BackendPrefs`.
 *
 * Backend connection preferences. Dynamically builds the preference
 * list based on the selected protocol (APRS-IS, AFSK, KISS, TNC2,
 * Kenwood) and link type. Handles passcode dialog and GPS permission.
 */
class BackendPrefs : ComponentActivity() {

    private val prefs by lazy { PrefsWrapper(this) }

    private val gpsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            // Grant the kenwood.gps preference
            val sp = getSharedPreferences("org.aprsdroid.app_preferences", MODE_PRIVATE)
            sp.edit().putBoolean("kenwood.gps", true).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            var showPasscode by remember { mutableStateOf(false) }
            PreferenceScreen(
                title = getString(R.string.p_connsetup),
                onBack = { finish() },
                items = backendPrefItems(showPasscode = { showPasscode = true }),
            )
            if (showPasscode) {
                PasscodeDialogCompose(
                    activity = this,
                    firstrun = false,
                    onDismiss = { showPasscode = false },
                )
            }
        }
    }

    private fun backendPrefItems(showPasscode: () -> Unit = {}): List<PrefItem> {
        val items = mutableListOf<PrefItem>()

        // Common backend preferences
        items.add(PrefItem.Switch(
            key = "conn_log",
            title = getString(R.string.p_connlog),
            summary = getString(R.string.p_connlog_summary),
        ))

        // Protocol selection
        val connTypeEntries = stringArray(R.array.p_conntype_e)
        val connTypeValues = stringArray(R.array.p_conntype_ev)
        items.add(PrefItem.List(
            key = "proto",
            title = getString(R.string.p_conntype),
            entries = connTypeEntries,
            entryValues = connTypeValues,
            default = "aprsis",
            dialogTitle = getString(R.string.p_conntype_entry),
            onChanged = { recreate() },
        ))

        // Protocol-specific preferences
        val proto = prefs.getString("proto", "aprsis")
        items.addAll(protoItems(proto, showPasscode))

        return items
    }

    private fun protoItems(proto: String, showPasscode: () -> Unit): List<PrefItem> {
        return when (proto) {
            "aprsis" -> aprsisItems(showPasscode)
            "afsk" -> afskItems()
            "kenwood" -> kenwoodItems()
            "kiss" -> kissItems()
            "tnc2" -> tnc2Items()
            else -> aprsisItems(showPasscode)
        }
    }

    private fun aprsisItems(showPasscode: () -> Unit): List<PrefItem> = listOf(
        PrefItem.Category(title = getString(R.string.p_conn_aprsis)),
        PrefItem.Clickable(
            title = getString(R.string.p_passcode),
            summary = getString(R.string.p_passcode_summary),
            onClick = showPasscode,
        ),
        PrefItem.Clickable(
            title = getString(R.string.p_passreq),
            summary = getString(R.string.p_passreq_summary),
            onClick = {
                startActivity(Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse(getString(R.string.passcode_url))))
            },
        ),
        PrefItem.List(
            key = "aprsis",
            title = getString(R.string.p_link),
            entries = stringArray(R.array.p_aprsis_e),
            entryValues = stringArray(R.array.p_aprsis_ev),
            default = "tcp",
            dialogTitle = getString(R.string.p_link_entry),
        ),
    )

    private fun afskItems(): List<PrefItem> = listOf(
        PrefItem.Category(title = getString(R.string.p_conn_afsk)),
        PrefItem.Switch(
            key = "afsk.hqdemod",
            title = getString(R.string.p_afsk_hqdemod),
            summary = getString(R.string.p_afsk_hqdemod_summary),
            default = true,
        ),
        PrefItem.Switch(
            key = "afsk.btsco",
            title = getString(R.string.p_afsk_btsco),
            summary = getString(R.string.p_afsk_btsco_summary),
            default = false,
            dependency = "afsk.hqdemod",
        ),
        PrefItem.List(
            key = "afsk.output",
            title = getString(R.string.p_afsk_output),
            entries = stringArray(R.array.p_afsk_out_e),
            entryValues = stringArray(R.array.p_afsk_out_ev),
            default = "0",
            dialogTitle = getString(R.string.p_afsk_output),
        ),
        PrefItem.EditText(
            key = "afsk.prefix",
            title = getString(R.string.p_afsk_prefix),
            summary = getString(R.string.p_afsk_prefix_summary),
            dialogTitle = getString(R.string.p_afsk_prefix_entry),
            default = "200",
            isNumeric = true,
        ),
        PrefItem.List(
            key = "afsk",
            title = getString(R.string.p_link),
            entries = stringArray(R.array.p_afsk_e),
            entryValues = stringArray(R.array.p_afsk_ev),
            default = "vox",
            dialogTitle = getString(R.string.p_link_entry),
        ),
    )

    private fun kenwoodItems(): List<PrefItem> = listOf(
        PrefItem.Category(title = getString(R.string.p_conn_kwd)),
        PrefItem.Clickable(
            title = getString(R.string.p_conn_kwd_info),
            onClick = {
                startActivity(Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse(getString(R.string.kwd_help_url))))
            },
        ),
        PrefItem.Switch(
            key = "kenwood.gps",
            title = getString(R.string.p_conn_kwd_gps),
            summary = getString(R.string.p_conn_kwd_gps_summary),
            default = false,
            onChanged = { checked ->
                if (checked) {
                    gpsPermissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ))
                }
            },
        ),
        PrefItem.Switch(
            key = "kenwood.gps_debug",
            title = getString(R.string.p_conn_kwd_gps_debug),
            summary = getString(R.string.p_conn_kwd_gps_debug_summary),
            default = false,
            dependency = "kenwood.gps",
        ),
        PrefItem.List(
            key = "link",
            title = getString(R.string.p_link),
            entries = stringArray(R.array.p_link_e),
            entryValues = stringArray(R.array.p_link_ev),
            default = "bluetooth",
            dialogTitle = getString(R.string.p_link_entry),
        ),
    )

    private fun kissItems(): List<PrefItem> = listOf(
        PrefItem.Category(title = getString(R.string.p_conn_kiss)),
        PrefItem.EditText(
            key = "kiss.init",
            title = getString(R.string.p_tnc_init),
            summary = getString(R.string.p_tnc_init_summary),
            dialogTitle = getString(R.string.p_tnc_init),
            default = "",
        ),
        PrefItem.EditText(
            key = "kiss.delay",
            title = getString(R.string.p_tnc_delay),
            summary = getString(R.string.p_tnc_delay_summary),
            dialogTitle = getString(R.string.p_tnc_delay_entry),
            default = "300",
            isNumeric = true,
        ),
        PrefItem.List(
            key = "link",
            title = getString(R.string.p_link),
            entries = stringArray(R.array.p_link_e),
            entryValues = stringArray(R.array.p_link_ev),
            default = "bluetooth",
            dialogTitle = getString(R.string.p_link_entry),
        ),
    )

    private fun tnc2Items(): List<PrefItem> = listOf(
        PrefItem.Category(title = getString(R.string.p_conn_tnc2)),
        PrefItem.List(
            key = "link",
            title = getString(R.string.p_link),
            entries = stringArray(R.array.p_link_e),
            entryValues = stringArray(R.array.p_link_ev),
            default = "bluetooth",
            dialogTitle = getString(R.string.p_link_entry),
        ),
    )
}
