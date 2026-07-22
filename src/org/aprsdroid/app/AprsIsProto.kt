package org.aprsdroid.app

import org.aprsdroid.app.data.PostEntity
import java.io.InputStream
import java.io.OutputStream

/**
 * Kotlin port of the Scala `AprsIsProto`.
 *
 * On construction, sends the APRS-IS login line (callsign + passcode +
 * version + filter) to the server.
 */
class AprsIsProto(service: AprsService, input: InputStream, output: OutputStream) :
    Tnc2Proto(input, output) {

    init {
        val loginfilter = service.prefs.getLoginString() + service.prefs.getFilterString(service)
        service.postAddPost(PostEntity.TYPE_TX, R.string.p_conn_aprsis, loginfilter)
        writer.println(loginfilter)
    }
}
