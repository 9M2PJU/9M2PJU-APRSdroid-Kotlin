package org.aprsdroid.app

import android.Manifest
import android.os.Build
import android.util.Log
import java.io.InputStream
import java.io.OutputStream

/**
 * Kotlin port of the Scala `AprsBackend` companion object.
 *
 * "Modular" system to connect to an APRS backend. The backend config
 * consists of three items backed by prefs values:
 *   - *proto* inside the connection ("aprsis", "afsk", "kiss", "tnc2", "kenwood")
 *   - *link* type ("bluetooth", "usb", "tcpip"; only for "kiss", "tnc2", "kenwood")
 *   - *aprsis* mode ("tcp", "http", "udp"; only for proto=aprsis)
 */
object AprsBackend {

    const val TAG = "AprsBackend"
    const val DEFAULT_CONNTYPE = "tcp"
    const val DEFAULT_LINK = "tcpip"
    const val DEFAULT_PROTO = "aprsis"

    const val PASSCODE_NONE = 0
    const val PASSCODE_OPTIONAL = 1
    const val PASSCODE_REQUIRED = 2

    const val CAN_RECEIVE = 1
    const val CAN_XMIT = 2
    const val CAN_DUPLEX = 3

    class BackendInfo(
        val create: (AprsService, PrefsWrapper) -> AprsBackendInterface,
        val prefxml: Int,
        val permissions: Set<String>,
        val duplex: Int,
        val needPasscode: Int,
    )

    class ProtoInfo(
        val create: (AprsService, InputStream, OutputStream) -> TncProto,
        val prefxml: Int,
        val link: String,
    )

    val BLUETOOTH_PERMISSION: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH_ADMIN
        }

    // Map from old "backend" to new proto-link-aprsis (defaults are bluetooth and tcp)
    val backendUpgrade = mapOf(
        "tcp" to "aprsis-tcpip-tcp",
        "udp" to "aprsis-tcpip-udp",
        "http" to "aprsis-tcpip-http",
        "afsk" to "afsk-bluetooth-tcp",
        "bluetooth" to "kiss-bluetooth-tcp",
        "kenwood" to "kenwood-bluetooth-tcp",
        "tcptnc" to "kiss-tcpip-tcp",
        "usb" to "kiss-usb-tcp",
    )

    // BackendInfo collection
    val backendCollection = linkedMapOf(
        "udp" to BackendInfo(
            { _, p -> UdpUploader(p) },
            R.xml.backend_udp,
            emptySet(),
            CAN_XMIT,
            PASSCODE_REQUIRED,
        ),
        "http" to BackendInfo(
            { _, p -> HttpPostUploader(p) },
            R.xml.backend_http,
            emptySet(),
            CAN_XMIT,
            PASSCODE_REQUIRED,
        ),
        "vox" to BackendInfo(
            { s, p -> AfskUploader(s, p) },
            0,
            setOf(Manifest.permission.RECORD_AUDIO),
            CAN_DUPLEX,
            PASSCODE_NONE,
        ),
        "tcp" to BackendInfo(
            { s, p -> TcpUploader(s, p) },
            R.xml.backend_tcp,
            emptySet(),
            CAN_DUPLEX,
            PASSCODE_OPTIONAL,
        ),
        "bluetooth" to BackendInfo(
            { s, p -> BluetoothTnc(s, p) },
            R.xml.backend_bluetooth,
            setOf(BLUETOOTH_PERMISSION),
            CAN_DUPLEX,
            PASSCODE_NONE,
        ),
        "ble" to BackendInfo(
            { s, p -> BluetoothLETnc(s, p) },
            R.xml.backend_ble,
            setOf(BLUETOOTH_PERMISSION),
            CAN_DUPLEX,
            PASSCODE_NONE,
        ),
        "tcpip" to BackendInfo(
            { s, p -> TcpUploader(s, p) },
            R.xml.backend_tcptnc,
            emptySet(),
            CAN_DUPLEX,
            PASSCODE_NONE,
        ),
        "usb" to BackendInfo(
            { s, p -> UsbTnc(s, p) },
            R.xml.backend_usb,
            emptySet(),
            CAN_DUPLEX,
            PASSCODE_NONE,
        ),
        "digirig" to BackendInfo(
            { s, p -> DigiRig(s, p) },
            R.xml.backend_digirig,
            setOf(Manifest.permission.RECORD_AUDIO),
            CAN_DUPLEX,
            PASSCODE_NONE,
        ),
    )

    val protoCollection = linkedMapOf(
        "aprsis" to ProtoInfo(
            { s, i, o -> AprsIsProto(s, i, o) },
            R.xml.proto_aprsis, "aprsis",
        ),
        "afsk" to ProtoInfo(
            { s, i, o -> AfskProto(s, i, o) },
            R.xml.proto_afsk, "afsk",
        ),
        "kiss" to ProtoInfo(
            { s, i, o -> KissProto(s, i, o) },
            R.xml.proto_kiss, "link",
        ),
        "tnc2" to ProtoInfo(
            { _, i, o -> Tnc2Proto(i, o) },
            R.xml.proto_tnc2, "link",
        ),
        "kenwood" to ProtoInfo(
            { s, i, o -> KenwoodProto(s, i, o) },
            R.xml.proto_kenwood, "link",
        ),
    )

    @JvmStatic
    fun defaultProtoInfo(p: String): ProtoInfo =
        protoCollection[p] ?: protoCollection.getValue("aprsis")

    @JvmStatic
    fun defaultProtoInfo(prefs: PrefsWrapper): ProtoInfo = defaultProtoInfo(prefs.getProto())

    @JvmStatic
    fun defaultBackendInfo(prefs: PrefsWrapper): BackendInfo {
        val pi = defaultProtoInfo(prefs)
        val link = if (pi.link.isNotEmpty()) {
            prefs.getString(pi.link, DEFAULT_LINK)
        } else {
            prefs.getProto()
        }
        Log.d(TAG, "DEBUG: pi.link (${pi.link}) : $link")
        return backendCollection[link] ?: backendCollection.getValue(DEFAULT_CONNTYPE)
    }

    @JvmStatic
    fun defaultBackendPermissions(prefs: PrefsWrapper): Set<String> {
        val perms = mutableSetOf<String>()
        perms += defaultBackendInfo(prefs).permissions
        if (prefs.getProto() == "kenwood" && prefs.getBoolean("kenwood.gps", false)) {
            perms += Manifest.permission.ACCESS_FINE_LOCATION
            perms += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        return perms.toSet()
    }

    @JvmStatic
    fun instanciateUploader(service: AprsService, prefs: PrefsWrapper): AprsBackendInterface =
        defaultBackendInfo(prefs).create(service, prefs)

    @JvmStatic
    fun instanciateProto(service: AprsService, inputStream: InputStream, outputStream: OutputStream): TncProto =
        defaultProtoInfo(service.prefs).create(service, inputStream, outputStream)

    @JvmStatic
    fun prefxmlProto(prefs: PrefsWrapper): Int = defaultProtoInfo(prefs).prefxml

    @JvmStatic
    fun prefxmlBackend(prefs: PrefsWrapper): Int = defaultBackendInfo(prefs).prefxml
}
