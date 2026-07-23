package org.aprsdroid.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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

    private var pendingAction = 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            when (pendingAction) {
                START_SERVICE -> startService(AprsService.intent(this, AprsService.SERVICE))
                START_SERVICE_ONCE -> startService(AprsService.intent(this, AprsService.SERVICE_ONCE))
            }
        }
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
        pendingAction = action
        try {
            val permsSet = mutableSetOf<String>()
            permsSet.addAll(AprsBackend.defaultBackendPermissions(prefs))
            permsSet.addAll(LocationSources.getPermissions(prefs))
            val perms = permsSet.toTypedArray()
            if (perms.isEmpty()) {
                doStartService(action)
            } else {
                val needed = perms.filter {
                    checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (needed.isEmpty()) {
                    doStartService(action)
                } else {
                    permissionLauncher.launch(needed.toTypedArray())
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("APRSdroid.Log", "Failed to start service", e)
            Toast.makeText(this, "Could not start service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun doStartService(action: Int) {
        when (action) {
            START_SERVICE -> startService(AprsService.intent(this, AprsService.SERVICE))
            START_SERVICE_ONCE -> startService(AprsService.intent(this, AprsService.SERVICE_ONCE))
        }
    }

    private fun stopAprsService() {
        try {
            prefs.prefs.edit().putBoolean("service_running", false).commit()
            stopService(AprsService.intent(this, AprsService.SERVICE))
        } catch (e: Exception) {
            android.util.Log.e("APRSdroid.Log", "Failed to stop service", e)
            Toast.makeText(this, "Could not stop service: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
