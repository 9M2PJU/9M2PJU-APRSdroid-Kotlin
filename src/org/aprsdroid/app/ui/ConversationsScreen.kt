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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.NavTarget
import org.aprsdroid.app.data.MessageEntity

/**
 * Compose screen for the conversations list.
 *
 * Shows the most recent message per callsign. Tapping a conversation
 * opens the message thread. A FAB allows starting a new conversation.
 * Long-press shows a context menu (clear messages, details, etc.).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationsScreen(
    viewModel: ConversationsViewModel,
    onConversationClick: (String) -> Unit,
    onNavigate: (NavTarget) -> Unit = {},
    onPreferences: () -> Unit = {},
) {
    val conversations by viewModel.conversations.collectAsState()
    var showNewDialog by remember { mutableStateOf(false) }
    var contextMenuCall by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Conversations") })
        },
        bottomBar = {
            AprsBottomBar(
                current = NavTarget.MESSAGES,
                onNavigate = onNavigate,
                onPreferences = onPreferences,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New conversation")
            }
        },
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        "No conversations yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Tap + to start a new conversation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(conversations) { message ->
                    ConversationItem(
                        message = message,
                        colors = viewModel.colors,
                        onClick = { onConversationClick(message.call) },
                        onLongClick = { contextMenuCall = message.call },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    // New conversation dialog
    if (showNewDialog) {
        NewConversationDialog(
            onDismiss = { showNewDialog = false },
            onConfirm = { call, message ->
                showNewDialog = false
                onConversationClick(call.uppercase().trim())
                // If message is provided, it will be sent by MessageActivity
                // via intent extra "message"
            },
        )
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    message: MessageEntity,
    colors: IntArray,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val color = if (message.type < colors.size) colors[message.type] else 0
    val isUnread = message.type == MessageEntity.TYPE_INCOMING

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar circle with first letter
        Surface(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(40.dp),
            shape = CircleShape,
            color = if (isUnread) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = message.call.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isUnread) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = message.call,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                ),
                color = Color(color),
            )
            if (!message.text.isNullOrEmpty()) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Default,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        Text(
            text = android.text.format.DateUtils.getRelativeTimeSpanString(
                System.currentTimeMillis(),
                message.ts,
                android.text.format.DateUtils.MINUTE_IN_MILLIS,
            ).toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NewConversationDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var callText by remember { mutableStateOf("") }
    var msgText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send message to...") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = callText,
                    onValueChange = { callText = it.uppercase() },
                    label = { Text("Callsign") },
                    placeholder = { Text("e.g. W1ABC-2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = msgText,
                    onValueChange = { msgText = it },
                    label = { Text("Message (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(callText, msgText) },
                enabled = callText.isNotBlank(),
            ) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
