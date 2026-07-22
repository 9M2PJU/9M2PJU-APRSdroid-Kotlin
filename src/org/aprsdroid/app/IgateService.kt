package org.aprsdroid.app

import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket
import net.ab0oo.aprs.parser.MessagePacket
import net.ab0oo.aprs.parser.Parser
import net.ab0oo.aprs.parser.PositionPacket
import org.aprsdroid.app.data.PostEntity
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.net.SocketException

/**
 * Callback for connection-loss events from the TCP socket thread.
 */
interface ConnectionListener {
    fun onConnectionLost()
}

/**
 * Kotlin port of the Scala `IgateService`.
 *
 * Manages a persistent TCP connection to an APRS-IS server for
 * bidirectional igating: gate RF traffic to APRS-IS and (optionally)
 * gate APRS-IS traffic back to RF for stations heard recently.
 *
 * The actual socket I/O lives in [TcpSocketThread]; this class owns
 * lifecycle (start/stop/reconnect) and delegates outbound data to the
 * thread.
 */
class IgateService(private val service: AprsService, private val prefs: PrefsWrapper) :
    ConnectionListener {

    private val TAG = "IgateService"

    private val hostport = prefs.getString("p.igserver", "aprs.hamradio.my")
    private val host: String
    private val port: Int

    init {
        val (h, p) = parseHostPort(hostport)
        host = h
        port = p
    }

    private val soTimeout = prefs.getStringInt("p.igsotimeout", 120)
    private val connectRetryInterval = prefs.getStringInt("p.igconnectretry", 30)

    private var conn: TcpSocketThread? = null
    private var reconnecting = false

    private fun parseHostPort(hostport: String): Pair<String, Int> {
        val parts = hostport.split(":")
        return if (parts.size == 2) parts[0] to parts[1].toInt() else hostport to 14580
    }

    fun start() {
        Log.d(TAG, "start() - Starting connection to $host:$port")
        if (conn == null) {
            Log.d(TAG, "start() - No existing connection, creating new connection.")
            createConnection()
        } else {
            Log.d(TAG, "start() - Connection already exists.")
        }
    }

    private fun createConnection() {
        Log.d(TAG, "createConnection() - Connecting to $host:$port")
        conn = TcpSocketThread(host, port, soTimeout, service, prefs, this)
        Log.d(TAG, "createConnection() - TcpSocketThread created, starting thread.")
        conn!!.start()
    }

    fun stop() {
        Log.d(TAG, "stop() - Stopping connection")
        val c = conn ?: run {
            Log.d(TAG, "stop() - No connection to stop.")
            return
        }
        synchronized(c) { c.running = false }
        Log.d(TAG, "stop() - Waiting for connection thread to join.")
        c.join(50)
        c.shutdown()
        Log.d(TAG, "stop() - Connection shutdown.")
    }

    /** Gate outbound RF data to APRS-IS. */
    fun handlePostSubmitData(data: String) {
        Log.d(TAG, "handlePostSubmitData() - Received data: $data")
        val c = conn
        if (c != null) {
            Log.d(TAG, "handlePostSubmitData() - Delegating data to TcpSocketThread.")
            c.handlePostSubmitData(data)
        } else {
            Log.d(TAG, "handlePostSubmitData() - No active connection to send data.")
        }
    }

    /** Reconnect after connection loss, respecting the service_running pref. */
    fun reconnect() {
        Log.d(TAG, "reconnect() - Initiating reconnect.")

        val serviceRunning = prefs.getBoolean("service_running", false)
        if (!serviceRunning) {
            Log.d(TAG, "start() - Service is not running, skipping connection.")
            reconnecting = false
            return
        }
        if (reconnecting) {
            Log.d(TAG, "reconnect() - Already in reconnecting process, skipping.")
            return
        }
        reconnecting = true

        service.addPost(
            PostEntity.TYPE_INFO,
            "APRS-IS",
            "Connection lost... Reconnecting in $connectRetryInterval seconds",
        )

        stop()
        Thread.sleep(connectRetryInterval * 1000L)
        Log.d(TAG, "reconnect() - Attempting to create a new connection.")
        createConnection()
        reconnecting = false
    }

    override fun onConnectionLost() {
        Log.d(TAG, "onConnectionLost() - Connection lost, attempting to reconnect.")
        reconnect()
    }
}

