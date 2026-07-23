package org.aprsdroid.app

import android.content.Intent
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
 * messages as they arrive. Includes message input, send button,
 * context menu, and start-tracking dialog.
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

        // Cancel notification for this call
        ServiceNotifier.instance.cancelMessage(this, targetCall)

        setContent {
            AprsTheme {
                MessageListScreen(
                    viewModel = viewModel,
                    myCall = PrefsWrapper(this).getCallSsid(),
                    onBack = { finish() },
                    onStartService = {
                        startService(AprsService.intent(this, AprsService.SERVICE))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ServiceNotifier.instance.cancelMessage(this, targetCall)
    }
}
