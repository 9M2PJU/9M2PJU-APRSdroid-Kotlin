package org.aprsdroid.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.aprsdroid.app.NavTarget

/**
 * Shared bottom navigation bar shown on all main screens.
 *
 * Provides navigation between Hub, Map, Messages, and Log, plus a
 * Settings FAB for opening Preferences.
 */
@Composable
fun AprsBottomBar(
    current: NavTarget,
    onNavigate: (NavTarget) -> Unit,
    onPreferences: () -> Unit,
) {
    BottomAppBar(
        actions = {
            IconButton(onClick = { onNavigate(NavTarget.HUB) }) {
                Icon(
                    Icons.Filled.Hub,
                    contentDescription = "Hub",
                    tint = if (current == NavTarget.HUB)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onNavigate(NavTarget.MAP) }) {
                Icon(
                    Icons.Filled.Map,
                    contentDescription = "Map",
                    tint = if (current == NavTarget.MAP)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onNavigate(NavTarget.MESSAGES) }) {
                Icon(
                    Icons.Filled.Message,
                    contentDescription = "Messages",
                    tint = if (current == NavTarget.MESSAGES)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onNavigate(NavTarget.LOG) }) {
                Icon(
                    Icons.Filled.Subject,
                    contentDescription = "Log",
                    tint = if (current == NavTarget.LOG)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onPreferences) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        },
    )
}
