package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.aprsdroid.app.ui.PlaceholderScreen

/**
 * Kotlin/Compose skeleton for `BackendPrefs`.
 *
 * Connection / backend preferences. The Scala version dynamically
 * loaded `res/xml/backend.xml` plus a protocol-specific XML based on the
 * selected backend. The Compose port will render these as a form.
 */
class BackendPrefs : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            PlaceholderScreen(getString(R.string.p__connection))
        }
    }
}
