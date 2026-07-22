package org.aprsdroid.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.aprsdroid.app.data.AprsDatabase
import org.aprsdroid.app.data.MessageEntity

/**
 * ViewModel for the conversations list screen.
 *
 * Replaces the Scala `ConversationListAdapter` (a
 * `SimpleCursorAdapter` backed by `StorageDatabase` cursors) with a
 * Compose-friendly `StateFlow<List<MessageEntity>>` backed by Room.
 *
 * The list is automatically updated when the database changes, thanks
 * to Room's reactive `Flow` support — no manual broadcast receiver
 * reloading is needed (the Scala version used `LocationReceiver2`
 * for this).
 */
class ConversationsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AprsDatabase.get(app)

    /**
     * The list of conversations (most recent message per callsign).
     */
    val conversations: StateFlow<List<MessageEntity>> =
        db.messageDao().getConversations()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    // Message type colors — null, incoming, out-new, out-acked, out-rejected
    val colors: IntArray = intArrayOf(0, 0xff8080b0.toInt(), 0xff80a080.toInt(),
        0xff30b030.toInt(), 0xffb03030.toInt())

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConversationsViewModel(app) as T
        }
    }
}
