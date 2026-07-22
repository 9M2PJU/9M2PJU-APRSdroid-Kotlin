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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.data.PostEntity

/**
 * Compose screen for the packet log / activity feed.
 *
 * Replaces the Scala `LogActivity` + `PostListAdapter` +
 * `SimpleCursorAdapter` stack with a single Compose `LazyColumn`
 * backed by [PostListViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostListScreen(viewModel: PostListViewModel) {
    val posts by viewModel.posts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("APRS Log") })
        },
    ) { padding ->
        if (posts.isEmpty()) {
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
                    Text("Loading packets...")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(posts) { post ->
                    PostItem(post, viewModel.colors)
                }
            }
        }
    }
}

@Composable
private fun PostItem(post: PostEntity, colors: IntArray) {
    val color = if (post.type < colors.size) colors[post.type] else 0
    val isMonospace = post.type == PostEntity.TYPE_POST ||
        post.type == PostEntity.TYPE_INCMG ||
        post.type == PostEntity.TYPE_TX ||
        post.type == PostEntity.TYPE_DIGI ||
        post.type == PostEntity.TYPE_IG

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
