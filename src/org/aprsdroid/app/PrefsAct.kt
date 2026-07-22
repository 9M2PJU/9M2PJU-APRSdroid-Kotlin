package org.aprsdroid.app

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.preference.Preference
import android.preference.PreferenceActivity
import android.preference.PreferenceManager
import android.preference.Preference.OnPreferenceChangeListener
import android.preference.Preference.OnPreferenceClickListener
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Kotlin port of the Scala `PrefsAct`.
 *
 * Main preferences screen using the XML preference framework
 * (res/xml/preferences.xml). Handles profile import/export, offline
 * map file picker, all-files-access settings, and restart dialog
 * when offline mapping is toggled.
 */
class PrefsAct : PreferenceActivity() {

    private val prefs by lazy { PrefsWrapper(this) }

    private fun exportPrefs() {
        val filename = "profile-${SimpleDateFormat("yyyyMMdd-HHmm").format(Date())}.aprs"
        val directory = UIHelper.getExportDirectory(this)
        val file = File(directory, filename)
        try {
            directory.mkdirs()
            val allPrefs = PreferenceManager.getDefaultSharedPreferences(this).all
            allPrefs.remove("map_zoom")
            val json = JSONObject(allPrefs)
            PrintWriter(file).use { fo ->
                fo.println(json.toString(2))
            }
            UIHelper.shareFile(this, file, filename)
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun fileChooserPreference(prefName: String, reqCode: Int, titleId: Int) {
        findPreference(prefName)?.onPreferenceClickListener = OnPreferenceClickListener {
            val getFile = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType("*/*")
                .addCategory(Intent.CATEGORY_OPENABLE)
            startActivityForResult(
                Intent.createChooser(getFile, getString(titleId)),
                reqCode,
            )
            true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        addPreferencesFromResource(R.xml.preferences)

        // Set up "Grant Storage Permissions" button
        findPreference("all_files_access")?.onPreferenceClickListener = OnPreferenceClickListener {
            openAllFilesAccessSettings()
            true
        }

        // Warn user to restart app when offline map is toggled
        findPreference("p.offlinemap")?.onPreferenceChangeListener =
            OnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) showRestartDialog()
                true
            }

        // File pickers
        fileChooserPreference("tilepath", 123456, R.string.p_mbtiles_file_picker_title)
    }

    private fun showRestartDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.restart_required_title)
            .setMessage(R.string.restart_required_body)
            .setPositiveButton(R.string.restart_now) { _, _ ->
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                finishAffinity()
                if (intent != null) startActivity(intent)
                // hard kill so the process restarts cleanly
                Process.killProcess(Process.myPid())
            }
            .setNegativeButton(R.string.restart_later, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        findPreference("p_connsetup")?.summary = prefs.getBackendName()
        findPreference("p_location")?.summary = prefs.getLocationSourceName()
        findPreference("p_symbol")?.summary =
            getString(R.string.p_symbol_summary) + ": " + prefs.getString("symbol", "/$")
        // Show current tilepath in summary
        val tilepath = prefs.prefs.getString("tilepath", null)
        if (!tilepath.isNullOrEmpty()) {
            findPreference("tilepath")?.summary = tilepath
        }
    }

    private fun openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } else {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun resolveContentUri(uri: Uri): String? {
        val parts = uri.path?.replace("/document/", "")?.split(":", limit = 2) ?: return null
        val storage = parts[0]
        val path = parts.getOrNull(1) ?: return null
        Log.d("PrefsAct", "resolveContentUri s=$storage p=$path")
        return if (storage == "primary") {
            "${Environment.getExternalStorageDirectory()}/$path"
        } else {
            "/storage/$storage/$path"
        }
    }

    override fun onActivityResult(reqCode: Int, resultCode: Int, data: Intent?) {
        Log.d("PrefsAct", "onActResult: request=$reqCode result=$resultCode $data")
        if (resultCode == RESULT_OK && reqCode == 123456) {
            // tilepath picker — resolve URI to real path and save
            val uri = data?.data ?: return
            val takeFlags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, takeFlags)
            val resolvedPath = when (uri.scheme) {
                "file" -> uri.path
                "content" -> resolveContentUri(uri)
                else -> null
            }
            if (resolvedPath != null) {
                PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putString("tilepath", resolvedPath)
                    .commit()
                Toast.makeText(
                    this,
                    getString(R.string.selected_file, File(resolvedPath).name),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                Toast.makeText(this, R.string.mapfile_error, Toast.LENGTH_SHORT).show()
            }
            finish()
            startActivity(intent)
        } else if (resultCode == RESULT_OK && reqCode == 123458) {
            data?.setClass(this, ProfileImportActivity::class.java)
            startActivity(data)
        } else {
            super.onActivityResult(reqCode, resultCode, data)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.options_prefs, menu)
        return true
    }

    override fun onOptionsItemSelected(mi: MenuItem): Boolean {
        return when (mi.itemId) {
            R.id.profile_load -> {
                val getFile = Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                startActivityForResult(
                    Intent.createChooser(getFile, getString(R.string.profile_import_activity)),
                    123458,
                )
                true
            }
            R.id.profile_export -> {
                exportPrefs()
                true
            }
            else -> super.onOptionsItemSelected(mi)
        }
    }
}
