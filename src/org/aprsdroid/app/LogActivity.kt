package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import org.aprsdroid.app.ui.PostListScreen
import org.aprsdroid.app.ui.PostListViewModel
import org.aprsdroid.app.ui.theme.AprsTheme

/**
 * Kotlin/Compose implementation of `LogActivity`.
 *
 * Shows the packet log / activity feed. Uses [PostListViewModel]
 * backed by Room to reactively display packets as they arrive.
 */
class LogActivity : ComponentActivity() {

    private val viewModel: PostListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            AprsTheme {
                PostListScreen(viewModel)
            }
        }
    }
}
