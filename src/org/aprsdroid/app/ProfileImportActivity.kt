package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.aprsdroid.app.ui.PlaceholderScreen

/**
 * Kotlin/Compose skeleton for `ProfileImportActivity`.
 *
 * Handles `VIEW` intents for `.aprs` profile files. The Scala version
 * parsed the JSON profile and merged it into `SharedPreferences`; the
 * Compose port will show a confirmation dialog. Full logic pending
 * migration.
 */
class ProfileImportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            PlaceholderScreen(getString(R.string.profile_import_activity))
        }
    }
}
