package org.aprsdroid.app.ui

import android.location.Location
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.NavTarget
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.data.StationEntity

/**
 * Compose screen for the station list (HubActivity).
 *
 * Shows nearby stations sorted by distance, with search/filter,
 * distance/bearing display, and long-press context menu.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StationListScreen(
    viewModel: StationListViewModel,
    onNavigate: (NavTarget) -> Unit = {},
    onPreferences: () -> Unit = {},
) {
    val stations by viewModel.stations.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var contextMenuCall by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val myLat = remember { PrefsWrapper(context).prefs.getInt("last_lat", 0) }
    val myLon = remember { PrefsWrapper(context).prefs.getInt("last_lon", 0) }
    val hasMyPos = myLat != 0 || myLon != 0

    val filteredStations by remember(stations, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) stations
            else stations.filter { it.call.contains(searchQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stations") },
                actions = {
                    AprsOverflowMenu(
                        showAgeFilter = true,
                        onPreferences = onPreferences,
                        onAgeChanged = { /* trigger refresh */ },
                    )
                },
            )
        },
        bottomBar = {
            AprsBottomBar(
                current = NavTarget.HUB,
                onNavigate = onNavigate,
                onPreferences = onPreferences,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it.uppercase() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search callsign...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )

            if (stations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Text(
                            "No stations yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Start tracking to see nearby stations.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (filteredStations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No stations match \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filteredStations) { station ->
                        StationItem(
                            station = station,
                            viewModel = viewModel,
                            hasMyPos = hasMyPos,
                            myLat = myLat,
                            myLon = myLon,
                            onClick = { /* could open details */ },
                            onLongClick = { contextMenuCall = station.call },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    // Context menu
    contextMenuCall?.let { call ->
        CallsignContextMenu(
            call = call,
            expanded = true,
            onDismiss = { contextMenuCall = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StationItem(
    station: StationEntity,
    viewModel: StationListViewModel,
    hasMyPos: Boolean,
    myLat: Int,
    myLon: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val ageColor = viewModel.getAgeColor(station.ts)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = station.call,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                ),
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
        // Distance/bearing or relative time
        Column(
            horizontalAlignment = Alignment.End,
        ) {
            if (hasMyPos) {
                Text(
                    text = viewModel.getDistanceText(myLat, myLon, station),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(ageColor),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            } else {
                Text(
                    text = android.text.format.DateUtils.getRelativeTimeSpanString(
                        viewModel.app, station.ts,
                    ).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(ageColor),
                )
            }
        }
    }
}
