package org.aprsdroid.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.os.Process
import android.util.Log
import sivantoledo.ax25.Afsk1200Demodulator

/**
 * Kotlin port of the Scala `AfskDemodulator`.
 *
 * High-quality AFSK demodulator thread using the
 * `Afsk1200Demodulator` from jsoundmodem.jar. Reads audio from the
 * microphone and feeds normalized float samples to the demodulator.
 */
class AfskDemodulator(
    private val au: AfskUploader,
    private val inType: Int,
    private val samplerate: Int,
) : Thread("AFSK demodulator") {

    private val bufSize = 8192
    private val bufferS = ShortArray(bufSize)
    private val bufferF = FloatArray(bufSize)

    private val demod = Afsk1200Demodulator(samplerate, 1, 6, au)
    private var recorder: AudioRecord? = null

    init {
        // We process incoming audio
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
    }

    override fun run() {
        Log.d(TAG, "running...")
        try {
            var zeroReads = 0
            recorder = AudioRecord(
                inType, samplerate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                4 * bufSize,
            )
            recorder?.startRecording()
            while (!isInterrupted && recorder?.recordingState != AudioRecord.RECORDSTATE_STOPPED) {
                val count = recorder?.read(bufferS, 0, bufSize) ?: -1
                Log.d(TAG, "read $count samples")
                if (count == 0) {
                    zeroReads++
                    if (zeroReads == 10) {
                        throw RuntimeException("recorder.read() not delivering data!")
                    }
                } else if (count < 0) {
                    throw RuntimeException("recorder.read() = $count")
                } else {
                    zeroReads = 0
                }

                for (i in 0 until count) {
                    bufferF[i] = bufferS[i].toFloat() / 32768.0f
                }

                demod.addSamples(bufferF, count)
                au.notifyMicLevel(demod.peak())
            }
        } catch (e: Exception) {
            Log.e(TAG, "run(): $e")
            e.printStackTrace()
            au.postAbort(e.toString())
        }
        Log.d(TAG, "closed.")
    }

    fun close() {
        try {
            interrupt()
            recorder?.stop()
            join(50)
            recorder?.release()
        } catch (_: IllegalStateException) {
            Log.w(TAG, "close(): IllegalStateException")
        } catch (_: NullPointerException) {
            // no recorder yet, ignore.
        }
    }

    companion object {
        private const val TAG = "APRSdroid.AfskDemod"
    }
}
