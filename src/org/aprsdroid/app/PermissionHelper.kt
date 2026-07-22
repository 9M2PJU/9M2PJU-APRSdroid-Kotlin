package org.aprsdroid.app

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Kotlin port of the Scala `PermissionHelper` trait.
 *
 * The Scala version was a trait mixed into `Activity` subclasses with
 * abstract callback methods. In Kotlin we use a delegating helper that
 * the activity calls into, passing itself and the callbacks. This avoids
 * the multiple-inheritance issues of the trait-with-abstract-override
 * pattern and works naturally with `ComponentActivity`.
 *
 * Usage from an activity:
 * ```
 * private val permissionHelper = PermissionHelper(this,
 *     getActionName = { action -> ... },
 *     onAllGranted = { action -> ... },
 *     onFailedCancel = { action -> ... })
 *
 * permissionHelper.checkPermissions(perms, REQUEST_CODE)
 * // then in onRequestPermissionsResult:
 * permissionHelper.onResult(requestCode, permissions, grantResults)
 * ```
 */
class PermissionHelper(
    private val activity: Activity,
    private val getActionName: (Int) -> Int,
    private val onAllGranted: (Int) -> Unit,
    private val onFailedCancel: (Int) -> Unit,
) {

    /**
     * Check whether the given permissions are granted; if not, request
     * them. Returns true if all permissions were already granted (and
     * [onAllGranted] was called), false if a request dialog was shown.
     */
    fun checkPermissions(permissions: Array<String>, action: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            onAllGranted(action)
            return true
        }
        var needDialog = false
        for (p in permissions) {
            if (activity.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                needDialog = true
            }
        }
        if (needDialog) {
            activity.requestPermissions(permissions, action)
            return false
        }
        onAllGranted(action)
        return true
    }

    /**
     * Call from the activity's `onRequestPermissionsResult`.
     */
    fun onResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        val failedPerms = mutableSetOf<String>()
        for (i in permissions.indices) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                failedPerms.add(permissions[i])
            }
        }
        if (failedPerms.isNotEmpty()) {
            onPermissionsFailed(requestCode, failedPerms)
        } else {
            onAllGranted(requestCode)
        }
    }

    private fun getPermissionName(permission: String): String {
        return try {
            val pi = activity.packageManager.getPermissionInfo(permission, 0)
            pi.loadLabel(activity.packageManager).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            // Use the last segment of the permission name
            permission.substringAfterLast('.')
        }
    }

    private fun onPermissionsFailed(action: Int, permissions: Set<String>) {
        val sb = StringBuilder(activity.getString(R.string.no_perm_text))
        sb.append("\n\n")
        for (p in permissions) {
            sb.append("- ").append(getPermissionName(p)).append("\n")
        }
        AlertDialog.Builder(activity)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(getActionName(action))
            .setMessage(sb.toString())
            .setPositiveButton(R.string.preferences) { _: DialogInterface, _: Int ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", activity.packageName, null)
                activity.startActivity(intent)
            }
            .setNegativeButton(android.R.string.cancel) { _: DialogInterface, _: Int ->
                onFailedCancel(action)
            }
            .create()
            .show()
    }
}
