package org.aprsdroid.app

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.InputType
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.aprsdroid.app.data.AprsDatabase
import org.aprsdroid.app.data.PostEntity
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.cert.X509Certificate
import kotlinx.coroutines.runBlocking

/**
 * Kotlin port of the Scala `KeyfileImportActivity`.
 *
 * Imports a PKCS12 key file (.p12) for SSL authentication. Prompts
 * for the key password, extracts the callsign from the certificate's
 * X500Principal, saves the key to the app's private keystore directory,
 * and sets the callsign in preferences.
 */
class KeyfileImportActivity : AppCompatActivity() {

    private val tag = "APRSdroid.KeyImport"
    private val keystorePass = "APRS".toCharArray()
    private val keystoreDir = "keystore"

    private val callRegex = Regex(".*CALLSIGN=([0-9A-Za-z]+).*")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        Log.d(tag, "created: $intent")
        queryForPassword()
    }

    private fun queryForPassword() {
        val pwd = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val listener = DialogInterface.OnClickListener { _, which ->
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> importKey(pwd.text.toString())
                else -> finish()
            }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.ssl_import_activity)
            .setMessage(R.string.ssl_import_password)
            .setView(pwd)
            .setPositiveButton(android.R.string.ok, listener)
            .setNegativeButton(android.R.string.cancel, listener)
            .setOnCancelListener { finish() }
            .create()
            .show()
    }

    private fun importKey(password: String) {
        val db = AprsDatabase.get(this)
        try {
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(contentResolver.openInputStream(intent.data!!), password.toCharArray())
            var callsign: String? = null
            val aliases = ks.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                if (ks.isKeyEntry(alias)) {
                    val c = ks.getCertificate(alias) as X509Certificate
                    // work around missing X500Principal.getName(String, Map) on SDK<9:
                    val dn = c.subjectX500Principal.toString()
                        .replace("OID.1.3.6.1.4.1.12348.1.1=", "CALLSIGN=")
                    Log.d(tag, "Loaded key: $dn")
                    callRegex.find(dn)?.let { callsign = it.groupValues[1] }
                }
            }
            if (callsign != null) {
                val dir = applicationContext.getDir(keystoreDir, Context.MODE_PRIVATE)
                val keyStoreFile = File(dir, "$callsign.p12")
                ks.store(FileOutputStream(keyStoreFile), keystorePass)

                PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putString("callsign", callsign)
                    .commit()

                val msg = getString(R.string.ssl_import_ok, callsign)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                Thread {
                    runBlocking {
                        db.postDao().addPost(
                            System.currentTimeMillis(),
                            PostEntity.TYPE_INFO,
                            getString(R.string.post_info),
                            msg,
                        )
                    }
                }.start()
                startActivity(Intent(this, LogActivity::class.java))
            }
        } catch (e: Exception) {
            val errmsg = getString(R.string.ssl_import_error, e.message)
            Toast.makeText(this, errmsg, Toast.LENGTH_LONG).show()
            Thread {
                runBlocking {
                    db.postDao().addPost(
                        System.currentTimeMillis(),
                        PostEntity.TYPE_ERROR,
                        getString(R.string.post_error),
                        errmsg,
                    )
                }
            }.start()
            e.printStackTrace()
        }
        finish()
    }
}
