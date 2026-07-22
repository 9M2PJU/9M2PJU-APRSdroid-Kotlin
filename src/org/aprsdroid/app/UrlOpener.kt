package org.aprsdroid.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Kotlin port of the Scala `UrlOpener` object.
 *
 * Opens a URL in the system browser, showing a toast on failure.
 *
 * The Scala version also had a `class UrlOpener` that implemented
 * `DialogInterface.OnClickListener`; in Kotlin we use lambdas at
 * call sites instead (e.g. `{ _, _ -> UrlOpener.open(ctx, url) }`).
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
