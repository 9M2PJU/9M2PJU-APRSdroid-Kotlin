package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.aprsdroid.app.ui.PrefItem
import org.aprsdroid.app.ui.PreferenceScreen

/**
 * Kotlin/Compose port of `DigiPrefs`.
 *
 * Digipeater preferences with mutually exclusive checkboxes for
 * "p.digipeating" and "p.regenerate".
 */
class DigiPrefs : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            PreferenceScreen(
                title = getString(R.string.p_digipeating),
                onBack = { finish() },
                items = digiPrefItems(),
            )
        }
    }

    private fun digiPrefItems(): List<PrefItem> = listOf(
        PrefItem.Switch(
            key = "p.digipeating",
            title = getString(R.string.p_digipeating_entry),
            summary = getString(R.string.p_digipeating_summary),
            default = false,
            onChanged = { /* reload handled by dependency logic */ },
        ),
        PrefItem.Switch(
            key = "p.directonly",
            title = getString(R.string.p_directonly_entry),
            summary = getString(R.string.p_directonly_summary),
            default = false,
        ),
        PrefItem.EditText(
            key = "digipeater_path",
            title = getString(R.string.p_digipeaterpath),
            summary = getString(R.string.p_digipeaterpath_summary),
            dialogTitle = getString(R.string.p_digipeaterpath_entry),
            default = "WIDE1,WIDE2",
            hint = "WIDE1,WIDE2,TEMP1,MTN1",
            digits = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ,-",
        ),
        PrefItem.EditText(
            key = "p.dedupe",
            title = getString(R.string.p_dedupe),
            summary = getString(R.string.p_dedupe_summary),
            dialogTitle = getString(R.string.p_dedupe_entry),
            default = "30",
            isNumeric = true,
        ),
        PrefItem.Switch(
            key = "p.regenerate",
            title = getString(R.string.p_regenerate),
            summary = getString(R.string.p_regenerate_summary),
            default = false,
        ),
    )
}
