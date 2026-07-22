package org.aprsdroid.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.ui.theme.AprsTheme

/**
 * Placeholder screen used by activities that have been migrated from Scala
 * to a Kotlin/Compose skeleton but whose full UI has not yet been ported.
 *
 * Wraps the content in [AprsTheme] so the look-and-feel stays consistent
 * while the real Compose UI for each screen is being implemented.
 */
@Composable
fun PlaceholderScreen(label: String) {
    AprsTheme {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}
