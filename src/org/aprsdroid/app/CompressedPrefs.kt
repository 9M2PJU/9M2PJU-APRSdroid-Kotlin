package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.aprsdroid.app.ui.PrefItem
import org.aprsdroid.app.ui.PreferenceScreen
import org.aprsdroid.app.ui.stringArray

/**
 * Kotlin/Compose port of `CompressedPrefs`.
 *
 * Compressed position preferences with mutually exclusive checkboxes
 * for "compressed_location" and "compressed_mice", and a list for
 * MICE status.
 */
class CompressedPrefs : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            PreferenceScreen(
                title = getString(R.string.p__location_compressed_settings),
                onBack = { finish() },
                items = compressedPrefItems(),
            )
        }
    }

    private fun compressedPrefItems(): List<PrefItem> {
        val miceStatuses = stringArray(R.array.compressed_mice_status)
        return listOf(
            PrefItem.Switch(
                key = "compressed_location",
                title = getString(R.string.p__location_compressed_beacons),
                summaryOn = getString(R.string.p__location_compressed_beacons_on),
                summaryOff = getString(R.string.p__location_compressed_beacons_off),
                default = false,
            ),
            PrefItem.Switch(
                key = "compressed_mice",
                title = getString(R.string.p__location_mice_beacons),
                summaryOn = getString(R.string.p__location_mice_beacons_on),
                summaryOff = getString(R.string.p__location_mice_beacons_off),
                default = false,
            ),
            PrefItem.List(
                key = "p__location_mice_status",
                title = getString(R.string.p__location_mice_status),
                entries = miceStatuses,
                entryValues = miceStatuses,
                default = "Off Duty",
                dialogTitle = getString(R.string.p__location_mice_status),
            ),
        )
    }
}
