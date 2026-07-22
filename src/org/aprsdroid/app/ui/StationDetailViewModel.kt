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
import org.aprsdroid.app.data.StationEntity

/**
 * ViewModel for the station detail screen (StationActivity).
 *
 * Shows a single station's details, its SSIDs, and its recent
 * packets. Replaces the Scala `StationActivity` +
 * `StationListAdapter` (SSIDs mode) + `PostListAdapter` stack.
 */
class StationDetailViewModel(
    app: Application,
    val targetCall: String,
) : AndroidViewModel(app) {

    private val db = AprsDatabase.get(app)

    /** The target station's position/info. */
    val station: StateFlow<StationEntity?> =
        kotlinx.coroutines.flow.flow {
            emit(db.stationDao().getStaPosition(targetCall))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null,
        )

    /** All SSIDs of the target call. */
    val ssids: StateFlow<List<StationEntity>> =
        db.stationDao().getAllSsids(
            targetCall.substringBefore('-'),
            "${targetCall.substringBefore('-')}-%",
        ).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /** Recent posts/packets for the target station. */
    val posts: StateFlow<List<PostEntity>> =
        db.postDao().getStaPosts(targetCall, "300")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    // Post type colors — post, info, error, incoming, tx, digi, ig
    val postColors: IntArray = intArrayOf(
        0xff30b030.toInt(), 0xffc0c080.toInt(), 0xffffb0b0.toInt(),
        0xff8080b0.toInt(), 0xff30b030.toInt(), 0xfff38c0c.toInt(),
        0xffe3d61c.toInt(),
    )

    class Factory(private val app: Application, private val targetCall: String) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StationDetailViewModel(app, targetCall) as T
        }
    }
}
