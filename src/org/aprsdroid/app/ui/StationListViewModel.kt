package org.aprsdroid.app.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.data.AprsDatabase
import org.aprsdroid.app.data.StationEntity

/**
 * ViewModel for the station list screen (HubActivity).
 *
 * Replaces the Scala `StationListAdapter` (a `SimpleCursorAdapter`
 * with distance/age coloring and filter support) with a
 * Compose-friendly `StateFlow<List<StationEntity>>` backed by Room.
 */
class StationListViewModel(
    app: Application,
    private val myCall: String,
) : AndroidViewModel(app) {

    private val db = AprsDatabase.get(app)
    private val prefs = PrefsWrapper(app)

    val stations: StateFlow<List<StationEntity>> =
        db.stationDao().getStations("300")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    val isMetric: Boolean = prefs.isMetric()
    val showAge: Long = prefs.getShowAge()

    // Age color blending: dark -> bright over 30 minutes
    private val dark = intArrayOf(0xff, 0x80, 0x80, 0x50)
    private val bright = intArrayOf(0xff, 0xff, 0xff, 0xe8)
    private val maxAgeMs = 30 * 60 * 1000L

    fun getAgeColor(ts: Long): Int {
        val delta = System.currentTimeMillis() - ts
        val factor = if (delta < maxAgeMs) delta.toInt() else maxAgeMs.toInt()
        val mix = IntArray(4) { i ->
            bright[i] - (bright[i] - dark[i]) * factor / maxAgeMs.toInt()
        }
        return mix.reduce { acc, v -> acc * 256 + v }
    }

    // Compass bearing
    private val letters = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    fun getBearing(b: Double): String {
        return letters[((b.toInt() + 22 + 720) % 360) / 45]
    }

    /**
     * Compute distance and bearing from `myLat`/`myLon` to the given
     * station. Returns a pair of (distanceText, bearingText).
     */
    fun getDistanceText(myLat: Int, myLon: Int, station: StationEntity): String {
        val dist = FloatArray(2)
        val mcd = 1000000.0
        Location.distanceBetween(
            myLat / mcd, myLon / mcd,
            station.lat / mcd, station.lon / mcd,
            dist,
        )
        val age = android.text.format.DateUtils.getRelativeTimeSpanString(
            getApplication(), station.ts,
        )
        return if (isMetric) {
            val km = dist[0] / 1000.0
            "%1.1f km %s\n%s".format(km, getBearing(dist[1].toDouble()), age)
        } else {
            val mi = dist[0] / 1000.0 * 0.621371
            "%1.1f mi %s\n%s".format(mi, getBearing(dist[1].toDouble()), age)
        }
    }

    companion object {
        const val MODE_SINGLE = 0
        const val MODE_NEIGHBORS = 1
        const val MODE_SSIDS = 2

        class Factory(private val app: Application, private val myCall: String) :
            ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StationListViewModel(app, myCall) as T
            }
        }
    }
}
