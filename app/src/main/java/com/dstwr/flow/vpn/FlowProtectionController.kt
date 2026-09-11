package com.dstwr.flow.vpn

import android.content.Context
import com.dstwr.flow.data.settings.FlowSettingsRepository

/** Coordinates persisted protection state with the local VPN service. */
class FlowProtectionController(context: Context) {
    private val appContext = context.applicationContext
    private val settings = FlowSettingsRepository(appContext)
    private val vpn = VpnControlController(appContext)

    suspend fun enableProtection(): Boolean {
        if (!vpn.isPrepared()) {
            settings.setProtectionEnabled(false)
            settings.setEmergencyBlockEnabled(false)
            return false
        }

        return runCatching {
            settings.setProtectionEnabled(true)
            val emergency = settings.isEmergencyBlockEnabled()
            vpn.start(emergencyBlock = emergency)
            true
        }.getOrElse {
            settings.setProtectionEnabled(false)
            settings.setEmergencyBlockEnabled(false)
            vpn.stop()
            false
        }
    }

    suspend fun disableProtection() {
        settings.disableAllProtection()
        vpn.stop()
    }

    suspend fun setEmergencyBlock(enabled: Boolean): Boolean {
        if (enabled && !vpn.isPrepared()) {
            settings.setEmergencyBlockEnabled(false)
            return false
        }

        val protection = settings.isProtectionEnabled()
        if (enabled && !protection) {
            settings.setEmergencyBlockEnabled(false)
            return false
        }

        return runCatching {
            settings.setEmergencyBlockEnabled(enabled)
            if (!protection) {
                vpn.stop()
            } else {
                vpn.start(emergencyBlock = enabled)
            }
            true
        }.getOrElse {
            settings.setEmergencyBlockEnabled(false)
            false
        }
    }

    suspend fun reapply() {
        val protection = settings.isProtectionEnabled()
        val emergency = settings.isEmergencyBlockEnabled()

        if (!protection) {
            if (emergency) settings.setEmergencyBlockEnabled(false)
            vpn.stop()
            return
        }

        if (!vpn.isPrepared()) {
            settings.setProtectionEnabled(false)
            settings.setEmergencyBlockEnabled(false)
            vpn.stop()
            return
        }

        runCatching { vpn.start(emergencyBlock = emergency) }
            .onFailure {
                settings.setProtectionEnabled(false)
                settings.setEmergencyBlockEnabled(false)
                vpn.stop()
            }
    }

    fun isPrepared(): Boolean = vpn.isPrepared()
}
