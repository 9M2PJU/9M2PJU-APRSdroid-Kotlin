package org.aprsdroid.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.MapModes
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.data.PostEntity
import org.aprsdroid.app.data.StationEntity

/**
 * Compose screen for the station detail view.
 *
 * Shows the station's info, its SSIDs, and recent packets.
 * Includes action buttons: Map, aprs.fi, QRZ.com.
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
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.targetCall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Station info card with action buttons
            item {
                StationInfoCard(
                    station = station,
                    onMapClick = {
                        MapModes.startMap(context, PrefsWrapper(context), viewModel.targetCall)
                    },
                    onAprsFiClick = {
                        val url = "https://aprs.fi/info/a/${viewModel.targetCall}" +
                            "?utm_source=aprsdroid&utm_medium=inapp&utm_campaign=aprsfi"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    onQrzClick = {
                        val basecall = viewModel.targetCall.split("-", " ")[0]
                        val url = "https://qrz.com/db/$basecall"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                )
            }

            // SSIDs section
            if (ssids.isNotEmpty()) {
                item {
                    SectionHeader("SSIDs")
                }
                items(ssids) { ssid ->
                    SsidItem(ssid) { onSsidClick(ssid.call) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            // Recent packets section
            item {
                SectionHeader("Recent Packets")
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun StationInfoCard(
    station: StationEntity?,
    onMapClick: () -> Unit,
    onAprsFiClick: () -> Unit,
    onQrzClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (station == null) {
                Text("Loading station...", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Text(
                text = station.call,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (!station.comment.isNullOrEmpty()) {
                Text(
                    text = station.comment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!station.qrg.isNullOrEmpty()) {
                Text(
                    text = "QRG: ${station.qrg}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            // Action buttons
            Spacer(modifier = Modifier.padding(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onMapClick,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Map, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Map")
                }
                OutlinedButton(
                    onClick = onAprsFiClick,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Public, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("aprs.fi")
                }
                OutlinedButton(
                    onClick = onQrzClick,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("QRZ")
                }
            }
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
