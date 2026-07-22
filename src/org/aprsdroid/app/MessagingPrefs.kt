package org.aprsdroid.app

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceActivity

/**
 * Kotlin port of the Scala `MessagingPrefs`.
 *
 * Uses the XML preference framework (res/xml/messaging.xml). Reloads
 * the preference screen when relevant keys change so that dependent
 * preferences are shown/hidden correctly.
 */
class MessagingPrefs : PreferenceActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val prefs by lazy { PrefsWrapper(this) }

    private fun loadXml() {
        addPreferencesFromResource(R.xml.messaging)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        loadXml()
        preferenceScreen.sharedPreferences
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceScreen.sharedPreferences
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        when (key) {
            "p.messaging", "p.retry", "p.ackdupetoggle", "p.ackdupe",
            "p.msgdupetoggle", "p.msgdupetime" -> {
                preferenceScreen = null
                loadXml()
            }
        }
    }
}
