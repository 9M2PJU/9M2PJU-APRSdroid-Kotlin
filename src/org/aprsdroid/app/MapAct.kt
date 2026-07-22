package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.aprsdroid.app.ui.PlaceholderScreen

/**
 * Kotlin/Compose skeleton for `MapAct`.
 *
 * The Scala version used MapsForge for offline maps. Per the project
 * preferences (see AGENTS.md) the Kotlin rewrite will use
 * OpenStreetMap-based tile rendering with offline tile support.
 *
 * The full map UI (tile layer, station overlay, position marker, menu
 * helpers) is pending migration; this stub lets the manifest reference
 * resolve and the build succeed in the meantime.
 */
class MapAct : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            PlaceholderScreen(getString(R.string.app_map))
        }
    }
}
