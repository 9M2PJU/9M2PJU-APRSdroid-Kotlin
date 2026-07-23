package org.aprsdroid.app

import org.aprsdroid.app.ui.AprsBottomBar

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.LocationManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import org.mapsforge.v3.android.maps.MapActivity
import org.mapsforge.v3.android.maps.MapView
import org.mapsforge.v3.android.maps.Projection
import org.mapsforge.v3.android.maps.overlay.ItemizedOverlay
import org.mapsforge.v3.android.maps.overlay.OverlayItem
import org.mapsforge.v3.core.GeoPoint
import java.io.File
import kotlinx.coroutines.runBlocking
import org.aprsdroid.app.data.AprsDatabase

/**
 * Kotlin/Compose port of the Scala `MapAct` + `MapMenuHelper` + `StationOverlay`.
 *
 * OSM map view using MapsForge v3, wrapped in a Jetpack Compose
 * `AndroidView`. Shows APRS stations as symbols from the allicons
 * sprite sheet, with callsign labels and movement traces. Supports
 * offline map files, online OSM tiles, coordinate chooser mode, and
 * tap-to-open station details.
 */
class MapAct : MapActivity(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val tag = "APRSdroid.Map"

    private val prefs by lazy { PrefsWrapper(this) }
    private val db by lazy { AprsDatabase.get(this) }

    private var targetcall = ""
    private var showObjects = false
    private val isCoordinateChooser: Boolean by lazy { callingActivity != null }

    internal var mapview: MapView? = null
    private val allicons by lazy { ContextCompat.getDrawable(this, R.drawable.allicons) as BitmapDrawable }
    private val staoverlay by lazy { StationOverlay(allicons, this) }

    // --- Compose lifecycle support ---
    // MapActivity (from MapsForge) is a plain Activity, not ComponentActivity,
    // so we need to provide Lifecycle/ViewModelStore/SavedStateRegistry for ComposeView.
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val _viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = _viewModelStore
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val locReceiver by lazy {
        LocationReceiver2(
            { loadStations() },
            { stations -> staoverlay.replaceStations(stations) },
            { /* cancel: nop */ },
        )
    }

    private val resultIntent = Intent()

    // Compose state for UI elements
    private var isLoading by mutableStateOf(true)
    private var coordInfo by mutableStateOf("")
    private var acceptEnabled by mutableStateOf(false)
    private var showObjectsChecked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedStateRegistryController.performRestore(savedInstanceState)
        UIHelper.applySystemBarInsets(this)

        targetcall = intent.dataString ?: ""
        showObjects = prefs.getShowObjects()
        showObjectsChecked = showObjects

        setResult(RESULT_CANCELED)

        val composeView = ComposeView(this)
        composeView.setContent {
            MapScreen()
        }
        setContentView(composeView)

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onResume() {
        super.onResume()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        mapview?.requestFocus()
    }

    override fun onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        val mv = mapview
        if (mv != null) {
            val pos = mv.mapPosition.mapPosition
            if (pos?.geoPoint != null) {
                saveMapViewPosition(
                    pos.geoPoint.latitudeE6 / 1000000.0f,
                    pos.geoPoint.longitudeE6 / 1000000.0f,
                    pos.zoomLevel.toFloat(),
                )
            }
        }
        super.onPause()
    }

    override fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        _viewModelStore.clear()
        try {
            unregisterReceiver(locReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MapScreen() {
        var showMenu by remember { mutableStateOf(false) }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(if (isCoordinateChooser) getString(R.string.p_source_from_map)
                             else if (targetcall == "") getString(R.string.app_map)
                             else "${getString(R.string.app_map)}: $targetcall")
                    },
                    actions = {
                        if (!isCoordinateChooser) {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(getString(R.string.map_objects)) },
                                    onClick = {
                                        showMenu = false
                                        val newState = prefs.toggleBoolean("show_objects", true)
                                        showObjectsChecked = newState
                                        showObjects = newState
                                        reloadMap()
                                    },
                                )
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                if (!isCoordinateChooser) {
                    FloatingActionButton(onClick = { centerOnMyLocation() }) {
                        Icon(Icons.Filled.MyLocation, contentDescription = "My Location")
                    }
                }
            },
            bottomBar = {
                if (!isCoordinateChooser) {
                    AprsBottomBar(
                        current = NavTarget.MAP,
                        onNavigate = { target -> AprsNavigation.navigateTo(this, target, prefs) },
                        onPreferences = { AprsNavigation.openPreferences(this) },
                    )
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // MapsForge MapView wrapped in AndroidView
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).also { mv ->
                            mv.setBuiltInZoomControls(true)
                            mv.overlays.add(staoverlay)
                            mv.setTextScale(resources.displayMetrics.density)
                            mapview = mv
                            applyHardwareAcceleration(
                                prefs.getBoolean("hardware_acceleration", true),
                            )
                            reloadMapAndTheme()
                            startLoading()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Loading indicator
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                // Coordinate chooser overlay
                if (isCoordinateChooser) {
                    // Crosshair
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Transparent)
                            .align(Alignment.Center),
                    )
                    // Info text
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = coordInfo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    // Accept button
                    AcceptButton(
                        enabled = acceptEnabled,
                        onClick = {
                            updateResultIntent()
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun AcceptButton(
        enabled: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val ctx = LocalContext.current
        val label = try {
            ctx.getString(intent.getIntExtra("info", 0))
        } catch (_: Exception) {
            getString(R.string.p_source_from_map_save)
        }
        androidx.compose.material3.Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        ) {
            Text(label)
        }
    }

    private fun applyHardwareAcceleration(useHwAccel: Boolean) {
        val mv = mapview ?: return
        if (useHwAccel) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            )
            Log.d(tag, "Hardware acceleration enabled.")
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            mv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            Log.d(tag, "Hardware acceleration disabled for MapView.")
        }
    }

    private fun centerOnMyLocation() {
        try {
            val locMan = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val provider = PeriodicGPS.bestProvider(locMan)
            if (provider != null) {
                val loc = locMan.getLastKnownLocation(provider)
                if (loc != null) {
                    mapview?.controller?.setCenter(GeoPoint(loc.latitude, loc.longitude))
                    mapview?.controller?.setZoom(14)
                    return
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Throwable) {
        }
        Toast.makeText(this, R.string.map_no_location, Toast.LENGTH_SHORT).show()
    }

    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
    }

    private fun saveMapViewPosition(lat: Float, lon: Float, zoom: Float) {
        prefs.prefs.edit()
            .putFloat("map_lat", lat)
            .putFloat("map_lon", lon)
            .putFloat("map_zoom", zoom)
            .commit()
    }

    private fun loadMapViewPosition() {
        val mv = mapview ?: return
        val defaultLat = 52.5075f
        val defaultLon = 13.39027f
        val savedLat = prefs.prefs.getFloat("map_lat", Float.NaN)
        val savedLon = prefs.prefs.getFloat("map_lon", Float.NaN)
        var lat: Float = defaultLat
        var lon: Float = defaultLon
        if (!savedLat.isNaN() && !savedLon.isNaN()) {
            lat = savedLat
            lon = savedLon
        } else {
            try {
                val locMan = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val provider = PeriodicGPS.bestProvider(locMan)
                if (provider != null) {
                    val loc = locMan.getLastKnownLocation(provider)
                    if (loc != null) {
                        lat = loc.latitude.toFloat()
                        lon = loc.longitude.toFloat()
                    } else {
                        lat = defaultLat
                        lon = defaultLon
                    }
                } else {
                    lat = defaultLat
                    lon = defaultLon
                }
            } catch (_: SecurityException) {
                lat = defaultLat
                lon = defaultLon
            } catch (_: Throwable) {
                lat = defaultLat
                lon = defaultLon
            }
        }
        val zoom = prefs.prefs.getFloat("map_zoom", 14.0f)
        mv.controller.setCenter(GeoPoint(lat.toDouble(), lon.toDouble()))
        mv.controller.setZoom(zoom.toInt())
    }

    private fun reloadMapAndTheme() {
        val mv = mapview ?: return
        val tilepath = prefs.getString("tilepath", "")
        val mapfilePath = if (tilepath.isNotEmpty()) tilepath
            else prefs.getString("mapfile", "${Environment.getExternalStorageDirectory()}/aprsdroid.map")
        val mapfile = File(mapfilePath)
        val isMapFileValid = mapfilePath.endsWith(".map") && mapfile.exists() && mapfile.canRead()

        if (prefs.isOfflineMap()) {
            try {
                if (isMapFileValid) {
                    val result = mv.setMapFile(mapfile)
                    if (!result.isSuccess) {
                        Toast.makeText(this, result.errorMessage ?: "", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    loadOnlineMap()
                }
            } catch (e: Exception) {
                Log.e(tag, "Unexpected error during map reload", e)
                loadOnlineMap()
            }
        } else {
            loadOnlineMap()
        }

        val themefile = File(prefs.getString("themefile", "${Environment.getExternalStorageDirectory()}/aprsdroid.xml"))
        if (themefile.exists()) {
            try {
                mv.setRenderTheme(themefile)
            } catch (_: java.io.FileNotFoundException) {
                // ignore
            }
        }
        loadMapViewPosition()
    }

    private fun loadOnlineMap() {
        val mv = mapview ?: return
        try {
            if (mv.mapFile == null) {
                val mapGen = OsmTileDownloader.create(this)
                mapGen.setUserAgent(getString(R.string.build_version))
                mv.setMapGenerator(mapGen)
            }
        } catch (_: UnsupportedOperationException) {
            // ignore
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                changeZoom(+1)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                changeZoom(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                if (isCoordinateChooser) {
                    updateResultIntent()
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun updateResultIntent() {
        val mv = mapview ?: return
        val pos = mv.mapPosition.mapPosition ?: return
        pos.geoPoint ?: return
        val lat = pos.geoPoint.latitudeE6 / 1000000.0f
        val lon = pos.geoPoint.longitudeE6 / 1000000.0f
        resultIntent.putExtra("lat", lat)
        resultIntent.putExtra("lon", lon)
        updateCoordinateInfo(lat, lon)
    }

    private fun updateCoordinateInfo(lat: Float, lon: Float) {
        if (!isCoordinateChooser) return
        val (latS, lonS) = AprsPacket.formatCoordinates(lat, lon)
        coordInfo = "$latS\n$lonS"
        acceptEnabled = true
    }

    fun updateCoordinateInfoFromMap() {
        if (!isCoordinateChooser) return
        val mv = mapview ?: return
        val pos = mv.mapPosition.mapPosition ?: return
        pos.geoPoint ?: return
        updateCoordinateInfo(
            pos.geoPoint.latitudeE6 / 1000000.0f,
            pos.geoPoint.longitudeE6 / 1000000.0f,
        )
    }

    fun changeZoom(delta: Int) {
        val mv = mapview ?: return
        mv.controller.setZoom(mv.mapPosition.zoomLevel + delta)
    }

    private fun animateToCall() {
        if (targetcall.isNotEmpty()) {
            Thread {
                val sta = runBlocking { db.stationDao().getStaPosition(targetcall) }
                if (sta != null) {
                    runOnUiThread {
                        mapview?.controller?.setCenter(GeoPoint(sta.lat.toDouble(), sta.lon.toDouble()))
                    }
                }
            }.start()
        }
    }

    internal fun onPostLoad() {
        mapview?.invalidate()
        onStopLoading()
        animateToCall()
    }

    private fun startLoading() {
        UIHelper.safeRegisterReceiver(this, locReceiver, IntentFilter(AprsService.UPDATE))
        locReceiver.startTask(null)
    }

    fun reloadMap() {
        onStartLoading()
        locReceiver.startTask(null)
    }

    fun onStartLoading() {
        isLoading = true
    }

    fun onStopLoading() {
        isLoading = false
    }

    fun openDetails(call: String) {
        startActivity(Intent(this, StationActivity::class.java).setData(android.net.Uri.parse(call)))
    }

    /**
     * Load stations from Room for the map overlay.
     */
    private fun loadStations(): ArrayList<OSMStation> {
        val stations = ArrayList<OSMStation>()
        val ageTs = System.currentTimeMillis() - prefs.getShowAge()
        val allStations = runBlocking {
            db.stationDao().neighborsRaw(prefs.getCallSsid(), ageTs)
        }
        for (sta in allStations) {
            val symbol = sta.symbol ?: "/$"
            val p = GeoPoint(sta.lat, sta.lon)
            stations.add(OSMStation(ArrayList(), p, sta.call, sta.origin ?: "", symbol))
        }
        Log.d(tag, "total ${stations.size} items")
        return stations
    }
}

/**
 * A station on the map overlay.
 */
class OSMStation(
    val movelog: ArrayList<GeoPoint>,
    pt: GeoPoint,
    val call: String,
    val origin: String,
    val symbol: String,
) : OverlayItem(pt, call, origin) {

    fun inArea(bl: GeoPoint, tr: GeoPoint): Boolean {
        val pt = point
        val latOk = bl.latitudeE6 <= pt.latitudeE6 && pt.latitudeE6 <= tr.latitudeE6
        val lonOk = if (bl.longitudeE6 <= tr.longitudeE6) {
            bl.longitudeE6 <= pt.longitudeE6 && pt.longitudeE6 <= tr.longitudeE6
        } else {
            bl.longitudeE6 <= pt.longitudeE6 || pt.longitudeE6 <= tr.longitudeE6
        }
        return latOk && lonOk
    }
}

/**
 * Custom ItemizedOverlay that renders APRS stations as symbols from
 * the allicons sprite sheet, with callsign labels and movement traces.
 */
class StationOverlay(
    icons: Drawable,
    private val context: MapAct,
) : ItemizedOverlay<OSMStation>(icons) {

    private val tag = "APRSdroid.StaOverlay"

    var stations = ArrayList<OSMStation>()

    init {
        // prevent android bug #11666
        populate()
    }

    private val iconbitmap = (icons as BitmapDrawable).bitmap
    private val symbolSize = iconbitmap.width / 16
    private val drawSize = (context.resources.displayMetrics.density * 24).toInt()

    init {
        icons.setBounds(0, 0, symbolSize, symbolSize)
    }

    override fun size(): Int = stations.size
    override fun createItem(idx: Int): OSMStation = stations[idx]

    private fun symbol2rect(index: Int, page: Int): Rect {
        if (index < 0 || index >= 6 * 16) return Rect(0, 0, symbolSize, symbolSize)
        val altOffset = page * symbolSize * 6
        val y = (index / 16) * symbolSize + altOffset
        val x = (index % 16) * symbolSize
        return Rect(x, y, x + symbolSize, y + symbolSize)
    }

    private fun symbol2rect(symbol: String): Rect {
        return symbol2rect(symbol[1].code - 33, if (symbol[0] == '/') 0 else 1)
    }

    private fun symbolIsOverlayed(symbol: String): Boolean {
        return symbol[0] != '/' && symbol[0] != '\\'
    }

    private fun drawTrace(c: Canvas, proj: Projection, s: OSMStation) {
        val tracePaint = Paint().apply {
            setARGB(128, 100, 100, 255)
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = drawSize / 6f
            isAntiAlias = true
        }
        val dotPaint = Paint().apply {
            setARGB(128, 255, 0, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        if (s.movelog.size < 2) return

        val path = Path()
        val point = Point()
        var first = true
        for (p in s.movelog) {
            proj.toPixels(p, point)
            if (first) {
                path.moveTo(point.x.toFloat(), point.y.toFloat())
                first = false
            } else {
                path.lineTo(point.x.toFloat(), point.y.toFloat())
            }
            c.drawCircle(point.x.toFloat(), point.y.toFloat(), drawSize / 12f, dotPaint)
        }
        c.drawPath(path, tracePaint)
    }

    override fun drawOverlayBitmap(c: Canvas, dp: Point, proj: Projection, zoom: Byte) {
        val mv = context.mapview ?: return
        if (!mv.mapPosition.isValid) return
        Log.d(tag, "draw: symbolSize=$symbolSize drawSize=$drawSize")
        val fontSize = drawSize * 7 / 8
        val textPaint = Paint().apply {
            color = 0xff000000.toInt()
            textAlign = Paint.Align.CENTER
            textSize = fontSize.toFloat()
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
        val strokePaint = Paint(textPaint).apply {
            color = 0xffc8ffc8.toInt()
            style = Paint.Style.STROKE
            strokeWidth = drawSize / 12.0f
            setShadowLayer(10f, 0f, 0f, 0x80c8ffc8.toInt())
        }

        val p = Point()
        val width = c.width
        val height = c.height
        val ss = drawSize / 2

        for (s in stations) {
            proj.toPixels(s.getPoint(), p)
            if (p.x >= 0 && p.y >= 0 && p.x < width && p.y < height) {
                val srcRect = symbol2rect(s.symbol)
                val destRect = Rect(p.x - ss, p.y - ss, p.x + ss, p.y + ss)
                if (zoom.toInt() >= 10) {
                    drawTrace(c, proj, s)
                    c.drawText(s.call, p.x.toFloat(), (p.y + ss + fontSize).toFloat(), strokePaint)
                    c.drawText(s.call, p.x.toFloat(), (p.y + ss + fontSize).toFloat(), textPaint)
                }
                c.drawBitmap(iconbitmap, srcRect, destRect, null)
                if (symbolIsOverlayed(s.symbol)) {
                    c.drawBitmap(iconbitmap, symbol2rect(s.symbol[0].code - 33, 2), destRect, null)
                }
            }
        }
        context.runOnUiThread {
            context.updateCoordinateInfoFromMap()
        }
    }

    fun replaceStations(s: ArrayList<OSMStation>) {
        stations = s
        Benchmark("populate") { populate() }
        context.onPostLoad()
    }

    override fun onTap(gp: GeoPoint, mv: MapView): Boolean {
        val proj = mv.projection
        val p = proj.toPixels(gp, null)
        val botleft = proj.fromPixels(p.x - 50, p.y + 50)
        val topright = proj.fromPixels(p.x + 50, p.y - 50)
        Log.d(tag, "from $botleft to $topright")
        val list = stations.filter { it.inArea(botleft, topright) }.map { it.call }
        Log.d(tag, "found ${list.size} stations")
        return when {
            list.isEmpty() -> false
            list.size == 1 -> {
                context.openDetails(list[0])
                true
            }
            else -> {
                AlertDialog.Builder(context)
                    .setTitle(R.string.map_select)
                    .setItems(list.toTypedArray()) { _, item ->
                        context.openDetails(list[item])
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
        }
    }

    override fun onTap(index: Int): Boolean {
        val s = stations[index]
        Log.d(tag, "user clicked on ${s.call}")
        context.openDetails(s.call)
        return true
    }
}
