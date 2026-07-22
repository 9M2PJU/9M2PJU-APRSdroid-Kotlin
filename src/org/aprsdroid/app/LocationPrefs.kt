package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.aprsdroid.app.ui.PlaceholderScreen

/**
 * Kotlin/Compose skeleton for `LocationPrefs`.
 *
 * Location source preferences (manual, periodic GPS, smart beaconing).
 * The Scala version dynamically loaded `res/xml/location.xml` plus a
 * source-specific XML via `LocationSource.instanciatePrefsAct`.
 */
class LocationPrefs : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            PlaceholderScreen(getString(R.string.p__location))
        }
    }
}
