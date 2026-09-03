package com.dstwr.flow.vpn

import android.content.Context
import android.net.VpnService
import com.dstwr.flow.data.apps.AppInventoryRepository
import com.dstwr.flow.data.apps.AppPolicyRepository
import com.dstwr.flow.data.local.FlowDatabaseProvider
import com.dstwr.flow.data.settings.FlowSettingsRepository
import com.dstwr.flow.data.usage.UsageStatsRepository
import com.dstwr.flow.data.usage.UsageWindowRepository
import com.dstwr.flow.domain.policy.AppPolicyRuntimeCoordinator
import com.dstwr.flow.domain.policy.RuntimeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Builds the local VPN policy from the current application policy state.
 *
 * The current routing mode intentionally implements only blocking:
 * selected applications are routed into the local VPN tunnel, where there is
 * currently no upstream forwarding engine, so their traffic is stopped.
 * Applications not selected for blocking remain outside the VPN.
 */
class VpnPolicyEngine(private val context: Context) {
    private val database = FlowDatabaseProvider.get(context)
    private val inventory = AppInventoryRepository(context)
    private val policyRepository = AppPolicyRepository(database)
    private val settings = FlowSettingsRepository(context.applicationContext)
    private val runtime = AppPolicyRuntimeCoordinator(
        policyRepository = policyRepository,
        usageWindowRepository = UsageWindowRepository(UsageStatsRepository(context))
    )

    suspend fun currentEmergencyState(): Boolean =
        settings.emergencyBlockEnabled.first()

    suspend fun activeBlockedPackages(emergencyBlock: Boolean): List<String> = withContext(Dispatchers.IO) {
        if (emergencyBlock) {
            return@withContext inventory.getLaunchableApps().map { it.packageName }
        }

        val persistedPolicies = policyRepository.getAll()
            .associateBy { it.packageName }

        if (persistedPolicies.isEmpty()) return@withContext emptyList()

        val installedByPackage = inventory.getLaunchableApps()
            .associateBy { it.packageName }

        val candidates = persistedPolicies.values.mapNotNull { policy ->
            installedByPackage[policy.packageName]?.let { app ->
                RuntimeApp(app.packageName, app.uid)
            }
        }

        if (candidates.isEmpty()) return@withContext emptyList()

        runtime.evaluateAll(candidates, emergencyBlock = false)
            .filter { it.decision.blocked }
            .map { it.packageName }
            .distinct()
    }

    suspend fun blockedPackages(): List<String> = withContext(Dispatchers.IO) {
        policyRepository.getAll()
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
            .addAddress("fd00:dstwr:flow::2", 128)
            .addRoute("::", 0)

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
