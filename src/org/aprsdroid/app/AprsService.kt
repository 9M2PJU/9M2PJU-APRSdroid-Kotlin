package org.aprsdroid.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.ab0oo.aprs.parser.APRSPacket
import net.ab0oo.aprs.parser.APRSTypes
import net.ab0oo.aprs.parser.CourseAndSpeedExtension
import net.ab0oo.aprs.parser.Digipeater
import net.ab0oo.aprs.parser.InformationField
import net.ab0oo.aprs.parser.MessagePacket
import net.ab0oo.aprs.parser.ObjectPacket
import net.ab0oo.aprs.parser.Parser
import net.ab0oo.aprs.parser.Position
import net.ab0oo.aprs.parser.PositionPacket
import org.aprsdroid.app.data.AprsDatabase
import org.aprsdroid.app.data.PostEntity
import org.aprsdroid.app.data.StationEntity

/**
 * Kotlin port of the Scala `AprsService`.
 *
 * Foreground service that runs the APRS backend (TCP/AFSK/Bluetooth/USB),
 * collects GPS positions, formats and transmits APRS packets, parses
 * incoming packets, and dispatches broadcasts to the UI.
 *
 * Backends, location sources, and messaging are wired in via stubs that
 * will be replaced as the rest of the app is ported.
 */
class AprsService : Service() {

    val TAG = "APRSdroid.Service"

    val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var prefs: PrefsWrapper
    lateinit var db: AprsDatabase

    var poster: AprsBackendInterface? = null
    var locSource: LocationSource? = null
    var msgService: MessageService? = null
    var igateService: IgateService? = null
    var digipeaterService: DigipeaterService? = null
    var singleShot = false

