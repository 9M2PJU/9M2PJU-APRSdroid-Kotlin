package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.aprsdroid.app.ui.PlaceholderScreen

/**
 * Kotlin/Compose skeleton for `CompressedPrefs`.
 *
 * Compressed-position settings, loaded from `res/xml/compressed.xml`
 * in the Scala version. Full UI pending migration.
 */
class CompressedPrefs : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            PlaceholderScreen(getString(R.string.p__location_compressed_settings))
        }
    }
}
