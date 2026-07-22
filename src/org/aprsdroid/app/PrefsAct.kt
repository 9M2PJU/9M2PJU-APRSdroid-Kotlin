package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.aprsdroid.app.ui.PlaceholderScreen

/**
 * Kotlin/Compose skeleton for `PrefsAct`.
 *
 * Central preferences screen. The Scala version used a PreferenceActivity
 * with `res/xml/preferences.xml`; the Compose port will use a
 * `LazyColumn` of preference items. Full UI pending migration.
 */
class PrefsAct : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            PlaceholderScreen(getString(R.string.app_prefs))
        }
    }
}
