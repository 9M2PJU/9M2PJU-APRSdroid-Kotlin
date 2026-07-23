package org.aprsdroid.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import org.aprsdroid.app.ui.PostListScreen
import org.aprsdroid.app.ui.PostListViewModel
import org.aprsdroid.app.ui.theme.AprsTheme

/**
 * Kotlin/Compose implementation of `LogActivity`.
 *
 * Shows the packet log / activity feed. Uses [PostListViewModel]
 * backed by Room to reactively display packets as they arrive.
 * Includes Start/Stop service buttons and bottom navigation.
 */
class LogActivity : ComponentActivity() {

    private val viewModel: PostListViewModel by viewModels()
    private val prefs by lazy { PrefsWrapper(this) }

    private val permissionHelper by lazy {
        PermissionHelper(
            activity = this,
            getActionName = { action ->
                when (action) {
                    START_SERVICE -> R.string.startlog
                    START_SERVICE_ONCE -> R.string.singlelog
                    else -> R.string.startlog
                }
            },
            onAllGranted = { action ->
                when (action) {
                    START_SERVICE -> startService(AprsService.intent(this, AprsService.SERVICE))
                    START_SERVICE_ONCE -> startService(AprsService.intent(this, AprsService.SERVICE_ONCE))
                }
            },
            onFailedCancel = { /* nop */ },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)

        setContent {
            AprsTheme {
                PostListScreen(
                    viewModel = viewModel,
                    isServiceRunning = AprsService.running,
                    onStartService = { startAprsService(START_SERVICE) },
                    onStopService = { stopAprsService() },
                    onSingleShot = { startAprsService(START_SERVICE_ONCE) },
                    onNavigate = { target -> navigateTo(target) },
                    onPreferences = { startActivity(Intent(this, PrefsAct::class.java)) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        prefs.prefs.edit().putString("activity", "log").commit()
    }

    private fun startAprsService(action: Int) {
        val perms = AprsBackend.defaultBackendPermissions(prefs).toTypedArray()
        permissionHelper.checkPermissions(perms, action)
    }

    private fun stopAprsService() {
        prefs.prefs.edit().putBoolean("service_running", false).commit()
        stopService(AprsService.intent(this, AprsService.SERVICE))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionHelper.onResult(requestCode, permissions, grantResults)
    }

    private fun navigateTo(target: NavTarget) {
        val cls = when (target) {
            NavTarget.HUB -> HubActivity::class.java
            NavTarget.LOG -> return // already here
            NavTarget.MAP -> {
                MapModes.startMap(this, prefs, "")
                return
            }
            NavTarget.MESSAGES -> ConversationsActivity::class.java
        }
        startActivity(Intent(this, cls)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        overridePendingTransition(0, 0)
    }

    companion object {
        const val START_SERVICE = 1001
        const val START_SERVICE_ONCE = 1002
    }
}

enum class NavTarget { HUB, LOG, MAP, MESSAGES }
