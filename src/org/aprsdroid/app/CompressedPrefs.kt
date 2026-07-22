package org.aprsdroid.app

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.ListPreference
import android.preference.PreferenceActivity
import android.util.Log

/**
 * Kotlin port of the Scala `CompressedPrefs`.
 *
 * Uses the XML preference framework (res/xml/compressed.xml). Mutually
 * disables "compressed_location" and "compressed_mice" checkboxes,
 * and disables "p__location_mice_status" when "compressed_location"
 * is checked.
 */
class CompressedPrefs : PreferenceActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val prefs by lazy { PrefsWrapper(this) }

    private fun loadXml() {
        addPreferencesFromResource(R.xml.compressed)
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
            "compressed_location", "compressed_mice" -> updateCheckBoxState()
            "p__location_mice_status" -> updateStatus()
        }
    }

    private fun updateCheckBoxState() {
        val compressedLocationPref = findPreference("compressed_location") as? CheckBoxPreference ?: return
        val compressedMicePref = findPreference("compressed_mice") as? CheckBoxPreference ?: return
        val locationMiceStatusPref = findPreference("p__location_mice_status") as? ListPreference ?: return

        if (compressedLocationPref.isChecked) {
            locationMiceStatusPref.isEnabled = false
            compressedMicePref.isEnabled = false
        } else {
            locationMiceStatusPref.isEnabled = true
            compressedMicePref.isEnabled = true
        }

        compressedLocationPref.isEnabled = !compressedMicePref.isChecked
    }

    private fun updateStatus() {
        val statusPref = findPreference("p__location_mice_status") as? ListPreference ?: return
        Log.d("CompressedPrefs", "Selected Location Mice Status: ${statusPref.value}")
    }
}
