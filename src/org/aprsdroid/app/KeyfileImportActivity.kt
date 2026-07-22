package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.aprsdroid.app.ui.PlaceholderScreen

/**
 * Kotlin/Compose skeleton for `KeyfileImportActivity`.
 *
 * Handles `VIEW` intents for `.p12` SSL key files. The Scala version
 * prompted for a password, parsed the PKCS12 keystore, and stored the
 * key for APRS-IS SSL connections. The Compose port will show a
 * password dialog. Full logic pending migration.
 */
class KeyfileImportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            PlaceholderScreen(getString(R.string.ssl_import_activity))
        }
    }
}