    // ---- lifecycle ----

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsWrapper(this)
        db = AprsDatabase.get(this)
    }

    override fun onStartCommand(i: Intent, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: $i, $flags, $startId")
        handleStart(i)
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onUnbind(intent: Intent?): Boolean = false

    override fun onDestroy() {
        running = false
        linkError = 0
        poster?.stop()
        locSource?.stop()
        msgService?.stop()
        igateService?.stop()
        serviceScope.cancel()
        ServiceNotifier.instance.stop(this)
        super.onDestroy()
    }

    // ---- start / stop handling ----

    private fun handleStart(i: Intent) {
        when (i.action) {
            SERVICE_STOP -> {
                prefs.setBoolean("service_running", false)
                if (running) stopSelf()
                return
            }
            SERVICE_SEND_PACKET -> {
                if (!running) {
                    Log.d(TAG, "SEND_PACKET ignored, service not running.")
                    return
                }
                val dataField = i.getStringExtra("data")
                if (dataField == null) {
                    Log.d(TAG, "SEND_PACKET ignored, data extra is empty.")
                    return
                }
                val p = Parser.parseBody(prefs.getCallSsid(), appVersion, null, dataField)
                sendPacket(p)
                return
            }
            SERVICE_FREQUENCY -> {
                val dataField = i.getStringExtra("frequency")
                if (dataField == null) {
                    Log.d(TAG, "FREQUENCY ignored, 'frequency' extra is empty.")
                    return
                }
                val freqCleaned = dataField.replace("MHz", "").trim()
                val freq = try { freqCleaned.toFloat(); freqCleaned } catch (_: Throwable) { "" }
                if (prefs.getString("frequency", "") != freq) {
                    prefs.set("frequency", freq)
                    if (!running) return
                    // fall through into SERVICE_ONCE
                } else return
            }
        }

        val toastString = if (i.action == SERVICE_ONCE) {
            singleShot = !running || singleShot
            if (singleShot) getString(R.string.service_once) else null
        } else {
            getString(R.string.service_start)
        }
        if (toastString != null) {
            showToast(toastString.format(prefs.getLocationSourceName(), prefs.getBackendName()))
        }

        val callssid = prefs.getCallSsid()
        ServiceNotifier.instance.start(this, callssid)

        if (!running) {
            running = true
            startPoster()
            // Create the message service for incoming/outgoing APRS messages
            msgService = MessageService(this)
            // Create the digipeater service (only active over KISS/AFSK backends)
            digipeaterService = DigipeaterService(prefs, TAG) { packet ->
                sendDigipeatedPacket(packet)
            }
        } else {
            onPosterStarted()
        }
    }

    private fun startPoster() {
        poster?.stop()
        poster = AprsBackend.instanciateUploader(this, prefs)
        // (Re)create the location source so changes to the loc_source
        // preference take effect on each service start.
        locSource?.stop()
        locSource = LocationSources.instanciateLocation(this, prefs)
        val p = poster ?: return
        if (p.start()) {
            onPosterStarted()
        }
        // Start the IgateService if igating is enabled
        if (prefs.isIgateEnabled()) {
            igateService?.stop()
            igateService = IgateService(this, prefs)
            igateService?.start()
        }
    }

    fun onPosterStarted() {
        Log.d(TAG, "onPosterStarted")
        // (Re)start location source, get location source name.
        val locInfo = locSource?.start(singleShot) ?: getString(R.string.p_source_periodic)

        val callssid = prefs.getCallSsid()
        val message = "$callssid: $locInfo"
        ServiceNotifier.instance.start(this, message)

        // Kick off the outgoing message retry loop
        msgService?.sendPendingMessages()

        sendBroadcast(
            Intent(SERVICE_STARTED)
                .putExtra(API_VERSION, API_VERSION_CODE)
                .putExtra(CALLSIGN, callssid)
        )

        if (!singleShot) {
            prefs.setBoolean("service_running", true)
        }

        playNotifySound("start_notify_ringtone")
    }

    // ---- packet helpers ----

    val appVersion: String
        get() = try {
            "APDR%s".format(
                packageManager.getPackageInfo(packageName, 0).versionName
                    ?.filter { it.isDigit() }
                    ?.take(2)
            )
        } catch (e: Exception) {
            "APDR20"
        }

    fun newPacket(payload: InformationField): APRSPacket {
        val digipath = prefs.getString("digi_path", "WIDE1-1")
        return APRSPacket(prefs.getCallSsid(), appVersion, Digipeater.parseList(digipath, true), payload)
    }

    fun newPacketMice(payload: String, destString: String): APRSPacket {
        val digipath = prefs.getString("digi_path", "WIDE1-1")
        val callsignSsid = prefs.getCallSsid()
        Log.d("newPacketMice", "digipath retrieved: $digipath")
        val micePacketString = if (digipath.isNotEmpty()) {
            "$callsignSsid>$destString,$digipath:$payload"
        } else {
            "$callsignSsid>$destString:$payload"
        }
        Log.d("newPacketMice", "Constructed MICE Packet: $micePacketString")
        return Parser.parse(micePacketString)
    }

    fun formatLoc(symbol: String, status: String, location: Location): APRSPacket {
        Log.d("formatLoc", "Symbol: $symbol")
        Log.d("formatLoc", "Status: $status")
        Log.d("formatLoc", "Location: Latitude=${location.latitude}, Longitude=${location.longitude}")

        val pos = Position(location.latitude, location.longitude, 0, symbol[0], symbol[1])
        pos.positionAmbiguity = prefs.getStringInt("priv_ambiguity", 0)

        val statusSpd = if (prefs.getBoolean("priv_spdbear", true)) {
            if (prefs.getBoolean("compressed_location", false)) {
                AprsPacket.formatCourseSpeedCompressed(location)
            } else {
                AprsPacket.formatCourseSpeed(location)
            }
        } else ""
        Log.d("formatLoc", "Status Speed: $statusSpd")

        val statusFreq = AprsPacket.formatFreq(statusSpd, prefs.getStringFloat("frequency", 0.0f))

        val statusAlt = if (prefs.getBoolean("priv_altitude", true)) {
            if (prefs.getBoolean("compressed_location", false) && statusSpd.isEmpty()) {
                AprsPacket.formatAltitudeCompressed(location)
            } else {
                AprsPacket.formatAltitude(location)
            }
        } else ""
        Log.d("formatLoc", "Status Altitude: $statusAlt")

        return if (prefs.getBoolean("compressed_location", false)) {
            if (statusSpd.isEmpty()) {
                if (statusAlt.isEmpty()) {
                    pos.setCsTField(" sT")
                } else {
                    pos.setCsTField(statusAlt + "3")
                }
                val packet = PositionPacket(pos, "$statusFreq $status", true)
                packet.setCompressedFormat(true)
                newPacket(packet)
            } else {
                pos.setCsTField("$statusSpd[")
                val packet = PositionPacket(pos, statusFreq + statusAlt + " " + status, true)
                packet.setCompressedFormat(true)
                newPacket(packet)
            }
        } else {
            val packet = PositionPacket(pos, statusSpd + statusFreq + statusAlt + " " + status, true)
            newPacket(packet)
        }
    }

    fun formatLocMice(symbol: String, status: String, location: Location): APRSPacket {
        val privambiguity = 5 - prefs.getStringInt("priv_ambiguity", 0)
        val ambiguity = if (privambiguity == 5) 0 else privambiguity

        Log.d("MICE", "Set Ambiguity $ambiguity")

        val miceStatus = prefs.getString("p__location_mice_status", "Off Duty")
        val (a, b, c) = AprsPacket.statusToBits(miceStatus)

        val statusFreq = AprsPacket.formatFreqMice(prefs.getStringFloat("frequency", 0.0f))
        val (statusSpd, course) = AprsPacket.formatCourseSpeedMice(location)

        val (infoString, west, longOffset) = AprsPacket.encodeInfo(
            location.longitude, statusSpd.toDouble(), course.toDouble(), symbol
        )
        val destString = AprsPacket.encodeDest(
            location.latitude, longOffset, west, a, b, c, ambiguity
        )

        val altitudeValue = if (prefs.getBoolean("priv_altitude", true)) {
            AprsPacket.formatAltitudeMice(location)
        } else {
            null
        }
        val altString = altitudeValue?.let { AprsPacket.altitude(it.toDouble()) } ?: ""

        val formatPayload = infoString +
            (if (altString.isEmpty()) "" else altString) +
            (if (status.isEmpty()) "" else status) +
            (if (status.isNotEmpty() && statusFreq.isNotEmpty()) " " else "") +
            (if (statusFreq.isEmpty()) "" else "$statusFreq[1")

        Log.d("formatLoc", "MICE: $infoString $destString $altString")

        return newPacketMice(formatPayload, destString)
    }

    fun postLocation(location: Location) {
        var symbol = prefs.getString("symbol", "")
        if (symbol.length != 2) {
            symbol = getString(R.string.default_symbol)
        }
        val status = prefs.getString("status", getString(R.string.default_status))

        val packet = if (prefs.getBoolean("compressed_mice", false)) {
            formatLocMice(symbol, status, location)
        } else {
            formatLoc(symbol, status, location)
        }

        Log.d(TAG, "packet: $packet")
        sendPacket(packet, " (±${location.accuracy.toInt()}m)")
    }

    // ---- send / parse ----

    fun sendPacket(packet: APRSPacket, statusPostfix: String) {
        serviceScope.launch {
            val status = try {
                val s = poster?.update(packet) ?: "no backend"
                if (statusPostfix == "Digipeated") {
                    addPost(PostEntity.TYPE_DIGI, statusPostfix, packet.toString())
                    statusPostfix
                } else if (statusPostfix == "APRS-IS > RF") {
                    addPost(PostEntity.TYPE_TX, "APRS-IS > RF", packet.toString())
                    statusPostfix
                } else {
                    val fullStatus = s + statusPostfix
                    addPost(PostEntity.TYPE_POST, fullStatus, packet.toString())
                    fullStatus
                }
            } catch (e: Exception) {
                addPost(PostEntity.TYPE_ERROR, "Error", e.toString())
                e.printStackTrace()
                e.toString()
            }
            handler.post { sendPacketFinished(status) }
        }
    }

    fun sendPacket(packet: APRSPacket) {
        sendPacket(packet, "")
    }

    fun sendPacketFinished(result: String) {
        if (singleShot) {
            singleShot = false
            stopSelf()
        } else {
            val message = "${prefs.getCallSsid()}: $result"
            ServiceNotifier.instance.notifyPosition(this, prefs, message)
        }
    }

    fun sendDigipeatedPacket(packetString: String) {
        try {
            val digipeatedPacket = Parser.parse(packetString)
            sendPacket(digipeatedPacket, "Digipeated")
            Log.d(TAG, "Successfully sent packet: $packetString")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send packet: $packetString", e)
        }
    }

    fun sendThirdPartyPacket(packetString: String) {
        try {
            val igatedPacket = Parser.parse(packetString)
            sendPacket(igatedPacket, "APRS-IS > RF")
            Log.d(TAG, "Successfully sent packet: $packetString")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send packet: $packetString", e)
        }
    }

    fun parsePacket(ts: Long, message: String, source: Int) {
        try {
            var fap = Parser.parse(message)
            val digiPathCheck = fap.digiString

            if (fap.type == APRSTypes.T_THIRDPARTY) {
                Log.d(TAG, "parsePacket: third-party packet from ${fap.sourceCall}")
                val inner = fap.aprsInformation.toString()
                fap = Parser.parse(inner.substring(1, inner.length))
            }

            val callssid = prefs.getCallSsid()
            if (source == PostEntity.TYPE_INCMG &&
                fap.sourceCall.equals(callssid, ignoreCase = true) &&
                fap.lastUsedDigi != null
            ) {
                Log.i(TAG, "got digipeated own packet")
                val msg = getString(
                    R.string.got_digipeated,
                    fap.lastUsedDigi,
                    fap.aprsInformation.toString(),
                )
                ServiceNotifier.instance.notifyPosition(this, prefs, msg, "dgp_")
                return
            }

            if (fap.aprsInformation == null) {
                Log.d(TAG, "parsePacket() misses payload: $message")
                return
            }
            if (fap.hasFault()) {
                throw Exception("FAP fault")
            }
            when (val info = fap.aprsInformation) {
                is PositionPacket -> addPosition(ts, fap, info, info.position, null)
                is ObjectPacket -> addPosition(ts, fap, info, info.position, info.objectName)
                is MessagePacket -> {
                    msgService?.handleMessage(ts, fap, info, digiPathCheck)
                    Log.d(TAG, "received message from ${fap.sourceCall}: ${info.messageBody}")
                }
                else -> Log.d(TAG, "parsePacket: unhandled info type: ${info.javaClass}")
            }
        } catch (e: Exception) {
            Log.d(TAG, "parsePacket() unsupported packet: $message")
            e.printStackTrace()
        }
    }

    private fun getCSE(field: InformationField): CourseAndSpeedExtension? {
        return field.extension as? CourseAndSpeedExtension
    }

    fun addPosition(
        ts: Long,
        ap: APRSPacket,
        field: InformationField,
        pos: Position,
        objectname: String?,
    ) {
        val cse = getCSE(field)
        serviceScope.launch {
            val call = ap.sourceCall
            val lat = (pos.latitude * 1_000_000).toInt()
            val lon = (pos.longitude * 1_000_000).toInt()
            val sym = "${pos.symbolTable}${pos.symbolCode}"
            val comment = ap.aprsInformation?.comment ?: ""
            val qrg = AprsPacket.parseQrg(comment)
            val name = objectname ?: call

            // Insert into positions table
            db.positionDao().addPosition(ts, name, lat, lon)

            // Replace into stations table
            db.stationDao().upsert(
                StationEntity(
                    ts = ts,
                    call = name,
                    lat = lat,
                    lon = lon,
                    speed = cse?.speed,
                    course = cse?.course,
                    symbol = sym,
                    comment = comment,
                    origin = if (objectname != null) call else null,
                    qrg = qrg,
                    flags = 0,
                )
            )
        }

        sendBroadcast(
            Intent(POSITION)
                .putExtra(SOURCE, ap.sourceCall)
                .putExtra(LOCATION, AprsPacket.position2location(ts, pos, cse))
                .putExtra(CALLSIGN, objectname ?: ap.sourceCall)
                .putExtra(PACKET, ap.toString())
        )
    }

    // ---- post / log ----

    fun addPost(type: Int, status: String?, message: String?) {
        val ts = System.currentTimeMillis()
        serviceScope.launch {
            db.postDao().addPost(ts, type, status, message)
        }
        if (type == PostEntity.TYPE_POST || type == PostEntity.TYPE_INCMG) {
            parsePacket(ts, message ?: "", type)
        } else {
            Log.d(TAG, "addPost: $status - $message")
        }
        sendBroadcast(
            Intent(UPDATE)
                .putExtra(TYPE, type)
                .putExtra(STATUS, message)
        )
    }

    fun addPost(type: Int, statusId: Int, message: String) {
        addPost(type, getString(statusId), message)
    }

    fun postAddPost(type: Int, statusId: Int, message: String) {
        if (type == PostEntity.TYPE_INFO && !prefs.getBoolean("conn_log", false)) {
            return
        }
        handler.post {
            addPost(type, statusId, message)
            if (type == PostEntity.TYPE_INCMG) {
                msgService?.sendPendingMessages()
            } else if (type == PostEntity.TYPE_ERROR) {
                stopSelf()
            }
        }
    }

    fun postSubmit(post: String) {
        Log.d(TAG, "Incoming post: $post")
        postAddPost(PostEntity.TYPE_INCMG, R.string.post_incmg, post)
        digipeaterService?.processIncomingPost(post)
        igateService?.handlePostSubmitData(post)
    }

    fun postAbort(post: String) {
        postAddPost(PostEntity.TYPE_ERROR, R.string.post_error, post)
    }

    fun postPosterStarted() {
        handler.post { onPosterStarted() }
    }

    fun postLinkOn(link: Int) {
        linkError = 0
        sendBroadcast(Intent(LINK_ON).putExtra(LINK_INFO, link))
        val message = getString(R.string.status_linkon, getString(link))
        ServiceNotifier.instance.start(this, message)
    }

    fun postLinkOff(link: Int) {
        linkError = link
        sendBroadcast(Intent(LINK_OFF).putExtra(LINK_INFO, link))
        val message = getString(R.string.status_linkoff, getString(link))
        ServiceNotifier.instance.start(this, message)
    }

    // ---- helpers ----

    fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        addPost(PostEntity.TYPE_INFO, null, msg)
    }

    private fun playNotifySound(prefKey: String) {
        try {
            val sound = prefs.getString(prefKey, "")
            if (sound.isNotEmpty()) {
                val uri = Uri.parse(sound)
                val ringtone = RingtoneManager.getRingtone(this, uri)
                ringtone?.play()
            }
        } catch (e: Exception) {
            Log.d(TAG, "playNotifySound failed: $e")
        }
    }

    companion object {
        const val PACKAGE = "org.aprsdroid.app"

        const val SERVICE = "$PACKAGE.SERVICE"
        const val SERVICE_ONCE = "$PACKAGE.ONCE"
        const val SERVICE_SEND_PACKET = "$PACKAGE.SEND_PACKET"
        const val SERVICE_FREQUENCY = "$PACKAGE.FREQUENCY"
        const val SERVICE_STOP = "$PACKAGE.SERVICE_STOP"

        const val SERVICE_STARTED = "$PACKAGE.SERVICE_STARTED"
        const val SERVICE_STOPPED = "$PACKAGE.SERVICE_STOPPED"
        const val POSITION = "$PACKAGE.POSITION"
        const val MICLEVEL = "$PACKAGE.MICLEVEL"
        const val LINK_ON = "$PACKAGE.LINK_ON"
        const val LINK_OFF = "$PACKAGE.LINK_OFF"
        const val LINK_INFO = "$PACKAGE.LINK_INFO"

        const val UPDATE = "$PACKAGE.UPDATE"
        const val MESSAGE = "$PACKAGE.MESSAGE"
        const val MESSAGETX = "$PACKAGE.MESSAGETX"

        const val API_VERSION = "api_version"
        const val CALLSIGN = "callsign"
        const val TYPE = "type"
        const val STATUS = "status"
        const val LOCATION = "location"
        const val SOURCE = "source"
        const val PACKET = "packet"
        const val DEST = "dest"
        const val BODY = "body"

        const val API_VERSION_CODE = 1

        @JvmField
        var running = false

        @JvmField
        var linkError = 0

        @JvmStatic
        fun intent(ctx: Context, action: String): Intent {
            return Intent(action, null, ctx, AprsService::class.java)
        }
    }
}

/**
 * Common interface for APRS backends (TCP, AFSK, Bluetooth, USB, ...).
 *
 * The Scala code uses an abstract class with the same shape; this
 * interface is the Kotlin equivalent. Implementations are ported in
 * Batch 4.
 */
interface AprsBackendInterface {
    val login: String

    /** Returns true if successfully started. */
    fun start(): Boolean

    /** Submit a packet to the backend; returns a status string. */
    fun update(packet: APRSPacket): String

    /** Shut down the backend. */
    fun stop()
}
