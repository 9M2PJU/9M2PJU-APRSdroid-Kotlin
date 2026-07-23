package org.aprsdroid.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.aprsdroid.app.ui.theme.AprsTheme

class FirstRunActivity : ComponentActivity() {

    private var showPermissionDialog by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Regardless of which permissions are granted, proceed to the app.
        // The service will request specific permissions again when starting.
        prefs.prefs.edit().putBoolean("permissions_requested", true).commit()
        proceedToMain()
    }

    private val prefs by lazy { PrefsWrapper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)
        setContent {
            AprsTheme {
                var showPasscode by mutableStateOf(true)

                if (showPasscode) {
                    PasscodeDialogCompose(
                        activity = this,
                        firstrun = true,
                        onDismiss = {
                            showPasscode = false
                            showPermissionDialog = true
                        },
                    )
                }

                if (showPermissionDialog) {
                    PermissionRationaleDialog(
                        onAccept = {
                            showPermissionDialog = false
                            requestAllPermissions()
                        },
                        onSkip = {
                            showPermissionDialog = false
                            prefs.prefs.edit().putBoolean("permissions_requested", true).commit()
                            proceedToMain()
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun PermissionRationaleDialog(
        onAccept: () -> Unit,
        onSkip: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onSkip,
            title = { Text(getString(R.string.app_name)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("APRSdroid needs the following permissions to function:")
                    Text(
                        "• Location — to transmit your position and show nearby stations\n" +
                        "• Notifications — to show service status\n" +
                        "• Microphone — for AFSK audio modems (optional)\n" +
                        "• Bluetooth — for TNC connections (optional)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "You can grant the essential ones now and others later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(onClick = onAccept) {
                    Text("Grant permissions")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = onSkip) {
                    Text("Skip for now")
                }
            },
        )
    }

    private fun requestAllPermissions() {
        val perms = mutableListOf<String>()

        // Location — required for foreground service type "location"
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Notifications — required on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Microphone — for AFSK modems
        perms.add(Manifest.permission.RECORD_AUDIO)

        // Bluetooth — for TNC connections
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun proceedToMain() {
        val i = Intent(this, APRSdroid::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(i)
        finish()
    }
}
