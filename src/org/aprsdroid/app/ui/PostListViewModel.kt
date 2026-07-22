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
import org.aprsdroid.app.data.PostEntity

/**
 * ViewModel for the packet log / activity feed screen.
 *
 * Replaces the Scala `PostListAdapter` (a `SimpleCursorAdapter` with
 * a custom `ViewBinder` for color-coding by post type) with a
 * Compose-friendly `StateFlow<List<PostEntity>>` backed by Room.
 */
class PostListViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AprsDatabase.get(app)

    val posts: StateFlow<List<PostEntity>> =
        db.postDao().getPosts("300")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    // Post type colors — post, info, error, incoming, tx, digi, ig
    val colors: IntArray = intArrayOf(
        0xff30b030.toInt(),  // TYPE_POST
        0xffc0c080.toInt(),  // TYPE_INFO
        0xffffb0b0.toInt(),  // TYPE_ERROR
        0xff8080b0.toInt(),  // TYPE_INCMG
        0xff30b030.toInt(),  // TYPE_TX
        0xfff38c0c.toInt(),  // TYPE_DIGI
        0xffe3d61c.toInt(),  // TYPE_IG
    )

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PostListViewModel(app) as T
        }
    }
}
