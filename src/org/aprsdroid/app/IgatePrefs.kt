package org.aprsdroid.app

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.PreferenceActivity

/**
 * Kotlin port of the Scala `IgatePrefs`.
 *
 * Uses the XML preference framework (res/xml/igate.xml). Disables
 * the "p.igating" checkbox while the APRS service is running.
 */
class IgatePrefs : PreferenceActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val prefs by lazy { PrefsWrapper(this) }

    private fun loadXml() {
        addPreferencesFromResource(R.xml.igate)
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
            "p.igating" -> updateCheckBoxState()
        }
    }

    private fun updateCheckBoxState() {
        val igatingPref = findPreference("p.igating") as? CheckBoxPreference ?: return
        val isServiceRunning = prefs.getBoolean("service_running", false)

        if (isServiceRunning) {
            igatingPref.isEnabled = false
            igatingPref.summary = "Setting disabled while the service is running."
        } else {
            igatingPref.isEnabled = true
        }
    }
}
