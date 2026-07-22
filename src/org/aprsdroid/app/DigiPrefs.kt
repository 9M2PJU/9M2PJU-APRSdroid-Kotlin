package org.aprsdroid.app

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.PreferenceActivity

/**
 * Kotlin port of the Scala `DigiPrefs`.
 *
 * Uses the XML preference framework (res/xml/digi.xml). Mutually
 * disables the "p.digipeating" and "p.regenerate" checkboxes so
 * only one can be active at a time.
 */
class DigiPrefs : PreferenceActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val prefs by lazy { PrefsWrapper(this) }

    private fun loadXml() {
        addPreferencesFromResource(R.xml.digi)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        loadXml()
        preferenceScreen.sharedPreferences
            .registerOnSharedPreferenceChangeListener(this)
        updateCheckBoxState()
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceScreen.sharedPreferences
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        when (key) {
            "p.digipeating", "p.regenerate" -> updateCheckBoxState()
        }
    }

    private fun updateCheckBoxState() {
        val digipeatingPref = findPreference("p.digipeating") as? CheckBoxPreference ?: return
        val regeneratePref = findPreference("p.regenerate") as? CheckBoxPreference ?: return

        // If "p.digipeating" is checked, disable "p.regenerate"
        regeneratePref.isEnabled = !digipeatingPref.isChecked

        // If "p.regenerate" is checked, disable "p.digipeating"
        digipeatingPref.isEnabled = !regeneratePref.isChecked
    }
}
