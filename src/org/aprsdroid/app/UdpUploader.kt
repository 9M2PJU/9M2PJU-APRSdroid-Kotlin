package org.aprsdroid.app

import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Kotlin port of the Scala `UdpUploader`.
 *
 * Sends APRS packets to an APRS-IS server over UDP. Receive-only is not
 * possible with UDP, so this backend is transmit-only.
 */
class UdpUploader(prefs: PrefsWrapper) : AprsBackendAbstract(prefs) {

    private val TAG = "APRSdroid.Udp"
    private val socket: DatagramSocket by lazy { DatagramSocket() }
    private val host: String = prefs.getString("udp.server", "srvr.aprs-is.net")

    override fun start(): Boolean = true

    override fun update(packet: APRSPacket): String {
        val (h, port) = AprsPacket.parseHostPort(host, 8080)
        val addr = InetAddress.getByName(h)
        val pbytes = "$login\r\n$packet\r\n".toByteArray()
        socket.send(DatagramPacket(pbytes, pbytes.size, addr, port))
        Log.d(TAG, "update(): sent '$packet' to $host")
        return "UDP OK"
    }

    override fun stop() {}
}
