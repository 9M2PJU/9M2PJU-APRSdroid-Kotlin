package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.aprsdroid.app.ui.PlaceholderScreen

/**
 * Kotlin/Compose skeleton for `StationActivity`.
 *
 * Shows per-station details (SSIDs, packets). Full UI pending migration
 * from the Scala `StationHelper` / `StationListAdapter` / `PostListAdapter`.
 */
class StationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            PlaceholderScreen(getString(R.string.app_sta))
        }
    }
}
