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
import org.aprsdroid.app.R
import org.aprsdroid.app.data.MessageEntity

/**
 * Compose screen for a single message conversation (message thread
 * with one callsign).
 *
 * Replaces the Scala `MessageActivity` + `MessageListAdapter` +
 * `SimpleCursorAdapter` stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageListScreen(
    viewModel: MessageListViewModel,
    myCall: String,
    onBack: () -> Unit,
) {
    val messages by viewModel.messages.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.targetCall) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (messages.isEmpty()) {
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
                    Text("No messages yet")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(messages) { message ->
                    MessageItem(message, viewModel.colors, myCall, viewModel.targetCall,
                        viewModel.numOfRetries)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun MessageItem(
    message: MessageEntity,
    colors: IntArray,
    myCall: String,
    targetCall: String,
    numOfRetries: Int,
) {
    val color = if (message.type < colors.size) colors[message.type] else 0
    val status = when (message.type) {
        MessageEntity.TYPE_INCOMING -> targetCall
        MessageEntity.TYPE_OUT_NEW -> "$myCall ${message.retrycnt}/$numOfRetries"
        MessageEntity.TYPE_OUT_ACKED -> myCall
        MessageEntity.TYPE_OUT_REJECTED -> "$myCall rejected"
        MessageEntity.TYPE_OUT_ABORTED -> "$myCall aborted"
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = status,
            modifier = Modifier.weight(0.3f),
            style = MaterialTheme.typography.bodySmall,
            color = Color(color),
        )
        Text(
            text = message.text ?: "",
            modifier = Modifier.weight(0.7f),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Default,
            ),
            color = Color(color),
        )
    }
}
