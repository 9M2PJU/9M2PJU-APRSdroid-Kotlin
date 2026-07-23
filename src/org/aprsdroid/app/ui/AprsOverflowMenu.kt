package org.aprsdroid.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.aprsdroid.app.AprsService
import org.aprsdroid.app.PrefsWrapper
import org.aprsdroid.app.UIHelper
import org.aprsdroid.app.UpdateChecker
import org.aprsdroid.app.data.AprsDatabase
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Shared overflow menu for all main screens.
 *
 * Provides: Preferences, Export Log, Clear Log, Clear Messages,
 * Age Filter, Check Updates, About, Report Bug.
 */
@Composable
fun AprsOverflowMenu(
    showAgeFilter: Boolean = false,
    onPreferences: () -> Unit,
    onAgeChanged: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showAgeDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showMenu = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
    }

    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
    ) {
        DropdownMenuItem(
            text = { Text("Preferences") },
            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            onClick = {
                showMenu = false
                onPreferences()
            },
        )
        if (showAgeFilter && onAgeChanged != null) {
            DropdownMenuItem(
                text = { Text("Show last...") },
                leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                onClick = {
                    showMenu = false
                    showAgeDialog = true
                },
            )
        }
        DropdownMenuItem(
            text = { Text("Export Log") },
            leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
            onClick = {
                showMenu = false
                exportLog(context as Activity)
            },
        )
        DropdownMenuItem(
            text = { Text("Clear Log") },
            leadingIcon = { Icon(Icons.Filled.Clear, contentDescription = null) },
            onClick = {
                showMenu = false
                clearLog(context)
            },
        )
        DropdownMenuItem(
            text = { Text("Clear Messages") },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            onClick = {
                showMenu = false
                clearAllMessages(context)
            },
        )
        DropdownMenuItem(
            text = { Text("Check for Updates") },
            leadingIcon = { Icon(Icons.Filled.Update, contentDescription = null) },
            onClick = {
                showMenu = false
                UpdateChecker.forceCheckForUpdates(context as Activity)
            },
        )
        DropdownMenuItem(
            text = { Text("About") },
            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
            onClick = {
                showMenu = false
                showAbout = true
            },
        )
        DropdownMenuItem(
            text = { Text("Report Bug") },
            leadingIcon = { Icon(Icons.Filled.BugReport, contentDescription = null) },
            onClick = {
                showMenu = false
                reportBug(context as Activity)
            },
        )
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    if (showAgeDialog && onAgeChanged != null) {
        AgeDialog(
            onSelected = { minutes ->
                showAgeDialog = false
                PrefsWrapper(context).prefs.edit()
                    .putLong("show_age", minutes * 60 * 1000L)
                    .commit()
                onAgeChanged()
            },
            onDismiss = { showAgeDialog = false },
        )
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = try {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        "${pi.versionName} (${pi.longVersionCode})"
    } catch (_: Exception) { "unknown" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("APRSdroid 9M2PJU Mod") },
        text = {
            Text(
                "Version $version\n\n" +
                "APRSdroid is an Android application for the Automatic Packet " +
                "Reporting System (APRS).\n\n" +
                "9M2PJU Mod features:\n" +
                "• Material 3 / Jetpack Compose UI\n" +
                "• OSM / MapsForge offline maps\n" +
                "• In-app updates\n" +
                "• Bottom navigation\n\n" +
                "https://aprsdroid.hamradio.my/",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://aprsdroid.hamradio.my/"))
                context.startActivity(intent)
            }) { Text("Homepage") }
        },
    )
}

@Composable
private fun AgeDialog(
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val ages = listOf(
        5 to "5 minutes", 10 to "10 minutes", 15 to "15 minutes",
        30 to "30 minutes", 60 to "1 hour", 120 to "2 hours",
        240 to "4 hours", 720 to "12 hours", 1440 to "1 day", 2880 to "2 days",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Show stations from last...") },
        text = {
            Text("Select the time range for displaying stations:")
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
    // Simple selection via a second dropdown would be complex;
    // use a simple list dialog approach
}

private fun exportLog(activity: Activity) {
    GlobalScope.launch(Dispatchers.IO) {
        try {
            val db = AprsDatabase.get(activity)
            val posts = db.postDao().getExportPostsList()
            if (posts.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, "Nothing to export", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val dir = UIHelper.getExportDirectory(activity)
            dir.mkdirs()
            val sdf = SimpleDateFormat("yyyyMMdd-HHmm")
            val file = File(dir, "aprsdroid-${sdf.format(Date())}.log")
            PrintWriter(file).use { writer ->
                writer.println("# APRSdroid log export")
                for (post in posts) {
                    val typeStr = when (post.type) {
                        0, 3 -> "RX"
                        4 -> "TX"
                        5 -> "DP"
                        6 -> "IG"
                        else -> "??"
                    }
                    val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(post.ts))
                    writer.println("$ts\t$typeStr\t${post.message ?: ""}")
                }
            }
            withContext(Dispatchers.Main) {
                UIHelper.shareFile(activity, file, file.name)
                Toast.makeText(activity, "Exported ${posts.size} packets", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun clearLog(context: android.content.Context) {
    GlobalScope.launch(Dispatchers.IO) {
        try {
            val db = AprsDatabase.get(context)
            // Trim older than 2 days
            val cutoff = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L
            db.postDao().trimOlderThan(cutoff)
            db.positionDao().trimOlderThan(cutoff)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Log cleared (kept last 2 days)", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Clear failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun clearAllMessages(context: android.content.Context) {
    GlobalScope.launch(Dispatchers.IO) {
        try {
            val db = AprsDatabase.get(context)
            db.messageDao().deleteAll()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "All messages cleared", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Clear failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun reportBug(activity: Activity) {
    val version = try {
        val pi = activity.packageManager.getPackageInfo(activity.packageName, 0)
        pi.versionName ?: "unknown"
    } catch (_: Exception) { "unknown" }

    val body = "Please describe the bug:\n\n\n" +
        "App version: $version\n" +
        "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
        "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n"

    val intent = Intent(Intent.ACTION_SENDTO,
        Uri.parse("mailto:9m2pju@hamradio.my"))
        .putExtra(Intent.EXTRA_SUBJECT, "APRSdroid 9M2PJU Mod bug report")
        .putExtra(Intent.EXTRA_TEXT, body)
    try {
        activity.startActivity(Intent.createChooser(intent, "Report bug"))
    } catch (_: Exception) {
        Toast.makeText(activity, "No email app available", Toast.LENGTH_SHORT).show()
    }
}
