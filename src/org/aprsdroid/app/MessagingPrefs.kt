package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.aprsdroid.app.ui.PrefItem
import org.aprsdroid.app.ui.PreferenceScreen

/**
 * Kotlin/Compose port of `MessagingPrefs`.
 *
 * Messaging preferences screen built with Jetpack Compose, replacing
 * the old PreferenceActivity + XML framework.
 */
class MessagingPrefs : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            PreferenceScreen(
                title = getString(R.string.p__messaging),
                onBack = { finish() },
                items = messagingPrefItems(),
            )
        }
    }

    private fun messagingPrefItems(): List<PrefItem> = listOf(
        PrefItem.EditText(
            key = "p.messaging",
            title = getString(R.string.p_message_retry),
            summary = getString(R.string.p_message_retry_summary),
            dialogTitle = getString(R.string.p_message_retry_entry),
            default = "7",
            isNumeric = true,
        ),
        PrefItem.EditText(
            key = "p.retry",
            title = getString(R.string.p_retry_interval),
            summary = getString(R.string.p_retry_interval_summary),
            dialogTitle = getString(R.string.p_retry_interval_entry),
            default = "30",
            isNumeric = true,
        ),
        PrefItem.Switch(
            key = "p.ackdupetoggle",
            title = getString(R.string.p_ackdupetoggle),
            summary = getString(R.string.p_ackdupetoggle_summary),
        ),
        PrefItem.EditText(
            key = "p.ackdupe",
            title = getString(R.string.p_ackdupe_interval),
            summary = getString(R.string.p_ackdupe_interval_summary),
            dialogTitle = getString(R.string.p_ackdupe_interval_entry),
            default = "30",
            isNumeric = true,
            dependency = "p.ackdupetoggle",
        ),
        PrefItem.Switch(
            key = "p.msgdupetoggle",
            title = getString(R.string.p_msgdupetoggle),
            summary = getString(R.string.p_msgdupetoggle_summary),
        ),
        PrefItem.EditText(
            key = "p.msgdupetime",
            title = getString(R.string.p_msgdupetime_title),
            summary = getString(R.string.p_msgdupetime_summary),
            dialogTitle = getString(R.string.p_msgdupetime_entry),
            default = "30",
            isNumeric = true,
            dependency = "p.msgdupetoggle",
        ),
    )
}
