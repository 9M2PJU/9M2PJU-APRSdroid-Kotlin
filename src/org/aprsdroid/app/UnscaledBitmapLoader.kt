package org.aprsdroid.app

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Kotlin port of the Java `UnscaledBitmapLoader`.
 *
 * The original Java version had Old/New variants for Android SDK < 4
 * vs >= 4. Since minSdk is now 24, only the "New" path (inScaled =
 * false) is needed.
 */
object UnscaledBitmapLoader {

    @JvmStatic
    fun loadFromResource(
        resources: Resources,
        resId: Int,
        options: BitmapFactory.Options?,
    ): Bitmap {
        val opts = options ?: BitmapFactory.Options()
        opts.inScaled = false
        return BitmapFactory.decodeResource(resources, resId, opts)
    }
}
