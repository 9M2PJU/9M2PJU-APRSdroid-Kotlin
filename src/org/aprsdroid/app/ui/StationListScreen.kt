package org.aprsdroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.NavTarget
import org.aprsdroid.app.data.StationEntity

/**
 * Compose screen for the station list (HubActivity).
 *
 * Replaces the Scala `HubActivity` + `StationListAdapter` +
 * `SimpleCursorAdapter` stack with a single Compose `LazyColumn`
 * backed by [StationListViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationListScreen(
    viewModel: StationListViewModel,
    onNavigate: (NavTarget) -> Unit = {},
    onPreferences: () -> Unit = {},
) {
    val stations by viewModel.stations.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Stations") })
        },
        bottomBar = {
            AprsBottomBar(
                current = NavTarget.HUB,
                onNavigate = onNavigate,
                onPreferences = onPreferences,
            )
        },
    ) { padding ->
        if (stations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator()
                    Text("Loading stations...")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(stations) { station ->
                    StationItem(station, viewModel)
                }
            }
        }
    }
}

@Composable
private fun StationItem(station: StationEntity, viewModel: StationListViewModel) {
    val ageColor = viewModel.getAgeColor(station.ts)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = station.call,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(ageColor),
            )
            if (!station.comment.isNullOrEmpty()) {
                Text(
                    text = station.comment,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(ageColor),
                )
            }
            if (!station.qrg.isNullOrEmpty()) {
                Text(
                    text = station.qrg,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(ageColor),
                )
            }
        }
        // Distance/age would go here — needs my position from GPS
        // For now, just show relative time
        Text(
            text = android.text.format.DateUtils.getRelativeTimeSpanString(
                viewModel.app, station.ts,
            ).toString(),
            style = MaterialTheme.typography.bodySmall,
            color = Color(ageColor),
        )
    }
}
