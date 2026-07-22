package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.aprsdroid.app.ui.PlaceholderScreen

/**
 * Kotlin/Compose skeleton for `PrefSymbolAct`.
 *
 * Chooser for the APRS station symbol (table + symbol). The Scala version
 * used a custom `SymbolView`; the Compose port will use a grid of
 * symbol composables. Full UI pending migration.
 */
class PrefSymbolAct : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            PlaceholderScreen(getString(R.string.p_symbol))
        }
    }
}