/**
 * Kotlin port of the Scala `TcpSocketThread`.
 *
 * Background thread that holds the APRS-IS TCP socket, performs login,
 * reads incoming lines, and gates RF→APRS-IS and (optionally) APRS-IS→RF.
 *
 * Rate limiting uses two sliding windows (1 min, 5 min) to comply with
 * APRS-IS igate guidelines. The MSP (Message State Path) map tracks
 * stations that should have their reply packets gated to RF.
 */
class TcpSocketThread(
    private val host: String,
    private val port: Int,
    private val timeout: Int,
    private val service: AprsService,
    private val prefs: PrefsWrapper,
    private val listener: ConnectionListener,
) : Thread() {

    @Volatile
    var running = true

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null

    // Sliding-window rate-limit queues (timestamps of sent packets)
    private val sentPackets1Min = ArrayDeque<Long>()
    private val sentPackets5Min = ArrayDeque<Long>()
    private val mspMap = mutableMapOf<String, Int>()

    // Callsign -> last-heard timestamp (ms)
    private val lastHeardCalls = mutableMapOf<String, Long>()

    override fun run() {
        Log.d(TAG, "run() - Starting TCP connection to $host with timeout $timeout")
        service.addPost(PostEntity.TYPE_INFO, "APRS-IS", "Connecting to $host:$port")

        while (running) {
            try {
                socket = Socket(host, port)
                socket!!.soTimeout = timeout * 1000
                Log.d(TAG, "run() - Connected to $host")

                reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                writer = PrintWriter(socket!!.getOutputStream(), true)
                sendLogin()
                service.addPost(PostEntity.TYPE_INFO, "APRS-IS", "Connected to APRS-IS")

                while (running) {
                    val message = reader!!.readLine()
                    if (message != null) {
                        Log.d(TAG, "run() - Received message: $message")
                        handleMessage(message)
                        handleAprsTrafficPost(message)
                    } else {
                        Log.d(TAG, "run() - Server disconnected. Attempting to reconnect.")
                        running = false
                        listener.onConnectionLost()
                    }
                }
            } catch (e: SocketException) {
                Log.e(TAG, "run() - SocketException: ${e.message}")
                running = false
                listener.onConnectionLost()
            } catch (e: IOException) {
                Log.e(TAG, "run() - IOException: ${e.message}")
                running = false
                listener.onConnectionLost()
            } finally {
                shutdown()
            }
        }
    }

    private fun sendLogin() {
        Log.d(TAG, "sendLogin() - Sending login information to server.")
        val callsign = prefs.getCallSsid()
        val passcode = prefs.getPasscode()
        val version = "APRSdroid ${service.appVersion.filter { it.isDigit() }.takeLast(2)}"
        val filter = prefs.getString("p.igfilter", "")

        val loginMessage = "user $callsign pass $passcode vers $version\r\n"
        val filterMessage = "#filter $filter\r\n"

        Log.d(TAG, "sendLogin() - Sending login: $loginMessage")
        Log.d(TAG, "sendLogin() - Sending filter: $filterMessage")

        writer!!.println(loginMessage)
        writer!!.flush()
        Log.d(TAG, "sendLogin() - Login sent.")

        writer!!.println(filterMessage)
        writer!!.flush()
        Log.d(TAG, "sendLogin() - Filter sent.")
    }

    /**
     * Insert the q-construct before the first colon and reject
     * RFONLY/TCPIP/NOGATE packets. Returns null if the packet should
     * be dropped.
     */
    private fun modifyData(data: String): String? {
        Log.d(TAG, "modifyData() - Received data: $data")
        if (data.contains("RFONLY") || data.contains("TCPIP") || data.contains("NOGATE")) {
            Log.d(TAG, "modifyData() - RFONLY or TCPIP found: $data")
            return null
        }

        val colonIndex = data.indexOf(":")
        Log.d(TAG, "modifyData() - Colon index: $colonIndex")

        val qconstruct = if (prefs.getBoolean("p.aprsistorf", false)) "qAR" else "qAO"

        if (colonIndex != -1) {
            val modifiedData = data.substring(0, colonIndex) + ",$qconstruct," + prefs.getCallSsid() + data.substring(colonIndex)
            Log.d(TAG, "modifyData() - Modified data: $modifiedData")
            return modifiedData
        }
        Log.d(TAG, "modifyData() - No colon found, returning data as is.")
        return data
    }

    /** Gate RF data to APRS-IS (called from IgateService). */
    fun handlePostSubmitData(data: String) {
        val modifiedData = modifyData(data) ?: run {
            Log.d(TAG, "handlePostSubmitData() - Skipping data processing due to RFONLY/TCPIP in packet")
            return
        }

        val callSign = modifiedData.split(">")[0].trim()
        lastHeardCalls[callSign] = System.currentTimeMillis()
        Log.d(
            TAG,
            "handlePostSubmitData() - Extracted callsign: $callSign, updating last heard time to ${System.currentTimeMillis()} for that callsign.",
        )
        Log.d(TAG, "handlePostSubmitData() - Modified data: $modifiedData")

        val s = socket
        if (s != null && s.isConnected) {
            sendData(modifiedData)
            Log.d(TAG, "handlePostSubmitData() - Data sent to server.")
            service.addPost(PostEntity.TYPE_IG, "APRS-IS Sent", modifiedData)
        } else {
            Log.e(TAG, "handlePostSubmitData() - No active connection to send data.")
        }
    }

    private fun sendData(data: String) {
        Log.d(TAG, "sendData() - Sending data: $data")
        val w = writer
        if (w != null) {
            w.println(data)
            w.flush()
        } else {
            Log.e(TAG, "sendData() - Writer is null, cannot send data.")
        }
    }

    fun shutdown() {
        socket?.let { s ->
            try {
                s.close()
            } catch (e: IOException) {
                Log.e(TAG, "shutdown() - Error closing socket: ${e.message}")
            }
        }
    }

    private fun handleAprsTrafficPost(message: String) {
        val aprsIsTrafficEnabled = prefs.getBoolean("p.aprsistraffic", false)
        if (!aprsIsTrafficEnabled) {
            if (message.startsWith("#")) {
                service.addPost(PostEntity.TYPE_INFO, "APRS-IS", message)
                Log.d(TAG, "APRS-IS traffic enabled, post added: $message")
            } else {
                service.addPost(PostEntity.TYPE_IG, "APRS-IS Received", message)
                Log.d(TAG, "APRS-IS traffic enabled, post added: $message")
            }
        } else {
            Log.d(TAG, "APRS-IS traffic disabled, skipping the post.")
        }
    }

    /**
     * Extract the targeted callsign from a message payload, or null
     * if the payload is not a message or should be ignored (telemetry,
     * bulletins, NWS/SKY/CWA/BOM).
     */
    private fun processMessage(payloadString: String): String? {
        if (!payloadString.startsWith(":")) return null
        if (payloadString.length < 11) return null
        if (payloadString.length >= 16) {
            val sub = payloadString.substring(10)
            if (sub.startsWith(":PARM.") || sub.startsWith(":UNIT.") ||
                sub.startsWith(":EQNS.") || sub.startsWith(":BITS.")
            ) return null
        }
        if (payloadString.length >= 4) {
            val prefix = payloadString.substring(1, 4)
            if (prefix == "BLN" || prefix == "NWS" || prefix == "SKY" ||
                prefix == "CWA" || prefix == "BOM"
            ) return null
        }
        return payloadString.removePrefix(":").takeWhile { it != ':' }.replace("\\s".toRegex(), "")
    }

    /** Gate an APRS-IS position packet to RF if MSP entry exists. */
    private fun processPacketPosition(fap: APRSPacket): String? {
        return try {
            val callssid = prefs.getCallSsid()
            val sourceCall = fap.sourceCall
            val destinationCall = fap.destinationCall
            val payload = fap.aprsInformation
            val payloadString = payload?.toString() ?: ""
            val digipath = prefs.getString("igpath", "WIDE1-1")
            val formattedDigipath = if (digipath.isNotEmpty()) ",$digipath" else ""
            val version = service.appVersion

            if (mspMap[sourceCall] == 1) {
                Log.d(TAG, "MSP entry found and is 1 for $sourceCall, pass packet")
                mspMap.remove(sourceCall)

                val igatedPacket = "$callssid>$version$formattedDigipath:}$sourceCall>$destinationCall,TCPIP,$callssid*:$payload"
                Log.d(TAG, "Processed packet: $igatedPacket")

                if (checkRateLimit()) {
                    Log.d(TAG, "Rate limit exceeded, skipping this packet.")
                    return null
                }
                handleRateLimiting()
                igatedPacket
            } else {
                Log.d(TAG, "Station not MSP, skipping processing.")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "processPacketPostion() - Error processing packet", e)
            null
        }
    }

    /** Gate an APRS-IS message packet to RF if target was heard recently. */
    private fun processPacketMessage(fap: APRSPacket): String? {
        val timeLastHeard = prefs.getStringInt("p.timelastheard", 30)
        return try {
            val callssid = prefs.getCallSsid()
            val sourceCall = fap.sourceCall
            val destinationCall = fap.destinationCall
            val payload = fap.aprsInformation
            val payloadString = payload?.toString() ?: ""
            val digipath = prefs.getString("igpath", "WIDE1-1")
            val formattedDigipath = if (digipath.isNotEmpty()) ",$digipath" else ""
            val version = service.appVersion

            val targetedCallsign = processMessage(payloadString)
            Log.d(TAG, "Targeted Callsign: $targetedCallsign")

            if (targetedCallsign == null) {
                Log.d(TAG, "Target station not found or not a message packet, skipping packet processing.")
                return null
            }

            val currentTime = System.currentTimeMillis()
            val lastHeardTime = lastHeardCalls[targetedCallsign] ?: 0L
            val timeElapsed = currentTime - lastHeardTime
            Log.d(TAG, "processPacketMessage() - $targetedCallsign, last heard at $lastHeardTime, time elapsed: $timeElapsed ms.")

            if (timeElapsed <= timeLastHeard * 60 * 1000L) {
                mspMap.putIfAbsent(sourceCall, 1)
                Log.d(TAG, "MSP set to 1 for $sourceCall")

                val igatedPacket = "$callssid>$version$formattedDigipath:}$sourceCall>$destinationCall,TCPIP,$callssid*:$payload"
                Log.d(TAG, "Processed packet: $igatedPacket")

                if (checkRateLimit()) {
                    Log.d(TAG, "Rate limit exceeded, skipping this packet.")
                    return null
                }
                handleRateLimiting()
                igatedPacket
            } else {
                Log.d(TAG, "Station not heard recently, skipping processing.")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "processPacketMessage() - Error processing packet", e)
            null
        }
    }

    /** Add current timestamp to both rate-limit queues and trim to limits. */
    private fun handleRateLimiting() {
        val currentTime = System.currentTimeMillis()
        Log.d(TAG, "Adding current time to queues: $currentTime")
        sentPackets1Min.addLast(currentTime)
        sentPackets5Min.addLast(currentTime)

        val limit1Min = prefs.getStringInt("p.ratelimit1", 6)
        val limit5Min = prefs.getStringInt("p.ratelimit5", 10)
        if (sentPackets1Min.size > limit1Min) sentPackets1Min.removeFirst()
        if (sentPackets5Min.size > limit5Min) sentPackets5Min.removeFirst()

        Log.d(TAG, "sentPackets1Min size after enqueue: ${sentPackets1Min.size}")
        Log.d(TAG, "sentPackets5Min size after enqueue: ${sentPackets5Min.size}")
    }

    /** Returns true if the rate limit is exceeded (packet should be dropped). */
    private fun checkRateLimit(): Boolean {
        val currentTime = System.currentTimeMillis()
        Log.d(TAG, "Current time: $currentTime")

        sentPackets1Min.removeAll { currentTime - it > 60_000 }
        Log.d(TAG, "sentPackets1Min size after cleanup: ${sentPackets1Min.size}")

        sentPackets5Min.removeAll { currentTime - it > 300_000 }
        Log.d(TAG, "sentPackets5Min size after cleanup: ${sentPackets5Min.size}")

        val limit1Min = prefs.getStringInt("p.ratelimit1", 6)
        val limit5Min = prefs.getStringInt("p.ratelimit5", 10)

        if (sentPackets1Min.size >= limit1Min) {
            Log.w(TAG, "Packet limit exceeded for 1 minute interval. Dropping packet.")
            service.addPost(PostEntity.TYPE_ERROR, "APRS-IS > RF", "Packet limit exceeded for 1 minute. Packet dropped.")
            Log.d(TAG, "Rate limit exceeded for 1 minute. Returning true.")
            return true
        }
        if (sentPackets5Min.size >= limit5Min) {
            Log.w(TAG, "Packet limit exceeded for 5 minute interval. Dropping packet.")
            service.addPost(PostEntity.TYPE_ERROR, "APRS-IS > RF", "Packet limit exceeded for 5 minutes. Packet dropped.")
            Log.d(TAG, "Rate limit exceeded for 5 minutes. Returning true.")
            return true
        }
        Log.d(TAG, "No rate limit exceeded. Returning false.")
        return false
    }

    /**
     * Parse an incoming APRS-IS line and, if bidirectional igating is
     * enabled, gate messages and positions to RF via
     * [AprsService.sendThirdPartyPacket].
     */
    private fun handleMessage(message: String) {
        if (message.startsWith("#")) {
            Log.d(TAG, "Message starts with '#', skipping processing.")
            return
        }
        Log.d(TAG, "handleMessage() - Handling incoming message: $message")

        val bidirectionalGate = prefs.getBoolean("p.aprsistorf", false)
        if (!bidirectionalGate) {
            Log.d(TAG, "Bidirectional IGate disabled.")
            return
        }

        try {
            val fap = Parser.parse(message)
            Log.d(TAG, "Packet type: ${fap.aprsInformation?.javaClass?.simpleName}")

            when (val info = fap.aprsInformation) {
                is MessagePacket -> {
                    try {
                        val igatedPacket = processPacketMessage(fap)
                        if (igatedPacket != null) {
                            Log.d(TAG, "Sending igated packet: $igatedPacket")
                            service.sendThirdPartyPacket(igatedPacket)
                        } else {
                            Log.d(TAG, "Packet not processed, skipping send.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing MessagePacket: ${e.message}")
                    }
                }
                is PositionPacket -> {
                    try {
                        val igatedPacket = processPacketPosition(fap)
                        if (igatedPacket != null) {
                            Log.d(TAG, "Sending igated packet: $igatedPacket")
                            service.sendThirdPartyPacket(igatedPacket)
                        } else {
                            Log.d(TAG, "Packet not processed, skipping send.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing PositionPacket: ${e.message}")
                    }
                }
                else -> {
                    Log.d(TAG, "handleMessage() - Not a MessagePacket or PositionPacket, skipping processing.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleMessage() - Failed to parse packet: $message", e)
        }
    }

    companion object {
        private const val TAG = "IgateService"
    }
}
