package org.aprsdroid.app

import android.content.Context
import android.util.Log
import android.widget.Toast
import net.ab0oo.aprs.parser.APRSPacket
import org.aprsdroid.app.data.PostEntity
import java.io.File
import java.io.FileInputStream
import java.net.Socket
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Kotlin port of the Scala `TcpUploader`.
 *
 * Connects to an APRS-IS server (or a TCP TNC) over a long-lived socket
 * thread. Supports TLS using a per-callsign PKCS12 keystore, with a
 * naive trust manager that accepts any server certificate. Reconnects
 * automatically on socket errors / timeouts.
 */
class TcpUploader(private val service: AprsService, prefs: PrefsWrapper) :
    AprsBackendAbstract(prefs) {

    private val TAG = "APRSdroid.TcpUploader"
    private val hostport = prefs.getString("tcp.server", "aprs.hamradio.my")
    private val soTimeout = prefs.getStringInt("tcp.sotimeout", 120)
    private val RECONNECT = 30
    private var conn: TcpSocketThread? = null

    override fun start(): Boolean {
        if (conn == null) {
            createConnection()
        }
        return false
    }

    private fun createConnection() {
        Log.d(TAG, "TcpUploader.createConnection: $hostport")
        conn = TcpSocketThread(hostport).also { it.start() }
    }

    override fun update(packet: APRSPacket): String {
        Log.d(TAG, "TcpUploader.update: $packet")
        return conn!!.update(packet)
    }

    override fun stop() {
        val c = conn ?: return
        synchronized(c) { c.running = false }
        Thread { c.shutdown() }.start()
        c.interrupt()
        try {
            c.join(50)
        } catch (_: InterruptedException) {}
    }

    inner class TcpSocketThread(private val hostport: String) :
        Thread("APRSdroid TCP connection") {

        private val TAG = "APRSdroid.TcpSocketThread"
        @Volatile
        var running = true
        private var passcodeWarned = false
        private var socket: Socket? = null
        private var tnc: TncProto? = null

        private val KEYSTORE_DIR = "keystore"
        private val KEYSTORE_PASS = "APRS".toCharArray()

        private fun initSslSocket(hostport: String): Socket? {
            val dir = service.applicationContext.getDir(KEYSTORE_DIR, Context.MODE_PRIVATE)
            val keyStoreFile = File(dir.toString() + File.separator + prefs.getCallsign() + ".p12")

            val ks = KeyStore.getInstance("PKCS12")
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())

            try {
                FileInputStream(keyStoreFile).use { fis ->
                    ks.load(fis, KEYSTORE_PASS)
                }
                for (alias in ks.aliases()) {
                    if (ks.isKeyEntry(alias)) {
                        val c = ks.getCertificate(alias) as X509Certificate
                        c.checkValidity()
                        val dn = c.subjectX500Principal.toString()
                            .replace("OID.1.3.6.1.4.1.12348.1.1=", "CALLSIGN=")
                        service.postAddPost(PostEntity.TYPE_INFO, R.string.post_info, "Loaded key: $dn")
                    }
                }
                kmf.init(ks, KEYSTORE_PASS)
                val sc = SSLContext.getInstance("TLS")
                sc.init(kmf.keyManagers, arrayOf<javax.net.ssl.TrustManager>(NaiveTrustManager()), null)

                val (host, port) = AprsPacket.parseHostPort(hostport, 24580)
                service.postAddPost(
                    PostEntity.TYPE_INFO, R.string.post_info,
                    service.getString(R.string.post_connecting, host, port as Any),
                )

                val socket = sc.socketFactory.createSocket(host, port) as SSLSocket
                socket.enabledCipherSuites = sc.socketFactory.defaultCipherSuites
                return socket
            } catch (e: java.io.FileNotFoundException) {
                service.postAddPost(
                    PostEntity.TYPE_INFO, R.string.post_info,
                    service.getString(R.string.ssl_no_keyfile, prefs.getCallsign()),
                )
                return null
            } catch (e: Exception) {
                e.printStackTrace()
                service.postAddPost(PostEntity.TYPE_INFO, R.string.post_info, e.toString())
                return null
            }
        }

        @Synchronized
        private fun initSocket() {
            Log.d(TAG, "init_socket()")
            if (!running) {
                Log.d(TAG, "init_socket() aborted")
                return
            }
            // Only try TLS on APRS-IS
            if (prefs.getProto() == "aprsis") {
                socket = initSslSocket(hostport)
            }
            if (socket == null) {
                val (host, port) = AprsPacket.parseHostPort(hostport, 14580)
                service.postAddPost(
                    PostEntity.TYPE_INFO, R.string.post_info,
                    service.getString(R.string.post_connecting, host, port as Any),
                )
                // passcode warning toast (only needed in non-SSL mode)
                if (!passcodeWarned && prefs.getProto() == "aprsis" && prefs.getPasscode() == "-1") {
                    service.handler.post {
                        Toast.makeText(service, R.string.anon_warning, Toast.LENGTH_LONG).show()
                    }
                    passcodeWarned = true
                }
                socket = Socket(host, port)
            }
            socket!!.keepAlive = true
            socket!!.soTimeout = soTimeout * 1000
            tnc = AprsBackend.instanciateProto(service, socket!!.getInputStream(), socket!!.getOutputStream())
            Log.d(TAG, "init_socket() done")
        }

        override fun run() {
            var needReconnect = false
            Log.d(TAG, "TcpSocketThread.run()")
            try {
                initSocket()
                service.postLinkOn(R.string.p_aprsis_tcp)
                service.postPosterStarted()
            } catch (e: IllegalArgumentException) {
                service.postAbort(e.message ?: ""); running = false
            } catch (e: Exception) {
                service.postAbort(e.toString()); running = false
            }
            while (running) {
                try {
                    if (needReconnect) {
                        Log.d(TAG, "reconnecting in $RECONNECT s")
                        service.postAddPost(
                            PostEntity.TYPE_INFO, R.string.post_info,
                            service.getString(R.string.post_reconnect, RECONNECT as Any),
                        )
                        shutdown()
                        sleep(RECONNECT * 1000L)
                        initSocket()
                        needReconnect = false
                        service.postLinkOn(R.string.p_aprsis_tcp)
                    }
                    Log.d(TAG, "waiting for data...")
                    var line: String?
                    while (running) {
                        line = tnc!!.readPacket()
                        if (line.isEmpty()) break
                        Log.d(TAG, "recv: $line")
                        if (line[0] != '#') {
                            service.postSubmit(line)
                        } else {
                            service.postAddPost(PostEntity.TYPE_INFO, R.string.post_info, line)
                        }
                    }
                    if (running) {
                        needReconnect = true
                    }
                } catch (se: java.net.SocketTimeoutException) {
                    Log.i(TAG, "restarting due to timeout")
                    needReconnect = true
                } catch (e: Exception) {
                    Log.d(TAG, "Exception $e")
                    needReconnect = true
                }
                if (needReconnect) {
                    service.postLinkOff(R.string.p_aprsis_tcp)
                }
            }
            Log.d(TAG, "TcpSocketThread.terminate()")
        }

        fun update(packet: APRSPacket): String {
            val s = socket
            return if (s != null && s.isConnected) {
                tnc!!.writePacket(packet)
                "TCP OK"
            } else {
                "TCP disconnected"
            }
        }

        private fun catchLog(tag: String, f: () -> Unit) {
            Log.d(TAG, "catchLog($tag)")
            try {
                f()
            } catch (e: Exception) {
                Log.d(TAG, "$tag exception: $e")
            }
        }

        @Synchronized
        fun shutdown() {
            Log.d(TAG, "shutdown()")
            tnc?.stop()
            val s = socket
            if (s != null) {
                catchLog("shutdownInput") { s.shutdownInput() }
                catchLog("shutdownOutput") { s.shutdownOutput() }
                catchLog("socket.close") { s.close() }
                socket = null
            }
        }
    }

    private class NaiveTrustManager : X509TrustManager {
        override fun checkClientTrusted(cert: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(cert: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate>? = null
    }
}
