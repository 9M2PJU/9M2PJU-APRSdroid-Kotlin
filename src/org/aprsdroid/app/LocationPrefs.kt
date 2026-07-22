package org.aprsdroid.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.aprsdroid.app.ui.PrefItem
import org.aprsdroid.app.ui.PreferenceScreen
import org.aprsdroid.app.ui.stringArray

/**
 * Kotlin/Compose port of `LocationPrefs`.
 *
 * Location source preferences. Dynamically builds the preference
 * list based on the selected location source (SmartBeaconing,
 * Periodic, Manual). Handles "choose on map" and "use last GPS
 * position" actions.
 */
class LocationPrefs : ComponentActivity() {

    private val prefs by lazy { PrefsWrapper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)

        // Handle special data actions from the main preferences screen
        when (intent?.dataString) {
            "chooseOnMap" -> {
                startMapChooser()
                finish()
                return
            }
            "gps2manual" -> {
                gpsToManual()
                finish()
                return
            }
        }

        setContent {
            PreferenceScreen(
                title = getString(R.string.p__location),
                onBack = { finish() },
                items = locationPrefItems(),
            )
        }
    }

    private fun locationPrefItems(): List<PrefItem> {
        val items = mutableListOf<PrefItem>()

        // Location source selection
        items.add(PrefItem.List(
            key = "loc_source",
            title = getString(R.string.p_locsource),
            summary = getString(R.string.p_locsource_summary),
            entries = stringArray(R.array.p_locsource_e),
            entryValues = stringArray(R.array.p_locsource_ev),
            default = "smartbeaconing",
            dialogTitle = getString(R.string.p_locsource),
            onChanged = { recreate() },
        ))

        // Source-specific preferences
        val source = prefs.getString("loc_source", "smartbeaconing")
        items.addAll(sourceItems(source))

        return items
    }

    private fun sourceItems(source: String): List<PrefItem> = when (source) {
        "smartbeaconing" -> smartBeaconingItems()
        "periodic" -> periodicItems()
        "manual" -> manualItems()
        else -> smartBeaconingItems()
    }

    private fun smartBeaconingItems(): List<PrefItem> = listOf(
        PrefItem.Category(title = getString(R.string.p_source_smart)),
        PrefItem.EditText(
            key = "sb.fastspeed",
            title = getString(R.string.p_sb_fast_speed),
            summary = getString(R.string.p_sb_fast_speed_summary),
            default = "100",
            isNumeric = true,
        ),
        PrefItem.EditText(
            key = "sb.fastrate",
            title = getString(R.string.p_sb_fast_rate),
            summary = getString(R.string.p_sb_fast_rate_summary),
            default = "60",
            isNumeric = true,
        ),
        PrefItem.EditText(
            key = "sb.slowspeed",
            title = getString(R.string.p_sb_slow_speed),
            summary = getString(R.string.p_sb_slow_speed_summary),
            default = "5",
            isNumeric = true,
        ),
        PrefItem.EditText(
            key = "sb.slowrate",
            title = getString(R.string.p_sb_slow_rate),
            summary = getString(R.string.p_sb_slow_rate_summary),
            default = "1200",
            isNumeric = true,
        ),
        PrefItem.Category(title = getString(R.string.p_corner_pegging)),
        PrefItem.EditText(
            key = "sb.turntime",
            title = getString(R.string.p_cp_turn_time),
            summary = getString(R.string.p_cp_turn_time_summary),
            default = "15",
            isNumeric = true,
        ),
        PrefItem.EditText(
            key = "sb.turnmin",
            title = getString(R.string.p_cp_turn_angle),
            summary = getString(R.string.p_cp_turn_angle_summary),
            default = "10",
            isNumeric = true,
        ),
        PrefItem.EditText(
            key = "sb.turnslope",
            title = getString(R.string.p_cp_turn_slope),
            summary = getString(R.string.p_cp_turn_slope_summary),
            default = "240",
            isNumeric = true,
        ),
        PrefItem.Clickable(
            title = getString(R.string.p_sb_help),
            summary = getString(R.string.sb_help_url),
            onClick = {
                startActivity(Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse(getString(R.string.sb_help_url))))
            },
        ),
    )

    private fun periodicItems(): List<PrefItem> = listOf(
        PrefItem.Category(title = getString(R.string.p_source_periodic)),
        PrefItem.EditText(
            key = "interval",
            title = getString(R.string.p_interval),
            summary = getString(R.string.p_interval_summary),
            dialogTitle = getString(R.string.p_interval_entry),
            default = "10",
            isNumeric = true,
        ),
        PrefItem.EditText(
            key = "distance",
            title = getString(R.string.p_distance),
            summary = getString(R.string.p_distance_summary),
            dialogTitle = getString(R.string.p_distance_entry),
            default = "10",
            isNumeric = true,
        ),
        PrefItem.List(
            key = "gps_activation",
            title = getString(R.string.p_gps),
            summary = getString(R.string.p_gps_summary),
            entries = stringArray(R.array.p_gps_e),
            entryValues = stringArray(R.array.p_gps_ev),
            default = "med",
            dialogTitle = getString(R.string.p_gps_entry),
        ),
        PrefItem.Switch(
            key = "netloc",
            title = getString(R.string.p_netloc),
            summary = getString(R.string.p_netloc_summary),
        ),
    )

    private fun manualItems(): List<PrefItem> = listOf(
        PrefItem.Category(title = getString(R.string.p_source_manual)),
        PrefItem.EditText(
            key = "manual_lat",
            title = getString(R.string.p_source_lat),
            summary = getString(R.string.p_source_coord),
            dialogTitle = getString(R.string.p_source_lat),
            default = "0.000",
            isNumeric = true,
        ),
        PrefItem.EditText(
            key = "manual_lon",
            title = getString(R.string.p_source_lon),
            summary = getString(R.string.p_source_coord),
            dialogTitle = getString(R.string.p_source_lon),
            default = "0.000",
            isNumeric = true,
        ),
        PrefItem.Clickable(
            title = getString(R.string.p_source_from_map),
            onClick = { startMapChooser() },
        ),
        PrefItem.Clickable(
            title = getString(R.string.p_source_get_last),
            onClick = { gpsToManual() },
        ),
        PrefItem.Switch(
            key = "periodicposition",
            title = getString(R.string.p_source_auto),
            summary = getString(R.string.p_source_auto_summary),
            default = true,
        ),
        PrefItem.EditText(
            key = "interval",
            title = getString(R.string.p_interval),
            summary = getString(R.string.p_interval_summary),
            dialogTitle = getString(R.string.p_interval_entry),
            default = "10",
            isNumeric = true,
            dependency = "periodicposition",
        ),
    )

    private fun startMapChooser() {
        val intent = Intent(this, MapAct::class.java)
        intent.putExtra("info", R.string.p_source_from_map_save)
        startActivityForResult(intent, 0)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val lat = data.getFloatExtra("lat", 0f)
            val lon = data.getFloatExtra("lon", 0f)
            prefs.prefs.edit()
                .putString("manual_lat", lat.toString())
                .putString("manual_lon", lon.toString())
                .apply()
            recreate()
        }
    }

    private fun gpsToManual() {
        try {
            val locMan = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
            val provider = PeriodicGPS.bestProvider(locMan)
            if (provider != null) {
                val loc = locMan.getLastKnownLocation(provider)
                if (loc != null) {
                    prefs.prefs.edit()
                        .putString("manual_lat", loc.latitude.toString())
                        .putString("manual_lon", loc.longitude.toString())
                        .apply()
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Throwable) {
        }
    }
}
