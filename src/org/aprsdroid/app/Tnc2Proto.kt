package org.aprsdroid.app

import net.ab0oo.aprs.parser.APRSPacket
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter

/**
 * Kotlin port of the Scala `Tnc2Proto`.
 *
 * Line-based TNC2 protocol — packets are exchanged as plain text lines
 * terminated by CR/LF. Used as the base for the APRS-IS protocol.
 */
open class Tnc2Proto(input: InputStream, output: OutputStream) : TncProto(input, output) {

    val reader: BufferedReader = BufferedReader(InputStreamReader(input), 256)
    val writer: PrintWriter = PrintWriter(OutputStreamWriter(output), true)

    override fun readPacket(): String = reader.readLine() ?: ""

    override fun writePacket(p: APRSPacket) {
        writer.println(p)
    }
}
