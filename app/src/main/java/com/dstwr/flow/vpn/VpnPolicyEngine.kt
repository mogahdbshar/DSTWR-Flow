package com.dstwr.flow.vpn

import android.content.Context
import android.net.VpnService
import com.dstwr.flow.data.local.FlowDatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds the local VPN policy from the locally stored app policies.
 *
 * The current routing mode intentionally implements only blocking:
 * blocked applications are routed into the local VPN tunnel, where there is
 * currently no upstream forwarding engine, so their traffic is stopped.
 * Applications not listed as blocked stay outside the VPN and keep using the
 * normal Android network path.
 */
class VpnPolicyEngine(private val context: Context) {
    private val database = FlowDatabaseProvider.get(context)

    suspend fun blockedPackages(): List<String> = withContext(Dispatchers.IO) {
        database.appPolicyDao().getAll()
            .filter { it.blocked }
            .map { it.packageName }
    }

    fun buildBlockingTunnel(
        blockedPackages: List<String>,
        emergencyBlock: Boolean
    ): VpnService.Builder {
        val builder = VpnService.Builder()
            .setSession("DSTWR Flow")
            .setMtu(1500)
            .addAddress("10.10.0.2", 32)
            .addRoute("0.0.0.0", 0)

        if (!emergencyBlock) {
            blockedPackages.forEach { packageName ->
                try {
                    builder.addAllowedApplication(packageName)
                } catch (_: Exception) {
                    // The package may have been uninstalled between refreshes.
                }
            }
        }

        return builder
    }
}
