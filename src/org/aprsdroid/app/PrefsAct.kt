package org.aprsdroid.app

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.preference.PreferenceManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.aprsdroid.app.ui.PrefItem
import org.aprsdroid.app.ui.PreferenceScreen
import org.aprsdroid.app.ui.stringArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Kotlin/Compose port of the Scala `PrefsAct`.
 *
 * Main preferences screen built with Jetpack Compose. Handles
 * profile import/export, offline map file picker, all-files-access
 * settings, and restart dialog when offline mapping is toggled.
 */
class PrefsAct : ComponentActivity() {

    private val prefs by lazy { PrefsWrapper(this) }

    private val tilepathPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: SecurityException) {
            }
            val resolvedPath = when (uri.scheme) {
                "file" -> uri.path
                "content" -> resolveContentUri(uri)
                else -> null
            }
            if (resolvedPath != null) {
                PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putString("tilepath", resolvedPath)
                    .commit()
                Toast.makeText(
                    this,
                    getString(R.string.selected_file, File(resolvedPath).name),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                Toast.makeText(this, R.string.mapfile_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val profileImportPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val intent = Intent(this, ProfileImportActivity::class.java)
            intent.data = uri
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            var showMenu by remember { mutableStateOf(false) }
            PreferenceScreen(
                title = getString(R.string.app_prefs),
                onBack = { finish() },
                items = prefsItems(),
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(getString(R.string.profile_load)) },
                            onClick = {
                                showMenu = false
                                profileImportPicker.launch(arrayOf("*/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(getString(R.string.profile_export)) },
                            onClick = {
                                showMenu = false
                                exportPrefs()
                            },
                        )
                    }
                },
            )
        }
    }

    private fun prefsItems(): List<PrefItem> {
        val items = mutableListOf<PrefItem>()

        // ---- APRS category ----
        items.add(PrefItem.Category(title = getString(R.string.p__aprs)))
        items.add(PrefItem.EditText(
            key = "callsign",
            title = getString(R.string.p_callsign_nossid),
            summary = getString(R.string.p_callsign_summary),
            dialogTitle = getString(R.string.p_callsign_entry),
            hint = getString(R.string.p_callsign_nossid),
            digits = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            maxLength = 7,
        ))
        items.add(PrefItem.List(
            key = "ssid",
            title = getString(R.string.p_ssid),
            summary = getString(R.string.p_ssid_summary),
            entries = stringArray(R.array.p_ssid_e),
            entryValues = stringArray(R.array.p_ssid_ev),
            default = "5",
            dialogTitle = getString(R.string.p_ssid_entry),
        ))
        items.add(PrefItem.EditText(
            key = "digi_path",
            title = getString(R.string.p_aprs_path),
            summary = getString(R.string.p_aprs_path_summary),
            dialogTitle = getString(R.string.p_aprs_path_entry),
            default = "WIDE1-1",
            hint = "hop1,hop2,..",
            digits = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ,-",
        ))

        // ---- Connection category ----
        items.add(PrefItem.Category(title = getString(R.string.p__connection)))
        items.add(PrefItem.Clickable(
            title = getString(R.string.p_connsetup),
            summary = prefs.getBackendName(),
            onClick = {
                startActivity(Intent(this, BackendPrefs::class.java))
            },
        ))

        // ---- Offline map category ----
        items.add(PrefItem.Category(title = getString(R.string.p_offlinemap)))
        val tilepath = prefs.prefs.getString("tilepath", null)
        items.add(PrefItem.Clickable(
            title = getString(R.string.p_mbtiles_file_picker_title),
            summary = tilepath ?: getString(R.string.p_mbtiles_file_picker_summary),
            onClick = { tilepathPicker.launch(arrayOf("*/*")) },
        ))
        items.add(PrefItem.Switch(
            key = "p.offlinemap",
            title = getString(R.string.p_offlinemap_entry),
            summary = getString(R.string.p_offlinemap_summary),
            default = false,
            onChanged = { checked -> if (checked) showRestartDialog() },
        ))
        items.add(PrefItem.Clickable(
            title = getString(R.string.p_all_files_access_title),
            summary = getString(R.string.p_all_files_access_summary),
            onClick = { openAllFilesAccessSettings() },
        ))
        items.add(PrefItem.Switch(
            key = "hardware_acceleration",
            title = getString(R.string.p_hwacceleration_title),
            summary = getString(R.string.p_hwacceleration_summary),
            default = true,
        ))

        // ---- Units category ----
        items.add(PrefItem.Category(title = getString(R.string.p_units_title)))
        items.add(PrefItem.List(
            key = "p.units",
            title = getString(R.string.p_units),
            entries = stringArray(R.array.p_units_e),
            entryValues = stringArray(R.array.p_units_ev),
            default = "1",
            dialogTitle = getString(R.string.p_units_entry),
        ))

        // ---- Digipeating category ----
        items.add(PrefItem.Category(title = getString(R.string.p__digipeating)))
        items.add(PrefItem.Clickable(
            title = getString(R.string.p_digipeating),
            summary = getString(R.string.p_digipeating_preferences),
            onClick = { startActivity(Intent(this, DigiPrefs::class.java)) },
        ))

        // ---- Igate category ----
        items.add(PrefItem.Category(title = getString(R.string.p__igating)))
        items.add(PrefItem.Clickable(
            title = getString(R.string.p_igating),
            summary = getString(R.string.p_igating_preferences),
            onClick = { startActivity(Intent(this, IgatePrefs::class.java)) },
        ))

        // ---- Messaging category ----
        items.add(PrefItem.Category(title = getString(R.string.p__messaging)))
        items.add(PrefItem.Clickable(
            title = getString(R.string.p_messaging),
            summary = getString(R.string.p_messaging_preferences),
            onClick = { startActivity(Intent(this, MessagingPrefs::class.java)) },
        ))

        // ---- Position category ----
        items.add(PrefItem.Category(title = getString(R.string.p__position)))
        items.add(PrefItem.Clickable(
            title = getString(R.string.p__location_compressed_settings),
            summary = getString(R.string.p__location_compressed_summary),
            onClick = { startActivity(Intent(this, CompressedPrefs::class.java)) },
        ))
        items.add(PrefItem.Clickable(
            title = getString(R.string.p_symbol),
            summary = getString(R.string.p_symbol_summary) + ": " +
                prefs.getString("symbol", "/$"),
            onClick = { startActivity(Intent(this, PrefSymbolAct::class.java)) },
        ))
        items.add(PrefItem.EditText(
            key = "frequency",
            title = getString(R.string.p_frequency),
            summary = getString(R.string.p_frequency_summary),
            dialogTitle = getString(R.string.p_frequency_summary),
            isNumeric = true,
        ))
        items.add(PrefItem.EditText(
            key = "status",
            title = getString(R.string.p_status),
            summary = getString(R.string.p_status_summary),
            dialogTitle = getString(R.string.p_status_entry),
            default = getString(R.string.default_status),
            hint = getString(R.string.default_status),
            maxLength = 50,
        ))
        items.add(PrefItem.Clickable(
            title = getString(R.string.p__location),
            summary = prefs.getLocationSourceName(),
            onClick = { startActivity(Intent(this, LocationPrefs::class.java)) },
        ))

        // ---- Privacy sub-section ----
        items.add(PrefItem.Category(title = getString(R.string.p_privacy)))
        items.add(PrefItem.List(
            key = "priv_ambiguity",
            title = getString(R.string.p_priv_ambiguity),
            summary = getString(R.string.p_priv_ambiguity_summary),
            entries = stringArray(R.array.p_ambiguity_e),
            entryValues = stringArray(R.array.p_ambiguity_ev),
            default = "0",
            dialogTitle = getString(R.string.p_priv_ambiguity),
        ))
        items.add(PrefItem.Switch(
            key = "priv_spdbear",
            title = getString(R.string.p_priv_spdbear),
            summary = getString(R.string.p_priv_spdbear_summary),
            default = true,
        ))
        items.add(PrefItem.Switch(
            key = "priv_altitude",
            title = getString(R.string.p_priv_altitude),
            summary = getString(R.string.p_priv_altitude_summary),
            default = true,
        ))

        // ---- Display category ----
        items.add(PrefItem.Category(title = getString(R.string.p__display)))
        items.add(PrefItem.Switch(
            key = "keepscreen",
            title = getString(R.string.p_keepscreen),
            summary = getString(R.string.p_keepscreen_summary),
        ))

        // ---- Notification: messages ----
        items.add(PrefItem.Category(title = getString(R.string.p_msg)))
        items.add(PrefItem.Switch(
            key = "notify_led",
            title = getString(R.string.p_msg_led),
            summaryOn = getString(R.string.p_msg_led_on),
            summaryOff = getString(R.string.p_msg_led_off),
            default = true,
        ))
        items.add(PrefItem.Switch(
            key = "notify_vibr",
            title = getString(R.string.p_msg_vibr),
            summaryOn = getString(R.string.p_msg_vibr_on),
            summaryOff = getString(R.string.p_msg_vibr_off),
            default = true,
        ))
        items.add(PrefItem.Ringtone(
            key = "notify_ringtone",
            title = getString(R.string.p_msg_ring),
            summary = getString(R.string.p_msg_ring_summary),
        ))

        // ---- Notification: position reports ----
        items.add(PrefItem.Category(title = getString(R.string.p_pos)))
        items.add(PrefItem.Switch(
            key = "pos_notify_led",
            title = getString(R.string.p_msg_led),
            summaryOn = getString(R.string.p_msg_led_on),
            summaryOff = getString(R.string.p_msg_led_off),
            default = false,
        ))
        items.add(PrefItem.Switch(
            key = "pos_notify_vibr",
            title = getString(R.string.p_msg_vibr),
            summaryOn = getString(R.string.p_msg_vibr_on),
            summaryOff = getString(R.string.p_msg_vibr_off),
            default = false,
        ))
        items.add(PrefItem.Ringtone(
            key = "pos_notify_ringtone",
            title = getString(R.string.p_pos_ring),
            summary = getString(R.string.p_pos_ring_summary),
        ))

        // ---- Notification: digipeated position reports ----
        items.add(PrefItem.Category(title = getString(R.string.p_dgp)))
        items.add(PrefItem.Switch(
            key = "dgp_notify_led",
            title = getString(R.string.p_msg_led),
            summaryOn = getString(R.string.p_msg_led_on),
            summaryOff = getString(R.string.p_msg_led_off),
            default = false,
        ))
        items.add(PrefItem.Switch(
            key = "dgp_notify_vibr",
            title = getString(R.string.p_msg_vibr),
            summaryOn = getString(R.string.p_msg_vibr_on),
            summaryOff = getString(R.string.p_msg_vibr_off),
            default = false,
        ))
        items.add(PrefItem.Ringtone(
            key = "dgp_notify_ringtone",
            title = getString(R.string.p_dgp_ring),
            summary = getString(R.string.p_dgp_ring_summary),
        ))

        // ---- Notification: tracking start/stop ----
        items.add(PrefItem.Category(title = getString(R.string.p_tracking)))
        items.add(PrefItem.Ringtone(
            key = "start_notify_ringtone",
            title = getString(R.string.p_start_ring),
            summary = getString(R.string.p_start_ring_summary),
        ))
        items.add(PrefItem.Ringtone(
            key = "stop_notify_ringtone",
            title = getString(R.string.p_stop_ring),
            summary = getString(R.string.p_stop_ring_summary),
        ))

        // ---- Frequency control category ----
        items.add(PrefItem.Category(title = getString(R.string.freq_control)))
        items.add(PrefItem.Switch(
            key = "freq_control",
            title = getString(R.string.freq_control_options),
            summary = getString(R.string.freq_control_options_summary),
            default = false,
        ))
        items.add(PrefItem.EditText(
            key = "frequency_control_value",
            title = getString(R.string.freq_control_title),
            summary = getString(R.string.freq_control_summary),
            dialogTitle = getString(R.string.freq_control_title),
            default = "144.390",
            hint = getString(R.string.freq_control_hint),
            isNumeric = true,
        ))

        return items
    }

    private fun exportPrefs() {
        val filename = "profile-${SimpleDateFormat("yyyyMMdd-HHmm").format(Date())}.aprs"
        val directory = UIHelper.getExportDirectory(this)
        val file = File(directory, filename)
        try {
            directory.mkdirs()
            val allPrefs = PreferenceManager.getDefaultSharedPreferences(this).all
            allPrefs.remove("map_zoom")
            val json = JSONObject(allPrefs)
            PrintWriter(file).use { fo ->
                fo.println(json.toString(2))
            }
            UIHelper.shareFile(this, file, filename)
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "", Toast.LENGTH_LONG).show()
        }
    }

    private fun showRestartDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.restart_required_title)
            .setMessage(R.string.restart_required_body)
            .setPositiveButton(R.string.restart_now) { _, _ ->
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                finishAffinity()
                if (intent != null) startActivity(intent)
                Process.killProcess(Process.myPid())
            }
            .setNegativeButton(R.string.restart_later, null)
            .show()
    }

    private fun openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } else {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun resolveContentUri(uri: Uri): String? {
        val parts = uri.path?.replace("/document/", "")?.split(":", limit = 2) ?: return null
        val storage = parts[0]
        val path = parts.getOrNull(1) ?: return null
        Log.d("PrefsAct", "resolveContentUri s=$storage p=$path")
        return if (storage == "primary") {
            "${Environment.getExternalStorageDirectory()}/$path"
        } else {
            "/storage/$storage/$path"
        }
    }
}
