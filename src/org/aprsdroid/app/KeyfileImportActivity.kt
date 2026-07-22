package org.aprsdroid.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.data.AprsDatabase
import org.aprsdroid.app.data.PostEntity
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.cert.X509Certificate
import kotlinx.coroutines.runBlocking

/**
 * Kotlin/Compose port of the Scala `KeyfileImportActivity`.
 *
 * Imports a PKCS12 key file (.p12) for SSL authentication. Prompts
 * for the key password via a Compose AlertDialog, extracts the
 * callsign from the certificate's X500Principal, saves the key to
 * the app's private keystore directory, and sets the callsign in
 * preferences.
 */
class KeyfileImportActivity : ComponentActivity() {

    private val tag = "APRSdroid.KeyImport"
    private val keystorePass = "APRS".toCharArray()
    private val keystoreDir = "keystore"
    private val callRegex = Regex(".*CALLSIGN=([0-9A-Za-z]+).*")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        Log.d(tag, "created: $intent")

        setContent {
            PasswordDialog(
                onConfirm = { pwd -> importKey(pwd) },
                onCancel = { finish() },
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PasswordDialog(
        onConfirm: (String) -> Unit,
        onCancel: () -> Unit,
    ) {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(getString(R.string.ssl_import_activity)) },
            text = {
                Column {
                    Text(getString(R.string.ssl_import_password))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(password) }) {
                    Text(getString(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancel) {
                    Text(getString(android.R.string.cancel))
                }
            },
        )
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
            val errmsg = getString(R.string.ssl_import_error, e.message ?: "")
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
