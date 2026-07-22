package org.aprsdroid.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import org.aprsdroid.app.ui.ConversationsScreen
import org.aprsdroid.app.ui.ConversationsViewModel
import org.aprsdroid.app.ui.theme.AprsTheme

/**
 * Kotlin/Compose implementation of `ConversationsActivity`.
 *
 * Shows the list of message conversations (most recent message per
 * callsign). Tapping a conversation opens [MessageActivity] with
 * that callsign.
 */
class ConversationsActivity : ComponentActivity() {

    private val viewModel: ConversationsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            AprsTheme {
                ConversationsScreen(viewModel) { call ->
                    val intent = Intent(this, MessageActivity::class.java)
                    intent.putExtra("call", call)
                    startActivity(intent)
                }
            }
        }
    }
}
