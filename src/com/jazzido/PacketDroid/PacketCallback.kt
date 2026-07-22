package com.jazzido.PacketDroid

/**
 * Kotlin port of the Java `PacketCallback` interface.
 *
 * Callback interface for receiving decoded packets and audio peak
 * levels from the AFSK demodulator.
 */
interface PacketCallback {
    fun received(packet: ByteArray)
    fun peak(peakValue: Short)
}
