package org.aprsdroid.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.data.PostEntity
import org.aprsdroid.app.data.StationEntity

/**
 * Compose screen for the station detail view.
 *
 * Shows the station's info, its SSIDs, and recent packets.
 * Replaces the Scala `StationActivity` + `StationListAdapter` +
 * `PostListAdapter` stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDetailScreen(
    viewModel: StationDetailViewModel,
    onSsidClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val station by viewModel.station.collectAsState()
    val ssids by viewModel.ssids.collectAsState()
    val posts by viewModel.posts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.targetCall) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹ Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Station info
            item {
                StationInfoCard(station)
            }

            // SSIDs section
            if (ssids.isNotEmpty()) {
                item {
                    Text(
                        text = "SSIDs",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(ssids) { ssid ->
                    SsidItem(ssid) { onSsidClick(ssid.call) }
                    HorizontalDivider()
                }
            }

            // Recent packets section
            item {
                Text(
                    text = "Recent Packets",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (posts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(posts) { post ->
                    PostRow(post, viewModel.postColors)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun StationInfoCard(station: StationEntity?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (station == null) {
            Text("Loading station...", style = MaterialTheme.typography.bodyMedium)
            return
        }
        Text(
            text = station.call,
            style = MaterialTheme.typography.headlineSmall,
        )
        if (!station.comment.isNullOrEmpty()) {
            Text(
                text = station.comment,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!station.qrg.isNullOrEmpty()) {
            Text(
                text = "QRG: ${station.qrg}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val latDeg = station.lat / 1000000.0
        val lonDeg = station.lon / 1000000.0
        Text(
            text = "Position: %.4f, %.4f".format(latDeg, lonDeg),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (station.alt != null) {
            Text(
                text = "Altitude: ${station.alt} m",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (station.speed != null) {
            Text(
                text = "Speed: ${station.speed}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SsidItem(station: StationEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = station.call,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (!station.comment.isNullOrEmpty()) {
            Text(
                text = station.comment,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PostRow(post: PostEntity, colors: IntArray) {
    val color = if (post.type < colors.size) colors[post.type] else 0
    val isMonospace = post.type == PostEntity.TYPE_POST ||
        post.type == PostEntity.TYPE_INCMG ||
        post.type == PostEntity.TYPE_TX ||
        post.type == PostEntity.TYPE_DIGI ||
        post.type == PostEntity.TYPE_IG

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = post.status ?: "",
            modifier = Modifier.weight(0.3f),
            style = MaterialTheme.typography.bodySmall,
            color = Color(color),
        )
        Text(
            text = post.message ?: "",
            modifier = Modifier.weight(0.7f),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            ),
            color = Color(color),
        )
    }
}
