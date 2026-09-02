package com.dstwr.flow.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

/**
 * Local VPN foundation for DSTWR Flow.
 *
 * The service is intentionally local: no remote VPN server is used.
 * Traffic filtering, accounting and rule evaluation will be added in the
 * traffic engine layer without requiring root access.
 */
class FlowVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        if (vpnInterface == null) {
            vpnInterface = Builder()
                .setSession("DSTWR Flow")
                .setMtu(1500)
                .addAddress("10.10.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .establish()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun stopVpn() {
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
    }

    companion object {
        const val ACTION_STOP = "com.dstwr.flow.action.STOP_VPN"
    }
}
