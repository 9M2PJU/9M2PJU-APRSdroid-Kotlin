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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.data.MessageEntity

/**
 * Compose screen for the conversations list.
 *
 * Shows the most recent message per callsign. Tapping a conversation
 * opens the message thread with that callsign.
 *
 * Replaces the Scala `ConversationsActivity` +
 * `ConversationListAdapter` + `SimpleCursorAdapter` stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    viewModel: ConversationsViewModel,
    onConversationClick: (String) -> Unit,
) {
    val conversations by viewModel.conversations.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Conversations") })
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator()
                    Text("No conversations yet")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(conversations) { message ->
                    ConversationItem(message, viewModel.colors) {
                        onConversationClick(message.call)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(message: MessageEntity, colors: IntArray, onClick: () -> Unit) {
    val color = if (message.type < colors.size) colors[message.type] else 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = message.call,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(color),
            )
            if (!message.text.isNullOrEmpty()) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Default,
                    ),
                    color = Color(color),
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
