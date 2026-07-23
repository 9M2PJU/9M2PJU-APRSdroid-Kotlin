package org.aprsdroid.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.aprsdroid.app.MapModes
import org.aprsdroid.app.MessageActivity
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.StationActivity
import org.aprsdroid.app.UIHelper
import org.aprsdroid.app.data.AprsDatabase
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Callsign context menu — shown on long-press of a callsign.
 *
 * Provides: Details, Message, Clear Messages, Map, External Map,
 * aprs.fi, QRZ.com, Export Log.
 */
@Composable
fun CallsignContextMenu(
    call: String,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text("Details") },
            onClick = {
                onDismiss()
                val intent = Intent(context, StationActivity::class.java)
                intent.putExtra("call", call)
                context.startActivity(intent)
            },
        )
        DropdownMenuItem(
            text = { Text("Message") },
            onClick = {
                onDismiss()
                val intent = Intent(context, MessageActivity::class.java)
                intent.putExtra("call", call)
                context.startActivity(intent)
            },
        )
        DropdownMenuItem(
            text = { Text("Clear Messages") },
            onClick = {
                onDismiss()
                clearMessagesForCall(context, call)
            },
        )
        DropdownMenuItem(
            text = { Text("Track on Map") },
            onClick = {
                onDismiss()
                MapModes.startMap(context, PrefsWrapper(context), call)
            },
        )
        DropdownMenuItem(
            text = { Text("External Map") },
            onClick = {
                onDismiss()
                openExternalMap(context, call)
            },
        )
        DropdownMenuItem(
            text = { Text("aprs.fi") },
            onClick = {
                onDismiss()
                val url = "https://aprs.fi/info/a/$call?utm_source=aprsdroid&utm_medium=inapp&utm_campaign=aprsfi"
                openUrl(context, url)
            },
        )
        DropdownMenuItem(
            text = { Text("QRZ.com") },
            onClick = {
                onDismiss()
                val basecall = call.split("-", " ")[0]
                val url = "https://qrz.com/db/$basecall"
                openUrl(context, url)
            },
        )
        DropdownMenuItem(
            text = { Text("Export Station Log") },
            onClick = {
                onDismiss()
                exportStationLog(context, call)
            },
        )
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show()
    }
}

private fun openExternalMap(context: android.content.Context, call: String) {
    GlobalScope.launch(Dispatchers.IO) {
        try {
            val db = AprsDatabase.get(context)
            val pos = db.stationDao().getStaPosition(call)
            if (pos != null) {
                val lat = pos.lat / 1000000.0
                val lon = pos.lon / 1000000.0
                val url = "geo:$lat,$lon?q=$lat,$lon($call)"
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)), call))
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No position for $call", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun clearMessagesForCall(context: android.content.Context, call: String) {
    GlobalScope.launch(Dispatchers.IO) {
        try {
            val db = AprsDatabase.get(context)
            db.messageDao().deleteMessages(call)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Messages cleared for $call", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun exportStationLog(context: android.content.Context, call: String) {
    GlobalScope.launch(Dispatchers.IO) {
        try {
            val db = AprsDatabase.get(context)
            val basecall = call.split("-", " ")[0]
            val posts = db.postDao().getStaPostsList("$basecall%", "%;$basecall%", "%)$basecall%")
            if (posts.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Nothing to export", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val dir = UIHelper.getExportDirectory(context)
            dir.mkdirs()
            val sdf = SimpleDateFormat("yyyyMMdd-HHmm")
            val file = File(dir, "aprsdroid-${basecall}-${sdf.format(Date())}.log")
            PrintWriter(file).use { writer ->
                for (post in posts) {
                    val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(post.ts))
                    writer.println("$ts\t${post.message ?: ""}")
                }
            }
            withContext(Dispatchers.Main) {
                UIHelper.shareFile(context, file, file.name)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
