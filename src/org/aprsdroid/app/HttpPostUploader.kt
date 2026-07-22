package org.aprsdroid.app

import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Kotlin port of the Scala `HttpPostUploader`.
 *
 * Sends APRS packets to an APRS-IS server over HTTP POST.
 * The original Scala code used the deprecated Apache `DefaultHttpClient`;
 * this Kotlin port uses `HttpURLConnection` (Android's recommended HTTP
 * client since API 8).
 */
class HttpPostUploader(prefs: PrefsWrapper) : AprsBackendAbstract(prefs) {

    private val TAG = "APRSdroid.HttpPost"
    private val host: String = prefs.getString("http.server", "srvr.aprs-is.net")

    override fun start(): Boolean = true

    private fun doPost(urlString: String, content: String): String {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("Accept-Type", "text/plain")
        }
        conn.useCaches = false
        try {
            OutputStreamWriter(conn.outputStream).use { it.write(content) }
            val status = conn.responseMessage
            Log.d(TAG, "doPost(): $status")
            return "HTTP $status"
        } finally {
            conn.disconnect()
        }
    }

    override fun update(packet: APRSPacket): String {
        var hostname = host
        if (hostname.indexOf(":") == -1) {
            hostname = "http://$hostname:8080/"
        }
        return doPost(hostname, "$login\r\n$packet\r\n")
    }

    override fun stop() {}
}
