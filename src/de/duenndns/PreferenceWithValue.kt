package de.duenndns

import android.content.Context
import android.preference.Preference
import android.util.AttributeSet
import android.view.View

/**
 * Kotlin port of the Java `PreferenceWithValue`.
 *
 * A Preference that shows the current value in the summary line.
 * Used by the XML preference screens.
 */
class PreferenceWithValue : Preference {

    private var mSummary: CharSequence? = null

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : super(context)

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
        setSummaryToText(sharedPreferences?.getString(key, ""))
    }
}
