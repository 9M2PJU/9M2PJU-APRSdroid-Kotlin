package org.aprsdroid.app

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.aprsdroid.app.data.AprsDatabase
import org.aprsdroid.app.data.PostEntity
import org.json.JSONObject
import java.util.Scanner
import kotlinx.coroutines.runBlocking

/**
 * Kotlin/Compose port of the Scala `ProfileImportActivity`.
 *
 * Imports a JSON profile file (previously exported by PrefsAct) into
 * SharedPreferences. The file URI is passed via the intent's data
 * field. Shows a loading indicator while importing, then a toast and
 * logs to the database on success/failure before redirecting to the
 * LogActivity.
 */
class ProfileImportActivity : ComponentActivity() {

    private val tag = "APRSdroid.ProfileImport"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        Log.d(tag, "created: $intent")

        setContent {
            ImportProgressScreen()
        }

        // Perform the import on a background thread
        Thread { importConfig() }.start()
    }

    @Composable
    private fun ImportProgressScreen() {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(getString(R.string.profile_import_activity))
        }
    }

    private fun importConfig() {
        val db = AprsDatabase.get(this)
        try {
            val scanner = Scanner(contentResolver.openInputStream(intent.data!!))
                .useDelimiter("\\A")
            val configString = scanner.next()
            val config = JSONObject(configString)
            val prefsEdit = PreferenceManager.getDefaultSharedPreferences(this).edit()

            val keys = config.keys()
            while (keys.hasNext()) {
                val item = keys.next() as String
                val value = config.get(item)
                Log.d(tag, "reading: $item = $value/${value.javaClass.simpleName}")

                when (value.javaClass.simpleName) {
                    "String" -> prefsEdit.putString(item, config.getString(item))
                    "Boolean" -> prefsEdit.putBoolean(item, config.getBoolean(item))
                    "Int", "Integer" -> prefsEdit.putInt(item, config.getInt(item))
                    "Double" -> prefsEdit.putFloat(item, config.getDouble(item).toFloat())
                }
            }
            prefsEdit.commit()
            val msg = getString(R.string.profile_import_done, intent.data?.path ?: "")
            runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }

            runBlocking {
                db.postDao().addPost(
                    System.currentTimeMillis(),
                    PostEntity.TYPE_INFO,
                    getString(R.string.profile_import_activity),
                    msg,
                )
            }

            startActivity(Intent(this, LogActivity::class.java))
        } catch (e: Exception) {
            val errmsg = getString(R.string.profile_import_error, e.message ?: "")
            runOnUiThread {
                Toast.makeText(this, errmsg, Toast.LENGTH_LONG).show()
            }
            runBlocking {
                db.postDao().addPost(
                    System.currentTimeMillis(),
                    PostEntity.TYPE_ERROR,
                    getString(R.string.profile_import_activity),
                    errmsg,
                )
            }
            e.printStackTrace()
        }
        finish()
    }
}
