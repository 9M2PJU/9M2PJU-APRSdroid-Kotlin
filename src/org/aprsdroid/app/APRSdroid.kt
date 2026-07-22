package org.aprsdroid.app

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.aprsdroid.app.ui.theme.AprsTheme

class APRSdroid : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UIHelper.applySystemBarInsets(this)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // If this is a USB device launch and the service is running, auto-start it.
        val device = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("device", Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("device")
        }
        if (UsbTnc.checkDeviceHandle(prefs, device) && prefs.getBoolean("service_running", false)) {
            startService(AprsService.intent(this, AprsService.SERVICE))
        }

        val target = when {
            !prefs.getBoolean("permissions_requested", false) -> FirstRunActivity::class.java
            !AprsService.running && !prefs.getBoolean("service_running", false) -> LogActivity::class.java
            else -> HubActivity::class.java
        }

        setContent {
            AprsTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.icon),
                        contentDescription = getString(R.string.app_name),
                        modifier = Modifier.size(128.dp),
                    )
                }

                LaunchedEffect(Unit) {
                    delay(SPLASH_DELAY_MS)
                    replaceAct(target)
                }
            }
        }
    }

    private fun replaceAct(act: Class<*>) {
        val i = Intent(this, act)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        startActivity(i)
        overridePendingTransition(0, 0)
        finish()
    }

    companion object {
        private const val SPLASH_DELAY_MS = 2000L
    }
}
