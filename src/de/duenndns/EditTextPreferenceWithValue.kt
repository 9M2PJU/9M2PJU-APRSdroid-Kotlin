package de.duenndns

import android.content.Context
import android.preference.EditTextPreference
import android.text.InputFilter
import android.text.InputType
import android.util.AttributeSet
import android.view.View
import android.widget.EditText

/**
 * Kotlin port of the Java `EditTextPreferenceWithValue`.
 *
 * An EditTextPreference that shows the current text in the summary
 * line and adds an AllCaps filter when the input type is
 * TYPE_TEXT_FLAG_CAP_CHARACTERS. Used by the XML preference screens.
 */
class EditTextPreferenceWithValue : EditTextPreference {

    private var mSummary: CharSequence? = null

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : super(context)

    private fun fixupCaps() {
        val et: EditText = editText
        if (et.inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0) {
            // append AllCaps filter
            val filters = et.filters
            val newFilters = filters + InputFilter.AllCaps()
            et.filters = newFilters
        }
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        fixupCaps()
    }

    private fun setSummaryToText(text: String?) {
        if (mSummary == null) mSummary = summary
        if (text.isNullOrEmpty()) {
            summary = mSummary
        } else {
            summary = "$mSummary: $text"
        }
    }

    override fun onBindView(view: View) {
        super.onBindView(view)
        setSummaryToText(text)
    }

    override fun setText(text: String?) {
        super.setText(text)
        setSummaryToText(text)
    }
}
