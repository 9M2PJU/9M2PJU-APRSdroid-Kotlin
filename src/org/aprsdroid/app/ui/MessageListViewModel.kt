package org.aprsdroid.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.data.AprsDatabase
import org.aprsdroid.app.data.MessageEntity

/**
 * ViewModel for the message conversation screen (messages with a
 * single callsign).
 *
 * Replaces the Scala `MessageListAdapter` (a `SimpleCursorAdapter`
 * backed by `StorageDatabase` cursors) with a Compose-friendly
 * `StateFlow<List<MessageEntity>>` backed by Room.
 */
class MessageListViewModel(
    app: Application,
    val targetCall: String,
) : AndroidViewModel(app) {

    private val db = AprsDatabase.get(app)
    private val prefs = PrefsWrapper(app)

    val messages: StateFlow<List<MessageEntity>> =
        db.messageDao().getMessages(targetCall)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    /** Max retries from prefs, default 7. */
    val numOfRetries: Int = prefs.getStringInt("p.messaging", 7)

    // Message type colors — null, incoming, out-new, out-acked, out-rejected, out-aborted
    val colors: IntArray = intArrayOf(0, 0xff8080b0.toInt(), 0xff80a080.toInt(),
        0xff30b030.toInt(), 0xffb03030.toInt(), 0xffa08080.toInt())

    class Factory(private val app: Application, private val targetCall: String) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MessageListViewModel(app, targetCall) as T
        }
    }
}
