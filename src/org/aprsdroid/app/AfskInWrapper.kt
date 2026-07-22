package org.aprsdroid.app

import com.jazzido.PacketDroid.AudioBufferProcessor

/**
 * Kotlin port of the Scala `AfskInWrapper`.
 *
 * Wraps the AFSK input demodulator, choosing between the high-quality
 * software demodulator (AfskDemodulator, using jsoundmodem) and the
 * native JNI demodulator (AudioBufferProcessor, using multimon).
 */
class AfskInWrapper(
    hq: Boolean,
    au: AfskUploader,
    inType: Int,
    samplerate: Int,
) {
    private val abp: AudioBufferProcessor? = if (!hq) AudioBufferProcessor(au) else null
    private val ad: AfskDemodulator? = if (hq) AfskDemodulator(au, inType, samplerate) else null

    fun start() {
        abp?.start()
        ad?.start()
    }

    fun close() {
        abp?.stopRecording()
        ad?.close()
    }
}
