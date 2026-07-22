package org.aprsdroid.app

import android.Manifest
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.PreferenceActivity
import android.preference.Preference.OnPreferenceClickListener

/**
 * Kotlin port of the Scala `BackendPrefs`.
 *
 * Uses the XML preference framework. Loads the backend preference XML
 * (res/xml/backend.xml) plus the protocol-specific and backend-specific
 * XML based on the user's current selections. Handles passcode dialog
 * and GPS permission for Kenwood.
 */
class BackendPrefs : PreferenceActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val backendPermission = 1000
    private val requestGps = 1010

    private val prefs by lazy { PrefsWrapper(this) }
    private val permissionHelper by lazy {
        PermissionHelper(
            this,
            getActionName = { R.string.p_conn_kwd_gps },
            onAllGranted = { action ->
                if (action == requestGps) {
                    (findPreference("kenwood.gps") as? CheckBoxPreference)?.isChecked = true
                }
            },
            onFailedCancel = { /* nop */ },
        )
    }

    private fun loadXml() {
        addPreferencesFromResource(R.xml.backend)
        addPreferencesFromResource(AprsBackend.prefxmlProto(prefs))
        val additionalXml = AprsBackend.prefxmlBackend(prefs)
        if (additionalXml != 0) {
            addPreferencesFromResource(additionalXml)
            hookPasscode()
            hookGpsPermission()
        }
        val perms = AprsBackend.defaultBackendPermissions(prefs)
        if (perms.isNotEmpty()) {
            permissionHelper.checkPermissions(perms.toTypedArray(), backendPermission)
        }
    }

    private fun hookPasscode() {
        val p = findPreference("passcode") ?: return
        p.onPreferenceClickListener = OnPreferenceClickListener {
            PasscodeDialog(this@BackendPrefs, false).show()
            true
        }
    }

    private fun hookGpsPermission() {
        val p = findPreference("kenwood.gps") ?: return
        p.onPreferenceClickListener = OnPreferenceClickListener { preference ->
            val cb = preference as CheckBoxPreference
            if (cb.isChecked) {
                cb.isChecked = false
                permissionHelper.checkPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                    requestGps,
                )
            }
            true
        }
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
        if (key == "proto" || key == "link" || key == "aprsis" || key == "afsk") {
            preferenceScreen = null
            loadXml()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionHelper.onResult(requestCode, permissions, grantResults)
    }
}
