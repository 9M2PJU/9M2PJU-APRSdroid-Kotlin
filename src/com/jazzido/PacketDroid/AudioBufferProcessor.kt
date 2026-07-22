package com.jazzido.PacketDroid

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder.AudioSource
import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.LinkedBlockingQueue

/**
 * Kotlin port of the Java `AudioBufferProcessor`.
 *
 * Captures audio from the microphone and feeds it to the native
 * multimon demodulator via JNI. Uses a producer-consumer queue
 * between the audio capture thread and the processing thread.
 *
 * Native methods (init, processBuffer, processBuffer2) are loaded
 * from the `multimon` JNI library (PacketDroid native code).
 */
class AudioBufferProcessor(
    private val callback: PacketCallback,
) : Thread("AudioBufferProcessor") {

    private var inited = false
    private val fbuf = FloatArray(16384)
    private var fbufCnt = 0
    private val overlap = 18 // overlap for AFSK DEMOD (FREQSAMP / BAUDRATE)
    private val writeAudioBuffer = false // for debug

    private val queue = LinkedBlockingQueue<ShortArray>()

    // for debugging captured samples
    // sox -e signed -r 22050 -b 16 sambombo.raw output2.wav
    private var fos: FileOutputStream? = null
    private val dumpFile = File("/sdcard/PacketDroidSamples.raw")

    private val audioIn = AudioIn()

    // JNI native methods
    external fun init()
    external fun processBuffer(buf: FloatArray, length: Int)
    external fun processBuffer2(buf: ByteArray)

    init {
        if (writeAudioBuffer) {
            try {
                fos = FileOutputStream(dumpFile)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun run() {
        Log.d(LOG_TAG, "thread started")
        if (!inited) {
            inited = true
            init() // init native demodulators
        }
        if (!audioIn.isAlive) audioIn.start()

        while (true) {
            try {
                decode(queue.take())
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    fun startRecording() {
        audioIn.recorder?.startRecording()
    }

    fun stopRecording() {
        try {
            audioIn.close()
        } catch (_: IllegalStateException) {
            // "stop() called on an uninitialized AudioRecord."
            // ignore this ;)
        }
        queue.clear()
    }

    private fun decode(s: ShortArray) {
        var max: Short = 0
        for (value in s) {
            if (writeAudioBuffer) {
                try {
                    fos?.write(value.toInt() and 0xFF)
                    fos?.write((value.toInt() shr 8) and 0xFF)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            fbuf[fbufCnt++] = value * (1.0f / 32768.0f)
            if (max < value) {
                max = value
            } else if (max < (-value).toShort()) {
                max = (-value).toShort()
            }
        }

        callback.peak(max)

        if (fbufCnt > overlap) {
            processBuffer(fbuf, fbufCnt - overlap)
            System.arraycopy(fbuf, fbufCnt - overlap, fbuf, 0, overlap)
            fbufCnt = overlap
        }
    }

    fun callback(data: ByteArray) {
        Log.d(LOG_TAG, "called callback: ${String(data)}")
        callback.received(data)
    }

    /**
     * Audio capture thread. Reads from the microphone into a queue.
     * Taken from: http://stackoverflow.com/questions/4525206/
     */
    inner class AudioIn : Thread("AudioIn") {
        var recorder: AudioRecord? = null
        private val buffers = Array(16) { ShortArray(8192) }

        init {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        }

        override fun run() {
            var ix = 0
            recorder = AudioRecord(
                AudioSource.MIC, 22050,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, 16384,
            )

            try {
                recorder?.startRecording()

                while (recorder?.recordingState != AudioRecord.RECORDSTATE_STOPPED) {
                    val buffer = buffers[ix++ % buffers.size]
                    recorder?.read(buffer, 0, buffer.size)
                    queue.put(buffer)
                }
            } catch (x: Throwable) {
                Log.w(LOG_TAG, "Error reading audio", x)
            }
        }

        fun close() {
            recorder?.stop()
            Log.d(LOG_TAG, "AudioIn: close")
        }
    }

    companion object {
        private const val LOG_TAG = "APRSdroid.AfskABP"

        init {
            System.loadLibrary("multimon")
        }
    }
}
