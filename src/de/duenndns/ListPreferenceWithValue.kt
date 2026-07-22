package de.duenndns

import android.content.Context
import android.preference.ListPreference
import android.util.AttributeSet
import android.view.View

/**
 * Kotlin port of the Java `ListPreferenceWithValue`.
 *
 * A ListPreference that shows the current entry (not just the value)
 * in the summary line. Used by the XML preference screens.
 */
class ListPreferenceWithValue : ListPreference {

    private var mSummary: CharSequence? = null

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : super(context)

    private fun setSummaryToText(text: CharSequence?) {
        if (mSummary == null) mSummary = summary
        if (text.isNullOrEmpty()) {
            summary = mSummary
        } else {
            summary = text
        }
    }

    override fun onBindView(view: View) {
        super.onBindView(view)
        setSummaryToText(entry)
    }

    override fun setValue(text: String?) {
        super.setValue(text)
        setSummaryToText(entry)
    }
}
