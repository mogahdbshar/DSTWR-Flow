package com.dstwr.flow.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

/**
 * Single entry point for the local VPN controller.
 * VPN consent is never requested silently here. The Activity owns consent UI.
 */
class VpnControlController(context: Context) {
    private val appContext = context.applicationContext

    fun start(emergencyBlock: Boolean = false) {
        require(isPrepared()) { "VPN consent is required before starting DSTWR Flow" }
        val intent = Intent(appContext, FlowVpnService::class.java).apply {
            putExtra(FlowVpnService.EXTRA_EMERGENCY, emergencyBlock)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    /** Re-evaluates the persisted policy while keeping the controller active. */
    fun apply(emergencyBlock: Boolean = false) {
        require(isPrepared()) { "VPN consent is required before applying DSTWR Flow policy" }
        val intent = Intent(appContext, FlowVpnService::class.java).apply {
            action = FlowVpnService.ACTION_APPLY
            putExtra(FlowVpnService.EXTRA_EMERGENCY, emergencyBlock)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun stop() {
        val intent = Intent(appContext, FlowVpnService::class.java).apply {
            action = FlowVpnService.ACTION_STOP
        }
        appContext.startService(intent)
    }

    fun isPrepared(): Boolean = VpnService.prepare(appContext) == null
}
