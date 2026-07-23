package org.aprsdroid.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.aprsdroid.app.AprsService
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.R
import org.aprsdroid.app.data.AprsDatabase
import org.aprsdroid.app.data.MessageEntity

/**
 * Compose screen for a single message conversation (message thread
 * with one callsign). Includes message input field, send button,
 * context menu (copy, abort, resend), and start-tracking dialog.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MessageListScreen(
    viewModel: MessageListViewModel,
    myCall: String,
    onBack: () -> Unit,
    onStartService: () -> Unit,
) {
    val messages by viewModel.messages.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var contextMenuFor by remember { mutableStateOf<MessageEntity?>(null) }
    var showStartTracking by remember { mutableStateOf(false) }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

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
        bottomBar = {
            MessageInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        sendMessage(context, viewModel.targetCall, inputText)
                        inputText = ""
                        if (!AprsService.running) {
                            showStartTracking = true
                        }
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
                    Text(
                        "No messages yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Type a message below to start the conversation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(messages) { message ->
                    MessageBubble(
                        message = message,
                        myCall = myCall,
                        targetCall = viewModel.targetCall,
                        numOfRetries = viewModel.numOfRetries,
                        onLongClick = { contextMenuFor = message },
                    )
                }
            }
        }
    }

    // Context menu for messages
    contextMenuFor?.let { msg ->
        MessageContextMenu(
            message = msg,
            onDismiss = { contextMenuFor = null },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("APRS message", msg.text ?: ""))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            },
            onAbort = {
                abortMessage(context, msg)
            },
            onResend = {
                resendMessage(context, msg)
            },
        )
    }

    // Start tracking dialog
    if (showStartTracking) {
        AlertDialog(
            onDismissRequest = { showStartTracking = false },
            title = { Text("Tracking is not started") },
            text = {
                Text("Your message has been saved, but APRSdroid is not connected. " +
                     "Start tracking now to send it?")
            },
            confirmButton = {
                TextButton(onClick = {
                    showStartTracking = false
                    onStartService()
                }) { Text("Start Tracking") }
            },
            dismissButton = {
                TextButton(onClick = { showStartTracking = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageEntity,
    myCall: String,
    targetCall: String,
    numOfRetries: Int,
    onLongClick: () -> Unit,
) {
    val isOutgoing = message.type != MessageEntity.TYPE_INCOMING
    val alignment = if (isOutgoing) Alignment.End else Alignment.Start
    val bubbleColor = when (message.type) {
        MessageEntity.TYPE_INCOMING -> MaterialTheme.colorScheme.surfaceVariant
        MessageEntity.TYPE_OUT_NEW -> MaterialTheme.colorScheme.primaryContainer
        MessageEntity.TYPE_OUT_ACKED -> MaterialTheme.colorScheme.primary
        MessageEntity.TYPE_OUT_REJECTED -> MaterialTheme.colorScheme.errorContainer
        MessageEntity.TYPE_OUT_ABORTED -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when (message.type) {
        MessageEntity.TYPE_OUT_ACKED -> MaterialTheme.colorScheme.onPrimary
        MessageEntity.TYPE_OUT_NEW -> MaterialTheme.colorScheme.onPrimaryContainer
        MessageEntity.TYPE_INCOMING -> MaterialTheme.colorScheme.onSurfaceVariant
        MessageEntity.TYPE_OUT_REJECTED -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = alignment,
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isOutgoing) 16.dp else 4.dp,
                bottomEnd = if (isOutgoing) 4.dp else 16.dp,
            ),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onLongClick = onLongClick) {},
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = message.text ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                )
                // Status line
                val statusText = when (message.type) {
                    MessageEntity.TYPE_OUT_NEW -> "sending ${message.retrycnt}/$numOfRetries"
                    MessageEntity.TYPE_OUT_ACKED -> "delivered"
                    MessageEntity.TYPE_OUT_REJECTED -> "rejected"
                    MessageEntity.TYPE_OUT_ABORTED -> "aborted"
                    else -> ""
                }
                if (statusText.isNotEmpty()) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                maxLines = 3,
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (text.isNotBlank())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@Composable
private fun MessageContextMenu(
    message: MessageEntity,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onAbort: () -> Unit,
    onResend: () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false; onDismiss() },
    ) {
        DropdownMenuItem(
            text = { Text("Copy") },
            onClick = { expanded = false; onDismiss(); onCopy() },
        )
        if (message.type == MessageEntity.TYPE_OUT_NEW) {
            DropdownMenuItem(
                text = { Text("Abort") },
                onClick = { expanded = false; onDismiss(); onAbort() },
            )
        }
        if (message.type != MessageEntity.TYPE_INCOMING) {
            DropdownMenuItem(
                text = { Text("Resend") },
                onClick = { expanded = false; onDismiss(); onResend() },
            )
        }
    }
}

// --- Message operations ---

private fun sendMessage(context: Context, targetCall: String, text: String) {
    val scope = kotlinx.coroutines.MainScope()
    scope.launch(Dispatchers.IO) {
        try {
            val db = AprsDatabase.get(context)
            val msgId = db.messageDao().createMsgId(targetCall)
            db.messageDao().insert(MessageEntity(
                ts = System.currentTimeMillis(),
                retrycnt = 0,
                call = targetCall,
                msgid = msgId.toString(),
                type = MessageEntity.TYPE_OUT_NEW,
                text = text,
            ))
            // Notify service to transmit
            val intent = Intent(AprsService.MESSAGETX)
                .putExtra(AprsService.SOURCE, PrefsWrapper(context).getCallSsid())
                .putExtra(AprsService.DEST, targetCall)
                .putExtra(AprsService.BODY, text)
            context.sendBroadcast(intent)
            // Notify UI
            context.sendBroadcast(Intent(AprsService.MESSAGE).setPackage("org.aprsdroid.app"))
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun abortMessage(context: Context, message: MessageEntity) {
    val scope = kotlinx.coroutines.MainScope()
    scope.launch(Dispatchers.IO) {
        try {
            val db = AprsDatabase.get(context)
            db.messageDao().updateMessageType(message.id, MessageEntity.TYPE_OUT_ABORTED)
            context.sendBroadcast(Intent(AprsService.MESSAGE).setPackage("org.aprsdroid.app"))
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun resendMessage(context: Context, message: MessageEntity) {
    val scope = kotlinx.coroutines.MainScope()
    scope.launch(Dispatchers.IO) {
        try {
            val db = AprsDatabase.get(context)
            db.messageDao().updateMessageRetry(message.id, 0, System.currentTimeMillis())
            db.messageDao().updateMessageType(message.id, MessageEntity.TYPE_OUT_NEW)
            // Notify service to retry
            context.sendBroadcast(Intent(AprsService.MESSAGETX).setPackage("org.aprsdroid.app"))
            context.sendBroadcast(Intent(AprsService.MESSAGE).setPackage("org.aprsdroid.app"))
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
