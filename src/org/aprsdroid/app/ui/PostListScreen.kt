package org.aprsdroid.app.ui

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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.NavTarget
import org.aprsdroid.app.data.PostEntity

/**
 * Compose screen for the packet log / activity feed.
 *
 * Includes Start/Stop service buttons, single-shot, overflow menu,
 * link status bar, and click-to-open station details.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PostListScreen(
    viewModel: PostListViewModel,
    isServiceRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onSingleShot: () -> Unit,
    onNavigate: (NavTarget) -> Unit,
    onPreferences: () -> Unit,
) {
    val posts by viewModel.posts.collectAsState()
    val context = LocalContext.current
    val linkError = rememberLinkStatus()
    var contextMenuCall by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("APRS Log") },
                actions = {
                    // Start/Stop button
                    IconButton(onClick = {
                        if (isServiceRunning) onStopService() else onStartService()
                    }) {
                        Icon(
                            if (isServiceRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = if (isServiceRunning) "Stop" else "Start",
                            tint = if (isServiceRunning) Color(0xFFD32F2F)
                                   else Color(0xFF388E3C),
                        )
                    }
                    // Single-shot button
                    IconButton(onClick = onSingleShot) {
                        Icon(Icons.Filled.Subject, contentDescription = "Single")
                    }
                    // Overflow menu
                    AprsOverflowMenu(
                        onPreferences = onPreferences,
                    )
                },
            )
        },
        bottomBar = {
            AprsBottomBar(
                current = NavTarget.LOG,
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
            // Link status bar
            LinkStatusBar(linkError)

            if (posts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "No packets yet.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            if (isServiceRunning) "Listening for packets..."
                            else "Press Play to start the APRS service.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(posts) { post ->
                        PostItem(
                            post = post,
                            colors = viewModel.colors,
                            onLongClick = {
                                // Extract callsign from message
                                val call = extractCall(post.message)
                                if (call != null) contextMenuCall = call
                            },
                        )
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
private fun PostItem(
    post: PostEntity,
    colors: IntArray,
    onLongClick: () -> Unit,
) {
    val color = if (post.type < colors.size) colors[post.type] else 0
    val isMonospace = post.type == PostEntity.TYPE_POST ||
        post.type == PostEntity.TYPE_INCMG ||
        post.type == PostEntity.TYPE_TX ||
        post.type == PostEntity.TYPE_DIGI ||
        post.type == PostEntity.TYPE_IG

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onLongClick = onLongClick) {}
            .padding(horizontal = 8.dp, vertical = 4.dp),
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

/**
 * Extract a callsign from an APRS packet message.
 * Format: "CALL>path:data" or "CALL-SSID>path:data"
 */
private fun extractCall(message: String?): String? {
    if (message.isNullOrEmpty()) return null
    val gt = message.indexOf('>')
    if (gt <= 0) return null
    return message.substring(0, gt)
}
