package org.aprsdroid.app

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Kotlin port of the Scala `UpdateChecker` object.
 *
 * Checks GitHub Releases API for a newer version than the installed
 * one. If a newer version is found, shows a dialog. When the user taps
 * "Download", the APK is downloaded to the app's cache directory and
 * Android's package installer is triggered automatically.
 *
 * The check runs at most once per day per app instance, controlled by
 * the "last_update_check" SharedPreferences timestamp.
 *
 * Uses Kotlin coroutines instead of the deprecated `AsyncTask` /
 * `MyAsyncTask` from the Scala version.
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val REPO_OWNER = "9M2PJU"
    private const val REPO_NAME = "APRSdroid-9M2PJU-Mod"
    private const val API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    private const val RELEASES_URL = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"

    // Fallback: self-hosted version.json on Cloudflare CDN (different
    // network than api.github.com, works when GitHub API is blocked/
    // throttled)
    private const val FALLBACK_URL = "https://aprsdroid.hamradio.my/version.json"
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours

    // Result of fetching release info from GitHub API
    data class ReleaseInfo(
        val tag: String,
        val apkUrl: String,
        val apkName: String?,
    )

    fun getInstalledVersionName(ctx: android.content.Context): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.packageManager.getPackageInfo(ctx.packageName,
                    PackageManager.PackageInfoFlags.of(0)).versionName ?: ""
            } else {
                @Suppress("DEPRECATION")
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
            }
        } catch (_: PackageManager.NameNotFoundException) {
            ""
        }
    }

    /**
     * Compare two version strings. Returns true if `remote` is newer
     * than `local`. Handles git describe output like
     * "v1.4.7-9M2PJU-12-gabc123" by stripping suffixes and comparing
     * numeric components.
     */
    fun isNewerVersion(local: String?, remote: String?): Boolean {
        if (local.isNullOrEmpty()) return true
        if (remote.isNullOrEmpty()) return false

        fun normalize(v: String): IntArray {
            val s = v.removePrefix("v").split("-")[0]
            return try {
                s.split(".").map { it.toInt() }.toIntArray()
            } catch (_: Exception) {
                intArrayOf(0)
            }
        }

        val l = normalize(local)
        val r = normalize(remote)
        val len = maxOf(l.size, r.size)
        for (i in 0 until len) {
            val lv = if (i < l.size) l[i] else 0
            val rv = if (i < r.size) r[i] else 0
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }

    fun shouldCheck(ctx: android.content.Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val last = prefs.getLong("last_update_check", 0)
        return System.currentTimeMillis() - last > CHECK_INTERVAL_MS
    }

    fun markChecked(ctx: android.content.Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putLong("last_update_check", System.currentTimeMillis()).commit()
    }

    /**
     * Fetch the latest release info. Tries the GitHub API first, and
     * falls back to a self-hosted version.json on Cloudflare CDN if
     * the GitHub API is unreachable.
     */
    suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        // Try GitHub API first
        val apiResult = fetchFromGitHubApi()
        if (apiResult != null) {
            return@withContext apiResult
        }
        // Fallback: self-hosted version.json
        Log.d(TAG, "GitHub API failed, trying fallback: $FALLBACK_URL")
        fetchFromFallback()
    }

    private fun fetchFromGitHubApi(): ReleaseInfo? {
        val conn = (URL(API_URL).openConnection() as HttpURLConnection)
        return try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "APRSdroid-9M2PJU-Mod")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (conn.responseCode != 200) {
                Log.e(TAG, "GitHub API returned ${conn.responseCode}")
                return null
            }
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            reader.close()
            val json = JSONObject(sb.toString())
            val tag = json.optString("tag_name", "")
            if (tag.isEmpty()) return null

            // Look for an .apk asset in the release
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        val downloadUrl = asset.optString("browser_download_url", "")
                        if (downloadUrl.isNotEmpty()) {
                            return ReleaseInfo(tag, downloadUrl, name)
                        }
                    }
                }
            }
            // No APK asset found — fall back to the releases page URL
            ReleaseInfo(tag, RELEASES_URL, null)
        } catch (e: Exception) {
            Log.e(TAG, "GitHub API failed: ${e.message}")
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchFromFallback(): ReleaseInfo? {
        val conn = (URL(FALLBACK_URL).openConnection() as HttpURLConnection)
        return try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "APRSdroid-9M2PJU-Mod")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (conn.responseCode != 200) {
                Log.e(TAG, "Fallback returned ${conn.responseCode}")
                return null
            }
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            reader.close()
            val json = JSONObject(sb.toString())
            val tag = json.optString("version", "")
            if (tag.isEmpty()) return null
            val apkUrl = json.optString("apkUrl", RELEASES_URL)
            val apkName = json.optString("apkName", null)
            Log.d(TAG, "Fallback succeeded: $tag")
            ReleaseInfo(tag, apkUrl, apkName)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback failed: ${e.message}")
            null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Show the "update available" dialog.
     */
    fun showUpdateDialog(act: Activity, localVersion: String, info: ReleaseInfo) {
        val message = act.getString(R.string.update_available_text, localVersion, info.tag)
        AlertDialog.Builder(act)
            .setTitle(R.string.update_available_title)
            .setMessage(message)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton(R.string.update_download) { _: DialogInterface, _: Int ->
                if (info.apkName != null) {
                    // We have a direct APK download URL — download and install
                    downloadAndInstall(act, info)
                } else {
                    // No APK asset — fall back to opening browser
                    UrlOpener.open(act, RELEASES_URL)
                }
            }
            .setNeutralButton(R.string.update_later, null)
            .setNegativeButton(R.string.update_skip) { _: DialogInterface, _: Int ->
                val prefs = PreferenceManager.getDefaultSharedPreferences(act)
                prefs.edit().putString("skipped_version", info.tag).commit()
            }
            .create()
            .show()
    }

    /**
     * Download the APK to the cache directory and trigger the installer.
     * Shows a progress dialog during download.
     */
    private fun downloadAndInstall(act: Activity, info: ReleaseInfo) {
        val progress = ProgressDialog(act)
        progress.setTitle(act.getString(R.string.update_downloading_title))
        progress.setMessage(act.getString(R.string.update_downloading_message, info.tag))
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progress.setCancelable(true)
        progress.max = 100
        progress.progress = 0
        progress.show()

        val scope = MainScope()
        progress.setOnCancelListener {
            Log.d(TAG, "Download cancelled by user")
            scope.cancel()
            Toast.makeText(act, R.string.update_download_cancelled, Toast.LENGTH_SHORT).show()
        }

        scope.launch {
            val apkFile = withContext(Dispatchers.IO) {
                try {
                    val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection)
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", "APRSdroid-9M2PJU-Mod")
                    conn.connectTimeout = 30000
                    conn.readTimeout = 60000
                    conn.connect()

                    if (conn.responseCode != 200) {
                        Log.e(TAG, "Download failed: HTTP ${conn.responseCode}")
                        conn.disconnect()
                        return@withContext null
                    }

                    val totalSize = conn.contentLength
                    val updatesDir = File(act.cacheDir, "updates").apply { mkdirs() }
                    val apkFile = File(updatesDir, info.apkName)

                    conn.inputStream.use { input ->
                        FileOutputStream(apkFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalRead = 0
                            var lastPct = -1
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                if (totalSize > 0) {
                                    val pct = totalRead * 100 / totalSize
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        if (progress.isShowing) {
                                            progress.progress = pct
                                        }
                                    }
                                }
                            }
                        }
                    }
                    conn.disconnect()
                    Log.i(TAG, "Downloaded APK: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
                    apkFile
                } catch (e: Exception) {
                    Log.e(TAG, "Download failed: ${e.message}")
                    null
                }
            }

            progress.dismiss()
            if (apkFile == null || !apkFile.exists()) {
                Toast.makeText(act, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                UrlOpener.open(act, RELEASES_URL)
                return@launch
            }
            promptInstall(act, apkFile)
        }
    }

    /**
     * Trigger Android's package installer with the downloaded APK.
     */
    private fun promptInstall(act: Activity, apkFile: File) {
        val uri = FileProvider.getUriForFile(act, "org.aprsdroid.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            act.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer: ${e.message}")
            Toast.makeText(act, R.string.update_install_failed, Toast.LENGTH_LONG).show()
            UrlOpener.open(act, RELEASES_URL)
        }
    }

    /**
     * Run the update check if needed. Called from onResume of the main
     * activity.
     */
    fun checkForUpdates(act: Activity) {
        if (!shouldCheck(act)) return
        doCheck(act, force = false)
    }

    /**
     * Force an update check, bypassing the 24h cooldown.
     * Called from the "Check for updates" menu item.
     */
    fun forceCheckForUpdates(act: Activity) {
        doCheck(act, force = true)
    }

    private fun doCheck(act: Activity, force: Boolean) {
        val localVersion = getInstalledVersionName(act)
        if (localVersion.isEmpty()) return

        markChecked(act) // record check time regardless of result

        val scope = MainScope()
        scope.launch {
            val info = fetchLatestRelease()
            if (info == null) {
                if (force) {
                    Toast.makeText(act, R.string.update_check_failed, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // User skipped this version previously?
            val prefs = PreferenceManager.getDefaultSharedPreferences(act)
            val skipped = prefs.getString("skipped_version", null)
            if (skipped != null && skipped == info.tag) return@launch

            if (isNewerVersion(localVersion, info.tag)) {
                Log.i(TAG, "Update available: $localVersion -> ${info.tag}")
                showUpdateDialog(act, localVersion, info)
            } else {
                Log.d(TAG, "Up to date: $localVersion (latest: ${info.tag})")
                if (force) {
                    Toast.makeText(act, act.getString(R.string.up_to_date, info.tag),
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
