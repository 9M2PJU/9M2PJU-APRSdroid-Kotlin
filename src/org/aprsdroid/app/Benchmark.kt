package org.aprsdroid.app

import android.util.Log

/**
 * Kotlin port of the Scala `Benchmark` object.
 *
 * A simple timing utility: wraps a block of code and logs its
 * execution time in seconds.
 *
 * Usage: `Benchmark("MyTag") { /* code to time */ }`
 */
object Benchmark {

    inline operator fun <T> invoke(tag: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        return try {
            block()
        } finally {
            val execTime = System.currentTimeMillis() - start
            Log.d(tag, "execution time: %.3f s".format(execTime / 1000.0))
        }
    }
}
