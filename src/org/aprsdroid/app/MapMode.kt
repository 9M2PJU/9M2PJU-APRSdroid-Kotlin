package org.aprsdroid.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.MenuItem
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.GoogleMap

/**
 * Kotlin port of the Scala `MapMode` / `MapModes` system.
 *
 * Describes the available map renderers (Google Maps, MapsForge online,
 * MapsForge offline) and dispatches to the right activity.
 *
 * The Google Maps and MapsForge activities are not yet ported — until they
 * are, the map modes fall back to `HubActivity` as a placeholder view class.
 */
open class MapMode(
    val tag: String,
    val menuId: Int,
    val title: String?,
    val viewClass: Class<*>,
) {
    open fun isAvailable(ctx: Context): Boolean = true
}

class GoogleMapMode(
    tag: String,
    menuId: Int,
    title: String?,
    val mapType: Int,
) : MapMode(tag, menuId, title, resolveMapClass("org.aprsdroid.app.GoogleMapAct")) {

    override fun isAvailable(ctx: Context): Boolean {
        return try {
            ctx.packageManager.getPackageInfo(GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}

class MapsforgeOnlineMode(
    tag: String,
    menuId: Int,
    title: String?,
    val foo: String,
) : MapMode(tag, menuId, title, resolveMapClass("org.aprsdroid.app.MapAct"))

class MapsforgeFileMode(
    tag: String,
    menuId: Int,
    title: String?,
    val file: String,
) : MapMode(tag, menuId, title, resolveMapClass("org.aprsdroid.app.MapAct"))

object MapModes {

    private val allMapModes = mutableListOf<MapMode>()

    @JvmStatic
    fun initialize(ctx: Context) {
        if (allMapModes.isNotEmpty()) return
        allMapModes += GoogleMapMode("google", R.id.normal, null, GoogleMap.MAP_TYPE_NORMAL)
        allMapModes += GoogleMapMode("satellite", R.id.satellite, null, GoogleMap.MAP_TYPE_HYBRID)
        allMapModes += MapsforgeOnlineMode("osm", R.id.mapsforge, null, "TODO")
    }

    @JvmStatic
    fun reloadOfflineMaps(ctx: Context) {
        // No-op for now — offline map discovery will be restored with MapAct.
    }

    @JvmStatic
    fun defaultMapMode(ctx: Context, prefs: PrefsWrapper): MapMode {
        initialize(ctx)
        // Default to OSM maps — Google Maps requires a build-time API key
        // that is not included in this mod. Users who build from source
        // with their own key can still select Google Maps from the menu.
        val tag = prefs.getString("mapmode", "osm")
        Log.d("MapModes", "tag is $tag")
        var default: MapMode? = null
        for (mode in allMapModes) {
            Log.d("MapModes", "mode ${mode.tag} isA=${mode.isAvailable(ctx)}")
            if (default == null && mode.isAvailable(ctx)) {
                default = mode
            }
            if (mode.tag == tag && mode.isAvailable(ctx)) {
                Log.d("MapModes", "mode ${mode.tag} is tagged")
                return mode
            }
        }
        Log.d("MapModes", "mode ${default?.tag} is default")
        return default ?: allMapModes.first()
    }

    @JvmStatic
    fun startMap(ctx: Context, prefs: PrefsWrapper, targetcall: String?) {
        val mm = defaultMapMode(ctx, prefs)
        val intent = Intent(ctx, mm.viewClass)
        if (!targetcall.isNullOrEmpty()) {
            intent.data = Uri.parse(targetcall)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        ctx.startActivity(intent)
        if (ctx is Activity) {
            @Suppress("DEPRECATION")
            ctx.overridePendingTransition(0, 0)
        }
    }

    @JvmStatic
    fun setDefault(prefs: PrefsWrapper, tag: String) {
        prefs.set("mapmode", tag)
    }

    @JvmStatic
    fun fromMenuItem(mi: MenuItem): MapMode? {
        for (mode in allMapModes) {
            if (mode.menuId == mi.itemId) return mode
        }
        return null
    }
}

/**
 * Resolve a map activity class by name, falling back to [HubActivity]
 * until the real Google/MapsForge activities are ported.
 */
private fun resolveMapClass(name: String): Class<*> {
    return try {
        Class.forName(name)
    } catch (_: ClassNotFoundException) {
        HubActivity::class.java
    }
}
