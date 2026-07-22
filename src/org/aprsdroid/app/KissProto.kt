package org.aprsdroid.app

import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket
import net.ab0oo.aprs.parser.Parser
import org.aprsdroid.app.data.PostEntity
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder

/**
 * Kotlin port of the Scala `KissProto`.
 *
 * KISS framing protocol used by Bluetooth / USB / TCP TNCs.
 */
class KissProto(private val service: AprsService, input: InputStream, output: OutputStream) :
    TncProto(input, output) {

    private val TAG = "APRSdroid.KissProto"

    private object Kiss {
        const val FEND = 0xC0
        const val FESC = 0xDB
        const val TFEND = 0xDC
        const val TFESC = 0xDD
        const val CMD_DATA = 0x00
        const val CONTROL_COMMAND = 0x06
        const val FREQ = 0xEA
        const val RETURN = 0xEB
    }

    init {
        val initstring = URLDecoder.decode(service.prefs.getString("kiss.init", ""), "UTF-8")
        val initdelay = service.prefs.getStringInt("kiss.delay", 300)
        if (initstring.isNotEmpty()) {
            for (line in initstring.split("\n")) {
                service.postAddPost(PostEntity.TYPE_TX, R.string.p_tnc_init, line)
                output.write(line.toByteArray())
                output.write('\r'.code)
                output.write('\n'.code)
                Thread.sleep(initdelay.toLong())
            }
        }

        val checkprefs = service.prefs.getBackendName()
        Log.d(TAG, "Backend Name1: $checkprefs")

        if (service.prefs.getBoolean("freq_control", false) &&
            service.prefs.getBackendName().contains("Bluetooth")
        ) {
            Log.d(TAG, "Frequency control is enabled.")
            val freqMHZ = service.prefs.getStringFloat("frequency_control_value", 144.390f)
            Log.d(TAG, "Frequency control value fetched: $freqMHZ MHz")
            val freqBytes = freqConvert(freqMHZ)
            writeFreq(freqBytes)
            Log.d(
                TAG,
                "Frequency in bytes (MSB first): ${freqBytes.joinToString(" ") { "0x%02X".format(it) }}",
            )
        }

        if (service.prefs.getCallsign().length > 6) {
            throw IllegalArgumentException(service.getString(R.string.e_toolong_callsign))
        }
    }

    private fun freqConvert(freqMHz: Float): ByteArray {
        val freqHz = (freqMHz * 1_000_000).toLong()
        val bytes = byteArrayOf(
            (freqHz shr 24).toByte(),
            (freqHz shr 16).toByte(),
            (freqHz shr 8).toByte(),
            freqHz.toByte(),
        )
        return bytes.flatMap { byte ->
            if (byte == Kiss.FEND.toByte()) {
                listOf(Kiss.FESC.toByte(), Kiss.TFEND.toByte())
            } else {
                listOf(byte)
            }
        }.toByteArray()
    }

    override fun readPacket(): String {
        val buf = mutableListOf<Byte>()
        while (true) {
            val ch = input.read()
            if (ch >= 0) {
                Log.d(TAG, "readPacket: %02X '%c'".format(ch, ch))
            }
            when (ch) {
                Kiss.FEND -> {
                    if (buf.isNotEmpty()) {
                        Log.d(TAG, "readPacket: sending back ${String(buf.toByteArray())}")
                        try {
                            return Parser.parseAX25(buf.toByteArray()).toString().trim()
                        } catch (e: Exception) {
                            buf.clear()
                        }
                    }
                }
                Kiss.FESC -> {
                    when (input.read()) {
                        Kiss.TFEND -> buf.add(Kiss.FEND.toByte())
                        Kiss.TFESC -> buf.add(Kiss.FESC.toByte())
                    }
                }
                -1 -> throw java.io.IOException("KissReader out of data")
                0 -> {
                    // hack: ignore 0x00 byte at start of frame, this is the command
                    if (buf.isNotEmpty()) {
                        buf.add(ch.toByte())
                    } else {
                        Log.d(TAG, "readPacket: ignoring command byte")
                    }
                }
                10 -> {
                    // heuristic for ASCII strings
                    if (buf.size > 1 && (buf[0] > 0) && buf[buf.size - 1] == 13.toByte()) {
                        return String(buf.toByteArray()).trim()
                    }
                }
                else -> buf.add(ch.toByte())
            }
        }
    }

    override fun writePacket(p: APRSPacket) {
        Log.d(TAG, "writePacket: $p")
        val combinedData = byteArrayOf(Kiss.FEND.toByte(), Kiss.CMD_DATA.toByte()) +
            p.toAX25Frame() + byteArrayOf(Kiss.FEND.toByte())
        output.write(combinedData)
        output.flush()
    }

    fun writeFreq(freqBytes: ByteArray) {
        val frame = byteArrayOf(
            Kiss.FEND.toByte(),
            Kiss.CONTROL_COMMAND.toByte(),
            Kiss.FREQ.toByte(),
        ) + freqBytes + byteArrayOf(Kiss.FEND.toByte())
        output.write(frame)
        output.flush()
    }
}
