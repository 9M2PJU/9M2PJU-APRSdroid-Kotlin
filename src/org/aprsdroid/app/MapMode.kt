package org.aprsdroid.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.MenuItem

/**
 * Kotlin port of the Scala `MapMode` / `MapModes` system.
 *
 * Describes the available map renderers and dispatches to the right
 * activity. Per the project preferences (see AGENTS.md) the Kotlin
 * rewrite uses OpenStreetMap-based rendering only — the Google Maps
 * mode from the Scala version has been removed.
 *
 * The OSM map activity (`MapAct`) is currently a Compose placeholder
 * while the full tile renderer + station overlay is being ported.
 */
open class MapMode(
    val tag: String,
    val menuId: Int,
    val title: String?,
    val viewClass: Class<*>,
) {
    open fun isAvailable(ctx: Context): Boolean = true
}

class MapsforgeOnlineMode(
    tag: String,
    menuId: Int,
    title: String?,
    val foo: String,
) : MapMode(tag, menuId, title, MapAct::class.java)

class MapsforgeFileMode(
    tag: String,
    menuId: Int,
    title: String?,
    val file: String,
) : MapMode(tag, menuId, title, MapAct::class.java)

object MapModes {

    private val allMapModes = mutableListOf<MapMode>()

    @JvmStatic
    fun initialize(ctx: Context) {
        if (allMapModes.isNotEmpty()) return
        allMapModes += MapsforgeOnlineMode("osm", 0, null, "TODO")
    }

    @JvmStatic
    fun reloadOfflineMaps(ctx: Context) {
        // No-op for now — offline map discovery will be restored with MapAct.
    }

    @JvmStatic
    fun defaultMapMode(ctx: Context, prefs: PrefsWrapper): MapMode {
        initialize(ctx)
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
