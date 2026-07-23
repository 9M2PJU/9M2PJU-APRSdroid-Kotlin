package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import org.aprsdroid.app.ui.StationListScreen
import org.aprsdroid.app.ui.StationListViewModel
import org.aprsdroid.app.ui.theme.AprsTheme

/**
 * Kotlin/Compose implementation of `HubActivity`.
 *
 * Shows the list of stations sorted by distance. Uses
 * [StationListViewModel] backed by Room to reactively display
 * stations as they are heard.
 */
class HubActivity : ComponentActivity() {

    private val viewModel: StationListViewModel by viewModels {
        StationListViewModel.Factory(application, PrefsWrapper(this).getCallSsid())
    }
    private val prefs by lazy { PrefsWrapper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            AprsTheme {
                StationListScreen(
                    viewModel = viewModel,
                    onNavigate = { target -> AprsNavigation.navigateTo(this, target, prefs) },
                    onPreferences = { AprsNavigation.openPreferences(this) },
                )
            }
        }
    }
}
