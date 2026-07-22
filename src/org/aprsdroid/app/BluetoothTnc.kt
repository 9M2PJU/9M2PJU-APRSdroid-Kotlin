package org.aprsdroid.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket
import java.io.IOException
import java.util.UUID

/**
 * Kotlin port of the Scala `BluetoothTnc`.
 *
 * Connects to a KISS-compatible TNC over Bluetooth SPP (Serial Port
 * Profile). Reuses the [KissProto] for framing.
 */
class BluetoothTnc(private val service: AprsService, prefs: PrefsWrapper) :
    AprsBackendAbstract(prefs) {

    private val TAG = "APRSdroid.BluetoothTnc"
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private var conn: Thread? = null

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            (service.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    override fun start(): Boolean {
        val addr = prefs.getString("bluetooth.target", "")
        if (addr.isEmpty()) {
            service.postAbort(service.getString(R.string.bt_error_no_tnc))
            return false
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            service.postAbort(service.getString(R.string.bt_error_disabled))
            return false
        }
        val device = adapter.getRemoteDevice(addr) ?: run {
            service.postAbort(service.getString(R.string.bt_error_no_tnc))
            return false
        }
        conn = Thread { connectAndRun(device) }.also { it.start() }
        return false
    }

    private fun connectAndRun(device: BluetoothDevice) {
        try {
            val s = device.createRfcommSocketToServiceRecord(uuid)
            s.connect()
            socket = s
            val tnc = KissProto(service, s.inputStream, s.outputStream)
            service.postLinkOn(R.string.p_link_bt)
            service.postPosterStarted()
            while (Thread.currentThread() == conn && s.isConnected) {
                val line = tnc.readPacket()
                if (line.isEmpty()) break
                Log.d(TAG, "recv: $line")
                service.postSubmit(line)
            }
        } catch (e: IOException) {
            Log.d(TAG, "Bluetooth IOException: $e")
            service.postLinkOff(R.string.p_link_bt)
            service.postAbort(e.toString())
        }
    }

    override fun update(packet: APRSPacket): String {
        val s = socket ?: return "BT disconnected"
        return if (s.isConnected) {
            // Reuse a single KissProto instance for writes
            val tnc = KissProto(service, s.inputStream, s.outputStream)
            tnc.writePacket(packet)
            "BT OK"
        } else {
            "BT disconnected"
        }
    }

    override fun stop() {
        conn = null
        try {
            socket?.close()
        } catch (_: IOException) {}
    }
}
