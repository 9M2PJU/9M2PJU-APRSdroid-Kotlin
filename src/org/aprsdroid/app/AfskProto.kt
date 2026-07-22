package org.aprsdroid.app

import android.util.Log
import net.ab0oo.aprs.parser.APRSPacket
import java.io.InputStream
import java.io.OutputStream

/**
 * Kotlin port of the Scala `AfskProto`.
 *
 * Stub AFSK protocol — actual AFSK modulation / demodulation is handled
 * by the native PacketDroid JNI code in the AfskUploader, not here.
 */
class AfskProto(service: AprsService, input: InputStream, output: OutputStream) :
    TncProto(input, output) {

    private val TAG = "APRSdroid.AfskProto"

    override fun readPacket(): String {
        return ""
    }

    override fun writePacket(p: APRSPacket) {
        Log.d(TAG, "writePacket: $p")
    }
}
