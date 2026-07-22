package org.aprsdroid.app

import net.ab0oo.aprs.parser.APRSPacket
import java.io.InputStream
import java.io.OutputStream

/**
 * Kotlin port of the Scala `TncProto` abstract class.
 *
 * Wraps a TNC (Terminal Node Controller) input/output stream pair with
 * frame-level read/write helpers. Subclasses implement the actual framing
 * (TNC2 line-based, KISS frame-based, AFSK, Kenwood, ...).
 */
abstract class TncProto(protected val input: InputStream, protected val output: OutputStream) {

    abstract fun readPacket(): String

    abstract fun writePacket(p: APRSPacket)

    open fun writeReturn() {
        val frame = byteArrayOf(
            KissConstants.FEND.toByte(),
            KissConstants.CONTROL_COMMAND.toByte(),
            KissConstants.RETURN.toByte(),
            KissConstants.FEND.toByte(),
        )
        output.write(frame)
        output.flush()
    }

    open fun stop() {}
}

object KissConstants {
    const val FEND = 0xC0
    const val CONTROL_COMMAND = 0x06
    const val RETURN = 0xEB
}
