package org.aprsdroid.app

import android.content.Context
import android.location.GpsStatus
import android.location.LocationListener
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket
import org.aprsdroid.app.data.PostEntity
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Kotlin port of the Scala `KenwoodProto`.
 *
 * Kenwood TNC protocol — reads NMEA WPL sentences from the radio and
 * forwards GPS NMEA sentences to it when `kenwood.gps` is enabled.
 */
class KenwoodProto(private val service: AprsService, input: InputStream, output: OutputStream) :
    TncProto(input, NULL_OUTPUT) {

    private val TAG = "APRSdroid.KenwoodProto"
    private val br = BufferedReader(InputStreamReader(input))
    private val sinkhole = LocationSinkhole()
    private val locMan = service.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val writerOut = OutputStreamWriter(output)

    private var listenerR5: NmeaListenerR5? = null
    private var listenerR24: NmeaListenerR24? = null

    init {
        if (service.prefs.getBoolean("kenwood.gps", false)) {
            Handler(Looper.getMainLooper()).post {
                @Suppress("DEPRECATION")
                locMan.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, sinkhole)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    listenerR5 = NmeaListenerR5()
                    @Suppress("DEPRECATION")
                    val l5: GpsStatus.NmeaListener = listenerR5!!
                    locMan.addNmeaListener(l5)
                } else {
                    listenerR24 = NmeaListenerR24()
                    val l24: OnNmeaMessageListener = listenerR24!!
                    locMan.addNmeaListener(l24)
                }
            }
        }
    }

    private fun wpl2aprs(line: String): String {
        val s = line.split("[,*]".toRegex())
        return when (s[0]) {
            "\$PKWDWPL" -> {
                val lat = "${s[3]}${s[4]}"
                val lon = "${s[5]}${s[6]}"
                val call = s[11].trim()
                val sym = s[12]
                "%s>APRS:!%s%s%s%s".format(call, lat, sym[0], lon, sym[1])
            }
            "\$GPWPL" -> {
                val lat = "${s[1]}${s[2]}"
                val lon = "${s[3]}${s[4]}"
                val call = s[5].trim()
                "%s>APRS:!%s/%s/".format(call, lat, lon)
            }
            else -> line.replaceFirst("^(cmd:)+".toRegex(), "")
        }
    }

    // Solution for #141 - yaesu FTM-400XDR packet monitor
    private fun yaesu2aprs(line1: String, line2: String): String {
        Log.d(TAG, "line1: $line1")
        Log.d(TAG, "line2: $line2")
        return line1.replaceFirst(" \\[[0-9/: ]+\\] <UI ?[A-Z]?>:$".toRegex(), ":") + line2
    }

    override fun readPacket(): String {
        var line = br.readLine()
        while (line.isNullOrEmpty()) {
            line = br.readLine()
        }
        if (line.contains("] <UI") && line.endsWith(">:")) {
            return yaesu2aprs(line, br.readLine() ?: "")
        }
        Log.d(TAG, "got $line")
        return wpl2aprs(line)
    }

    override fun writePacket(p: APRSPacket) {
        // don't do anything. yet.
    }

    private fun onNmeaReceived(timestamp: Long, nmea: String) {
        if (nmea.startsWith("\$GPGGA") || nmea.startsWith("\$GPRMC")) {
            Log.d(TAG, "NMEA >>> $nmea")
            try {
                Thread {
                    writerOut.write(nmea)
                    writerOut.flush()
                }.start()
                if (service.prefs.getBoolean("kenwood.gps_debug", false)) {
                    service.postAddPost(PostEntity.TYPE_TX, R.string.p_conn_kwd, nmea.trim())
                }
            } catch (e: Exception) {
                Log.e(TAG, "error sending NMEA to Kenwood: $e")
                e.printStackTrace()
            }
        } else {
            Log.d(TAG, "NMEA --- $nmea")
        }
    }

    inner class NmeaListenerR5 : GpsStatus.NmeaListener {
        override fun onNmeaReceived(timestamp: Long, nmea: String) {
            this@KenwoodProto.onNmeaReceived(timestamp, nmea)
        }
    }

    inner class NmeaListenerR24 : OnNmeaMessageListener {
        override fun onNmeaMessage(nmea: String, timestamp: Long) {
            this@KenwoodProto.onNmeaReceived(timestamp, nmea)
        }
    }

    inner class LocationSinkhole : LocationListener {
        override fun onLocationChanged(location: android.location.Location) {}
        override fun onProviderDisabled(provider: String) {}
        override fun onProviderEnabled(provider: String) {}
        @Deprecated("deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun stop() {
        locMan.removeUpdates(sinkhole)
        listenerR5?.let { locMan.removeNmeaListener(it) }
        listenerR24?.let { locMan.removeNmeaListener(it) }
        super.stop()
    }

    companion object {
        private val NULL_OUTPUT: OutputStream = object : OutputStream() {
            override fun write(b: Int) {}
        }
    }
}
