package org.aprsdroid.app

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Kotlin port of the Scala `UrlOpener` object + class.
 *
 * Opens a URL in the system browser, showing a toast on failure.
 * The companion `open` is the primary entry point; the class form is
 * kept for use as a `DialogInterface.OnClickListener`.
 */
object UrlOpener {

    @JvmStatic
    fun open(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(ctx, e.localizedMessage, Toast.LENGTH_SHORT).show()
        }
    }
}

class UrlOpener(private val ctx: Context, private val url: String) :
    DialogInterface.OnClickListener {

    override fun onClick(d: DialogInterface?, which: Int) {
        UrlOpener.open(ctx, url)
    }
}
