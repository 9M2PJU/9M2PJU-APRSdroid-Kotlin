package org.aprsdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Kotlin/Compose port of the Scala `PrefSymbolAct`.
 *
 * Symbol picker activity. The user taps a symbol from a grid,
 * optionally enters an overlay character, and taps OK to save.
 * The symbol grid cells use the existing [SymbolView] (an Android
 * View) wrapped via [AndroidView] since it does custom Canvas
 * drawing from the allicons sprite sheet.
 */
class PrefSymbolAct : ComponentActivity() {

    private val prefs by lazy { PrefsWrapper(this) }
    private val overlayable = "#&0>A^_acnsuvz"

    private fun overlayAllowed(symbol: String): Boolean =
        symbol[0] != '/' && overlayable.contains(symbol[1])

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)

        var initialSym = prefs.getString("symbol", "/$")
        if (initialSym.length != 2) initialSym = "/$"
        val initialOverlay = if (initialSym[0] != '/' && initialSym[0] != '\\') {
            initialSym[0].toString()
        } else ""

        setContent {
            SymbolPickerScreen(
                initialSymbol = initialSym,
                initialOverlay = initialOverlay,
                onOk = { sym ->
                    prefs.prefs.edit().putString("symbol", sym).commit()
                    finish()
                },
                onCancel = { finish() },
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SymbolPickerScreen(
        initialSymbol: String,
        initialOverlay: String,
        onOk: (String) -> Unit,
        onCancel: () -> Unit,
    ) {
        var chosenSym by remember { mutableStateOf(initialSymbol) }
        var overlayText by remember { mutableStateOf(initialOverlay) }

        fun setSymbol(symbol: String) {
            val ovEn = overlayAllowed(symbol)
            val ov = overlayText
            chosenSym = if (ovEn && ov.length == 1) {
                "${ov[0]}${symbol[1]}"
            } else {
                symbol
            }
        }

        // Generate all symbol identifiers: 12 pages × 16 columns = 192,
        // but the original adapter uses 16*12-2 = 190 items via
        // primary=position/95, secondary=position%95. Replicate that.
        val symbols = remember {
            (0 until (16 * 12 - 2)).map { pos ->
                val primary = pos / 95
                val secondary = pos % 95
                "${if (primary == 0) '/' else '\\'}${('!' + secondary).toChar()}"
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(getString(R.string.p_symbol)) },
                    navigationIcon = {
                        Button(onClick = onCancel) { Text("Cancel") }
                    },
                    actions = {
                        Button(onClick = { onOk(chosenSym) }) { Text("OK") }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // Preview + overlay editor
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Preview using SymbolView
                    AndroidView(
                        factory = { ctx ->
                            SymbolView(ctx, null).apply {
                                setSymbol(chosenSym)
                            }
                        },
                        update = { sv ->
                            if (chosenSym.length == 2) sv.setSymbol(chosenSym)
                            else sv.setSymbol("/$")
                        },
                        modifier = Modifier.size(64.dp),
                    )
                    OutlinedTextField(
                        value = overlayText,
                        onValueChange = { new ->
                            if (new.length <= 1) {
                                overlayText = new
                                if (chosenSym.length == 2) {
                                    setSymbol("\\${chosenSym[1]}")
                                }
                            }
                        },
                        label = { Text("Overlay") },
                        singleLine = true,
                        enabled = overlayAllowed(chosenSym),
                        modifier = Modifier.weight(1f),
                    )
                }

                // Symbol grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(symbols) { sym ->
                        SymbolCell(
                            symbol = sym,
                            isSelected = sym == chosenSym,
                            onClick = { setSymbol(sym) },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SymbolCell(
        symbol: String,
        isSelected: Boolean,
        onClick: () -> Unit,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent,
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx ->
                    SymbolView(ctx, null).apply {
                        setSymbol(symbol)
                    }
                },
                update = { sv -> sv.setSymbol(symbol) },
                modifier = Modifier.size(48.dp),
            )
        }
    }
}
