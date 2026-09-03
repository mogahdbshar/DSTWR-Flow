package com.dstwr.flow.vpn

import android.content.Context
import com.dstwr.flow.data.settings.FlowSettingsRepository
import kotlinx.coroutines.flow.first

/** Coordinates persisted protection state with the local VPN service. */
class FlowProtectionController(context: Context) {
    private val appContext = context.applicationContext
    private val settings = FlowSettingsRepository(appContext)
    private val vpn = VpnControlController(appContext)

    suspend fun enableProtection(): Boolean {
        if (!vpn.isPrepared()) {
            settings.setProtectionEnabled(false)
            return false
        }
        settings.setProtectionEnabled(true)
        val emergency = settings.emergencyBlockEnabled.first()
        vpn.start(emergencyBlock = emergency)
        return true
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
        settings.setEmergencyBlockEnabled(enabled)
        val protection = settings.protectionEnabled.first()
        if (!protection) return true
        vpn.start(emergencyBlock = enabled)
        return true
    }

    suspend fun reapply() {
        val protection = settings.protectionEnabled.first()
        val emergency = settings.emergencyBlockEnabled.first()
        if (!protection || !vpn.isPrepared()) {
            if (!protection) vpn.stop()
            return
        }
        vpn.start(emergencyBlock = emergency)
    }

    fun isPrepared(): Boolean = vpn.isPrepared()
}
