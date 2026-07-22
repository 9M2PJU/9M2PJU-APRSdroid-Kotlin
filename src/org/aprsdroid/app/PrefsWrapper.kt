package org.aprsdroid.app

import android.content.Context
import android.location.LocationManager
import android.media.AudioManager
import android.preference.PreferenceManager
import android.util.Log

/**
 * Kotlin port of the Scala `PrefsWrapper`.
 *
 * Wraps `SharedPreferences` with APRSdroid-specific accessors.
 */
class PrefsWrapper(val context: Context) {

    val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    // ---- raw accessors ----

    fun getString(key: String, defValue: String): String =
        prefs.getString(key, defValue) ?: defValue

    fun getBoolean(key: String, defValue: Boolean): Boolean =
        prefs.getBoolean(key, defValue)

    fun set(key: String, newValue: String): String {
        prefs.edit().putString(key, newValue).commit()
        return newValue
    }

    fun setBoolean(name: String, newValue: Boolean): Boolean {
        prefs.edit().putBoolean(name, newValue).commit()
        return newValue
    }

    fun toggleBoolean(name: String, default: Boolean): Boolean {
        val newVal = !prefs.getBoolean(name, default)
        Log.d("toggleBoolean", "$name=$newVal")
        return setBoolean(name, newVal)
    }

    // ---- feature toggles ----

    fun isIgateEnabled(): Boolean = prefs.getBoolean("p.igating", false)

    fun isDigipeaterEnabled(): Boolean = prefs.getBoolean("p.digipeating", false)

    fun isRegenerateEnabled(): Boolean = prefs.getBoolean("p.regenerate", false)

    fun isAckDupeEnabled(): Boolean = prefs.getBoolean("p.ackdupetoggle", false)

    fun isMsgDupeEnabled(): Boolean = prefs.getBoolean("p.msgdupetoggle", false)

    fun isMetric(): Boolean = getString("p.units", "1") == "1"

    fun isOfflineMap(): Boolean = prefs.getBoolean("p.offlinemap", false)

    fun getShowObjects(): Boolean = prefs.getBoolean("show_objects", true)

    fun getShowSatellite(): Boolean = prefs.getBoolean("show_satellite", false)

    // ---- safe numeric reads ----

    fun getStringInt(key: String, defValue: Int): Int {
        return try {
            prefs.getString(key, null)?.trim()?.toInt() ?: defValue
        } catch (_: Throwable) {
            defValue
        }
    }

    fun getStringFloat(key: String, defValue: Float): Float {
        return try {
            prefs.getString(key, null)?.trim()?.toFloat() ?: defValue
        } catch (_: Throwable) {
            defValue
        }
    }

    // ---- commonly used prefs ----

    fun getCallsign(): String =
        prefs.getString("callsign", "")?.trim()?.uppercase() ?: ""

    fun getPasscode(): String {
        val s = prefs.getString("passcode", "") ?: ""
        return if (s.isEmpty()) "-1" else s
    }

    fun getSsid(): String = getString("ssid", "10")

    fun getCallSsid(): String = AprsPacket.formatCallSsid(getCallsign(), getSsid())

    fun getShowAge(): Long = getStringInt("show_age", 30) * 60L * 1000L

    // ---- list-pref helpers ----

    fun getListItemIndex(pref: String, default: String, values: Int): Int {
        Log.d("getLII", getString(pref, default))
        val arr = context.resources.getStringArray(values)
        Log.d("getLII", "values: ${arr.joinToString(" ")}")
        return arr.indexOf(getString(pref, default))
    }

    fun getListItemName(pref: String, default: String, values: Int, names: Int): String {
        val id = getListItemIndex(pref, default, values)
        Log.d("getLIN", "id is $id")
        return if (id < 0) "<not in list>" else context.resources.getStringArray(names)[id]
    }

    fun getLocationSourceName(): String =
        getListItemName("loc_source", "manual", R.array.p_locsource_ev, R.array.p_locsource_e)

    fun getBackendName(): String {
        val proto = getListItemName(
            "proto", "aprsis",
            R.array.p_conntype_ev, R.array.p_conntype_e,
        )
        val link = AprsBackend.defaultProtoInfo(this).link
        return when (link) {
            "afsk" -> "$proto, ${getListItemName(link, "afsk12", R.array.p_afsk_ev, R.array.p_afsk_e)}"
            "aprsis" -> "$proto, ${getListItemName(link, "tcp", R.array.p_aprsis_ev, R.array.p_aprsis_e)}"
            "link" -> "$proto, ${getListItemName(link, "bluetooth", R.array.p_link_ev, R.array.p_link_e)}"
            else -> proto
        }
    }

    // ---- version / login ----

    fun getVersion(): String =
        context.getString(R.string.build_version).split(" ").take(2).joinToString(" ")

    fun getLoginString(): String =
        AprsPacket.formatLogin(getCallsign(), getSsid(), getPasscode(), getVersion())

    fun getFilterString(service: AprsService): String {
        val filterdist = getStringInt("tcp.filterdist", 50)
        val userfilter = getString("tcp.filter", "")
        val lastloc = try {
            val locMan = service.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            AprsPacket.formatRangeFilter(
                locMan.getLastKnownLocation(PeriodicGPS.bestProvider(locMan) ?: LocationManager.GPS_PROVIDER),
                filterdist,
            )
        } catch (_: IllegalArgumentException) {
            ""
        } catch (_: NullPointerException) {
            ""
        }
        return if (filterdist == 0) {
            " filter $userfilter $lastloc"
        } else {
            " filter m/$filterdist $userfilter $lastloc"
        }
    }

    // ---- backend / AFSK prefs ----

    fun getProto(): String = getString("proto", "aprsis")

    fun getAfskHQ(): Boolean = getBoolean("afsk.hqdemod", true)

    fun getAfskRTS(): Boolean = getBoolean("afsk.ptt", false)

    fun getPTTPort(): String = getString("afsk.pttport", "")

    fun getAfskBluetooth(): Boolean = getBoolean("afsk.btsco", false) && getAfskHQ()

    fun getAfskOutput(): Int =
        if (getAfskBluetooth()) AudioManager.STREAM_VOICE_CALL else getStringInt("afsk.output", 0)
}
