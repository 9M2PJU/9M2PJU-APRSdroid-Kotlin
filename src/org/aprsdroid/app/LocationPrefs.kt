package org.aprsdroid.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceActivity
import android.util.Log
import android.widget.Toast

/**
 * Kotlin port of the Scala `LocationPrefs`.
 *
 * Uses the XML preference framework. Loads the location preference XML
 * (res/xml/location.xml) plus the location-source-specific XML based
 * on the user's current selection. Handles GPS permission for manual
 * position and "choose on map" for manual position entry.
 */
class LocationPrefs : PreferenceActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val prefs by lazy { PrefsWrapper(this) }
    private val requestGps = 101
    private val requestMap = 102

    private val permissionHelper by lazy {
        PermissionHelper(
            this,
            getActionName = { R.string.p_source_get_last },
            onAllGranted = {
                val ls = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val l = PeriodicGPS.bestProvider(ls)?.let { ls.getLastKnownLocation(it) }
                if (l != null) {
                    prefs.prefs.edit()
                        .putString("manual_lat", l.latitude.toString())
                        .putString("manual_lon", l.longitude.toString())
                        .commit()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.map_track_unknown, prefs.getCallsign()),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onFailedCancel = { /* nop */ },
        )
    }

    private fun loadXml() {
        addPreferencesFromResource(R.xml.location)
        addPreferencesFromResource(LocationSources.instanciatePrefsAct(prefs))
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
        if (key == "loc_source" || key == "manual_lat" || key == "manual_lon") {
            preferenceScreen = null
            loadXml()
        }
    }

    override fun onNewIntent(i: Intent?) {
        if (i != null && i.dataString != null) {
            when (i.dataString) {
                "gps2manual" -> permissionHelper.checkPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                    requestGps,
                )
                "chooseOnMap" -> {
                    val mapmode = MapModes.defaultMapMode(this, prefs)
                    startActivityForResult(
                        Intent(this, mapmode.viewClass)
                            .putExtra("info", R.string.p_source_from_map_save),
                        requestMap,
                    )
                }
            }
        }
    }

    override fun onActivityResult(reqCode: Int, resultCode: Int, data: Intent?) {
        Log.d("LocationPrefs", "onActResult: request=$reqCode result=$resultCode $data")
        if (resultCode == RESULT_OK && reqCode == requestMap) {
            prefs.prefs.edit()
                .putString("manual_lat", data?.getFloatExtra("lat", 0.0f).toString())
                .putString("manual_lon", data?.getFloatExtra("lon", 0.0f).toString())
                .commit()
        } else {
            super.onActivityResult(reqCode, resultCode, data)
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
