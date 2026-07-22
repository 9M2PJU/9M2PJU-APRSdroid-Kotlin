package org.aprsdroid.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import org.aprsdroid.app.ui.StationDetailScreen
import org.aprsdroid.app.ui.StationDetailViewModel
import org.aprsdroid.app.ui.theme.AprsTheme

/**
 * Kotlin/Compose implementation of `StationActivity`.
 *
 * Shows a single station's details: position, comment, QRG, SSIDs,
 * and recent packets. Uses [StationDetailViewModel] backed by Room
 * to reactively display data.
 */
class StationActivity : ComponentActivity() {

    private val targetCall: String by lazy {
        intent.getStringExtra("call") ?: ""
    }

    private val viewModel: StationDetailViewModel by viewModels {
        StationDetailViewModel.Factory(application, targetCall)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            AprsTheme {
                StationDetailScreen(
                    viewModel = viewModel,
                    onSsidClick = { call ->
                        val intent = Intent(this, StationActivity::class.java)
                        intent.putExtra("call", call)
                        startActivity(intent)
                    },
                    onBack = { finish() },
                )
            }
        }
    }
}
