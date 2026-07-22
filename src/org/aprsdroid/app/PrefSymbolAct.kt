package org.aprsdroid.app

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.util.Log

/**
 * Kotlin port of the Scala `PrefSymbolAct`.
 *
 * Symbol picker activity using a GridView of SymbolView cells. The
 * user taps a symbol, optionally enters an overlay character, and
 * taps OK to save. Uses the XML layout res/layout/prefsymbol.xml.
 */
class PrefSymbolAct : Activity(), TextWatcher, View.OnClickListener {

    private val overlayedit by lazy { findViewById<EditText>(R.id.overlay) }
    private val symbolview by lazy { findViewById<SymbolView>(R.id.symbol) }
    private val okbutton by lazy { findViewById<Button>(R.id.ok) }
    private val prefs by lazy { PrefsWrapper(this) }

    private var chosenSym = ""

    private val overlayable = "#&0>A^_acnsuvz"

    private fun overlayAllowed(symbol: String): Boolean {
        return symbol[0] != '/' && overlayable.contains(symbol[1])
    }

    private fun setSymbol(symbol: String) {
        val ovEn = overlayAllowed(symbol)
        overlayedit.isEnabled = ovEn

        val ov = overlayedit.text.toString()
        chosenSym = if (ovEn && ov.length == 1) {
            "${ov[0]}${symbol[1]}"
        } else {
            symbol
        }
        if (chosenSym.length == 2) {
            symbolview.setSymbol(chosenSym)
        } else {
            symbolview.setSymbol("/$")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContentView(R.layout.prefsymbol)

        val gv = findViewById<GridView>(R.id.gridview)
        gv.adapter = SymbolAdapter(this)
        gv.onItemClickListener = AdapterView.OnItemClickListener { _, v, _, _ ->
            Log.d("PrefSymbolAct", "tapped ${(v as SymbolView).symbol}")
            setSymbol(v.symbol)
        }

        okbutton.setOnClickListener(this)
        chosenSym = prefs.getString("symbol", "/$")
        if (chosenSym.length != 2) chosenSym = "/$"
        val ov = chosenSym[0]
        if (ov != '/' && ov != '\\') {
            overlayedit.setText(ov.toString())
        }
        overlayedit.addTextChangedListener(this)
        setSymbol(chosenSym)
    }

    // OnClickListener for OK button
    override fun onClick(view: View) {
        prefs.prefs.edit().putString("symbol", chosenSym).commit()
        finish()
    }

    // TextWatcher for overlay edit
    override fun afterTextChanged(s: Editable?) {
        if (chosenSym.length == 2) {
            setSymbol("\\${chosenSym[1]}")
        }
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    private inner class SymbolAdapter(context: Context) : BaseAdapter() {

        override fun getCount(): Int = 16 * 12 - 2

        override fun getItem(position: Int): String {
            val primary = position / 95
            val secondary = position % 95
            return "${if (primary == 0) '/' else '\\'}${('!' + secondary).toChar()}"
        }

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = if (convertView == null) {
                SymbolView(this@PrefSymbolAct, null).apply {
                    val px = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 48f,
                        resources.displayMetrics,
                    ).toInt()
                    layoutParams = AbsListView.LayoutParams(px, px)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
            } else {
                convertView as SymbolView
            }
            v.setSymbol(getItem(position))
            return v
        }
    }
}
