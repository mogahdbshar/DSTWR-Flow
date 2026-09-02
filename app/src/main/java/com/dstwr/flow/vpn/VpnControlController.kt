package com.dstwr.flow.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

/**
 * Single entry point for starting and stopping the local VPN controller.
 * The controller never requests VPN consent silently. Consent is handled by
 * the Activity through VpnService.prepare().
 */
class VpnControlController(private val context: Context) {
    fun start(emergencyBlock: Boolean = false) {
        val intent = Intent(context, FlowVpnService::class.java).apply {
            putExtra(FlowVpnService.EXTRA_EMERGENCY, emergencyBlock)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop() {
        val intent = Intent(context, FlowVpnService::class.java).apply {
            action = FlowVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun isPrepared(): Boolean = VpnService.prepare(context) == null
}
