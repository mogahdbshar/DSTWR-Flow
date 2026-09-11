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

        return try {
            settings.setProtectionEnabled(true)
            vpn.start(emergencyBlock = settings.isEmergencyBlockEnabled())
            true
        } catch (_: Exception) {
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

        return try {
            settings.setEmergencyBlockEnabled(enabled)
            if (!protection) vpn.stop() else vpn.start(emergencyBlock = enabled)
            true
        } catch (_: Exception) {
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

        try {
            vpn.start(emergencyBlock = emergency)
        } catch (_: Exception) {
            settings.setProtectionEnabled(false)
            settings.setEmergencyBlockEnabled(false)
            vpn.stop()
        }
    }

    fun isPrepared(): Boolean = vpn.isPrepared()
}
