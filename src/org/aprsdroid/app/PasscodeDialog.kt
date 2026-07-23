package org.aprsdroid.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

/**
 * Kotlin/Compose port of the Scala `PasscodeDialog`.
 *
 * Prompts the user for their callsign + APRS-IS passcode. Used both on
 * first run (with explanatory text) and from the preferences screen
 * (passcode entry only).
 *
 * Rendered as a Compose [AlertDialog] instead of the old XML layout.
 */
@Composable
fun PasscodeDialogCompose(
    activity: Activity,
    firstrun: Boolean,
    onDismiss: () -> Unit,
) {
    val prefs = remember { PrefsWrapper(activity) }
    var callsign by remember {
        mutableStateOf(prefs.getCallsign())
    }
    var passcode by remember {
        mutableStateOf(prefs.getString("passcode", ""))
    }
    var movedAwayFromCallsign by remember { mutableStateOf(false) }
    var callsignHasFocus by remember { mutableStateOf(true) }

    val callError = if (callsign != "" || !movedAwayFromCallsign) null
        else activity.getString(R.string.p_callsign_entry)
    val passOk = if (passcode != "") AprsPacket.passcodeAllowed(callsign, passcode, true) else true
    val passError = if (passOk) null else activity.getString(R.string.wrongpasscode)
    val okEnabled = callsign != "" && callError == null && passError == null

    AlertDialog(
        onDismissRequest = {
            if (firstrun) {
                Log.d("PasscodeDialog", "closing parent activity")
                activity.finish()
            }
            onDismiss()
        },
        title = {
            Text(activity.getString(if (firstrun) R.string.fr_title else R.string.p_passcode_entry))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (firstrun) {
                    Text(activity.getString(R.string.firstrun_welcome_title))
                    Text(activity.getString(R.string.firstrun_welcome_text))
                }
                OutlinedTextField(
                    value = callsign,
                    onValueChange = { callsign = it.uppercase() },
                    label = { Text(activity.getString(R.string.p_callsign_nossid)) },
                    singleLine = true,
                    isError = callError != null,
                    supportingText = { callError?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = passcode,
                    onValueChange = { passcode = it.filter { c -> c.isDigit() } },
                    label = { Text(activity.getString(R.string.p_passcode)) },
                    singleLine = true,
                    isError = passError != null,
                    supportingText = { passError?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = okEnabled,
                onClick = {
                    savePasscode(activity, prefs, callsign, passcode, completed = true)
                    onDismiss()
                },
            ) {
                Text(activity.getString(android.R.string.ok))
            }
        },
        dismissButton = {
            if (firstrun) {
                TextButton(onClick = {
                    if (firstrun) {
                        Log.d("PasscodeDialog", "closing parent activity")
                        activity.finish()
                    }
                    onDismiss()
                }) {
                    Text(activity.getString(android.R.string.cancel))
                }
            } else {
                TextButton(onClick = {
                    savePasscode(activity, prefs, callsign, passcode, completed = false)
                    activity.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse(activity.getString(R.string.passcode_url))))
                    onDismiss()
                }) {
                    Text(activity.getString(R.string.p_passreq))
                }
            }
        },
    )
}

private fun savePasscode(
    activity: Activity,
    prefs: PrefsWrapper,
    call: String,
    passcode: String,
    completed: Boolean,
) {
    val pe = prefs.prefs.edit()
    val parts = call.split("-")
    when (parts.size) {
        1 -> pe.putString("callsign", parts[0])
        2 -> {
            pe.putString("callsign", parts[0])
            pe.putString("ssid", parts[1])
        }
        else -> {
            Log.d("PasscodeDialog", "could not split callsign")
            activity.finish()
            return
        }
    }
    val passOk = if (passcode != "") AprsPacket.passcodeAllowed(call, passcode, true) else true
    if (passOk) {
        pe.putString("passcode", passcode)
    }
    pe.putBoolean("firstrun", !completed)
    if (completed) {
        pe.putBoolean("permissions_requested", true)
    }
    pe.commit()
}
