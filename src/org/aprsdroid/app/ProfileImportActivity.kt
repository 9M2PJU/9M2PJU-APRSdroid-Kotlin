package org.aprsdroid.app

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.aprsdroid.app.data.AprsDatabase
import org.aprsdroid.app.data.PostEntity
import org.json.JSONObject
import java.util.Scanner
import kotlinx.coroutines.runBlocking

/**
 * Kotlin port of the Scala `ProfileImportActivity`.
 *
 * Imports a JSON profile file (previously exported by PrefsAct) into
 * SharedPreferences. The file URI is passed via the intent's data
 * field. Shows a toast and logs to the database on success/failure.
 */
class ProfileImportActivity : AppCompatActivity() {

    private val tag = "APRSdroid.ProfileImport"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        Log.d(tag, "created: $intent")
        importConfig()
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
            val msg = getString(R.string.profile_import_done, intent.data?.path)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

            Thread {
                runBlocking {
                    db.postDao().addPost(
                        System.currentTimeMillis(),
                        PostEntity.TYPE_INFO,
                        getString(R.string.profile_import_activity),
                        msg,
                    )
                }
            }.start()

            startActivity(Intent(this, LogActivity::class.java))
        } catch (e: Exception) {
            val errmsg = getString(R.string.profile_import_error, e.message)
            Toast.makeText(this, errmsg, Toast.LENGTH_LONG).show()
            Thread {
                runBlocking {
                    db.postDao().addPost(
                        System.currentTimeMillis(),
                        PostEntity.TYPE_ERROR,
                        getString(R.string.profile_import_activity),
                        errmsg,
                    )
                }
            }.start()
            e.printStackTrace()
        }
        finish()
    }
}
