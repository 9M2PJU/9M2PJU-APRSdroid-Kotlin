package org.aprsdroid.app

import android.content.Context
import android.util.Log
import org.mapsforge.v3.android.maps.mapgenerator.tiledownloader.TileDownloader
import org.mapsforge.v3.core.Tile
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Kotlin port of the Java `OsmTileDownloader`.
 *
 * A MapsForge TileDownloader that fetches OSM tiles from either the
 * online OpenStreetMap tile server or a local offline tile server
 * (127.0.0.1:8080), depending on the user's offline-map preference.
 *
 * If offline mode is enabled but the local tile server is not
 * reachable, falls back to online OSM tiles so the map still loads
 * instead of showing a blank grid.
 */
class OsmTileDownloader(
    private val prefsWrapper: PrefsWrapper,
) : TileDownloader() {

    private val stringBuilder = StringBuilder()

    override fun getHostName(): String {
        var offline = prefsWrapper.isOfflineMap()
        // If offline mode is on, try to detect if the local tile server
        // is actually running. If not, fall back to online OSM tiles.
        if (offline) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(HOST_NAME_OFFLINE, 8080), 500)
                }
                Log.d(TAG, "Offline tile server detected, using local tiles")
            } catch (e: Exception) {
                Log.w(TAG, "Offline tile server not reachable, falling back to online OSM tiles")
                offline = false
            }
        }
        val hostName = if (offline) HOST_NAME_OFFLINE else HOST_NAME_ONLINE
        Log.d(TAG, "Getting host name: $hostName")
        return hostName
    }

    override fun getProtocol(): String {
        val offline = prefsWrapper.isOfflineMap()
        val protocol = if (offline) "http" else "https"
        Log.d(TAG, "Getting protocol: $protocol")
        return protocol
    }

    override fun getPort(): Int {
        val offline = prefsWrapper.isOfflineMap()
        val port = if (offline) 8080 else 443
        Log.d(TAG, "Getting port: $port")
        return port
    }

    override fun getTilePath(tile: Tile): String {
        stringBuilder.setLength(0)
        stringBuilder.append('/')
        stringBuilder.append(tile.zoomLevel)
        stringBuilder.append('/')
        stringBuilder.append(tile.tileX)
        stringBuilder.append('/')
        stringBuilder.append(tile.tileY)
        stringBuilder.append(".png")

        val tilePath = stringBuilder.toString()
        Log.d(TAG, "Generated tile path: $tilePath")
        return tilePath
    }

    override fun getZoomLevelMax(): Byte {
        Log.d(TAG, "Getting maximum zoom level: $ZOOM_MAX")
        return ZOOM_MAX
    }

    companion object {
        private const val TAG = "OsmTileDownloader"
        private const val HOST_NAME_ONLINE = "tile.openstreetmap.org"
        private const val HOST_NAME_OFFLINE = "127.0.0.1"
        private const val ZOOM_MAX: Byte = 18

        @JvmStatic
        fun create(context: Context): OsmTileDownloader {
            val prefsWrapper = PrefsWrapper(context)
            return OsmTileDownloader(prefsWrapper)
        }
    }
}
