package com.dstwr.flow.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.dstwr.flow.data.settings.FlowSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restores an explicitly enabled local protection state after reboot. */
class FlowBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = FlowSettingsRepository(appContext)
                if (!settings.protectionEnabled.first()) return@launch

                if (!VpnControlController(appContext).isPrepared()) {
                    settings.setProtectionEnabled(false)
                    settings.setEmergencyBlockEnabled(false)
                    return@launch
                }

                val serviceIntent = Intent(appContext, FlowVpnService::class.java).apply {
                    putExtra(
                        FlowVpnService.EXTRA_EMERGENCY,
                        settings.emergencyBlockEnabled.first()
                    )
                }

                runCatching {
                    ContextCompat.startForegroundService(appContext, serviceIntent)
                }.onFailure {
                    // Android may reject foreground-service startup from boot on some versions.
                    // Keep the saved preference disabled rather than leaving a false active state.
                    settings.disableAllProtection()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
