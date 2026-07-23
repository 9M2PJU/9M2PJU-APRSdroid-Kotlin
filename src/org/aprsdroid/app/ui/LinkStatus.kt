package org.aprsdroid.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.AprsService

/**
 * Tracks the APRS service link status via broadcast intents.
 *
 * Returns the current link error code (0 = connected, nonzero = error).
 */
@Composable
fun rememberLinkStatus(): Int {
    val context = LocalContext.current
    var linkError by remember { mutableIntStateOf(AprsService.linkError) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    AprsService.LINK_ON -> linkError = 0
                    AprsService.LINK_OFF -> {
                        linkError = intent.getIntExtra("error", 1)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(AprsService.LINK_ON)
            addAction(AprsService.LINK_OFF)
        }
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    return linkError
}

/**
 * A thin status bar shown below the top app bar when the link is down.
 * Shows nothing when connected (linkError == 0).
 */
@Composable
fun LinkStatusBar(linkError: Int) {
    if (linkError == 0) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFB71C1C))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = "Connection error (code $linkError)",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}
