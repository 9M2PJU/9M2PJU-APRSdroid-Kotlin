package org.aprsdroid.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.ImageView

/**
 * Kotlin port of the Scala `SymbolView` / `SymbolView` companion.
 *
 * A custom ImageView that renders an APRS symbol from the `allicons`
 * sprite sheet. The sprite sheet is a 16x16 grid of symbols, with
 * page 0 (primary table, '/' prefix), page 1 (alternate table, '\\'
 * prefix), and page 2 (overlay letters).
 *
 * In the Compose rewrite this will eventually become a Compose
 * composable, but the View form is kept for now since it is used by
 * the XML-based preference screens (e.g. PrefSymbolAct).
 */
class SymbolView(context: Context, attrs: AttributeSet?) : ImageView(context, attrs) {

    private var symbol: String = "/$"
    private val iconbitmap: Bitmap = Companion.getSingleton(context)
    private val symbolSize: Int = iconbitmap.width / 16

    fun setSymbol(newSym: String) {
        symbol = newSym
        invalidate()
    }

    private fun symbol2rect(index: Int, page: Int): Rect {
        val altOffset = page * symbolSize * 6
        val y = (index / 16) * symbolSize + altOffset
        val x = (index % 16) * symbolSize
        return Rect(x, y, x + symbolSize, y + symbolSize)
    }

    private fun symbol2rect(symbol: String): Rect {
        return symbol2rect(symbol[1].code - 33, if (symbol[0] == '/') 0 else 1)
    }

    private fun symbolIsOverlayed(symbol: String): Boolean {
        return symbol[0] != '/' && symbol[0] != '\\'
    }

    override fun onDraw(canvas: Canvas) {
        val srcRect = symbol2rect(symbol)
        val destRect = Rect(0, 0, width, height)
        val drawPaint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }

        canvas.drawBitmap(iconbitmap, srcRect, destRect, drawPaint)

        if (symbolIsOverlayed(symbol)) {
            // use page 2, overlay letters
            canvas.drawBitmap(iconbitmap, symbol2rect(symbol[0].code - 33, 2), destRect, drawPaint)
        }
    }

    companion object {
        @JvmStatic
        @Volatile
        private var iconbitmap: Bitmap? = null

        @JvmStatic
        fun getSingleton(context: Context): Bitmap {
            return iconbitmap ?: synchronized(this) {
                iconbitmap ?: BitmapFactory.decodeResource(
                    context.resources, R.drawable.allicons
                ).also { iconbitmap = it }
            }
        }
    }
}
