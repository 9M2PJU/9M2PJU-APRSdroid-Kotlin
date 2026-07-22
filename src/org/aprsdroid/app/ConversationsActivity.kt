package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.aprsdroid.app.ui.PlaceholderScreen

/**
 * Kotlin/Compose skeleton for `ConversationsActivity`.
 *
 * Shows the list of APRS message conversations. The full list UI is
 * pending migration from the Scala `ConversationListAdapter`; this stub
 * lets the manifest reference resolve and the build succeed.
 */
class ConversationsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            PlaceholderScreen(getString(R.string.app_messages))
        }
    }
}
