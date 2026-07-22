package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.ui.MessageListScreen
import org.aprsdroid.app.ui.MessageListViewModel
import org.aprsdroid.app.ui.theme.AprsTheme

/**
 * Kotlin/Compose implementation of `MessageActivity`.
 *
 * Shows a message thread with a single callsign. Uses
 * [MessageListViewModel] backed by Room to reactively display
 * messages as they arrive.
 */
class MessageActivity : ComponentActivity() {

    private val targetCall: String by lazy {
        intent.getStringExtra("call") ?: ""
    }

    private val viewModel: MessageListViewModel by viewModels {
        MessageListViewModel.Factory(application, targetCall)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            AprsTheme {
                MessageListScreen(
                    viewModel = viewModel,
                    myCall = PrefsWrapper(this).getCallSsid(),
                    onBack = { finish() },
                )
            }
        }
    }
}
