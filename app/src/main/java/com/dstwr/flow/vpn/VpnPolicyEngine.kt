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
import com.dstwr.flow.domain.policy.PolicyAlertPolicy
import com.dstwr.flow.domain.policy.RuntimeApp
import com.dstwr.flow.domain.model.NetworkScope
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
    private val usageRepository = UsageStatsRepository(context)
    private val runtime = AppPolicyRuntimeCoordinator(
        policyRepository = policyRepository,
        usageWindowRepository = UsageWindowRepository(usageRepository)
    )

    suspend fun currentEmergencyState(): Boolean =
        settings.emergencyBlockEnabled.first()

    suspend fun notificationsEnabled(): Boolean =
        settings.notificationsEnabled.first()

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

    suspend fun quotaAlerts(): List<QuotaAlert> = withContext(Dispatchers.IO) {
        val policies = policyRepository.getAll()
            .filter { it.dailyQuotaBytes > 0L || it.monthlyQuotaBytes > 0L }
        if (policies.isEmpty()) return@withContext emptyList()

        val installed = inventory.getLaunchableApps().associateBy { it.packageName }
        val apps = policies.mapNotNull { policy ->
            installed[policy.packageName]?.let { RuntimeApp(it.packageName, it.uid) }
        }
        if (apps.isEmpty()) return@withContext emptyList()

        val decisions = runtime.evaluateAll(apps, emergencyBlock = false)
        decisions.mapNotNull { runtimeDecision ->
            val policy = policies.firstOrNull { it.packageName == runtimeDecision.packageName } ?: return@mapNotNull null
            val app = installed[runtimeDecision.packageName] ?: return@mapNotNull null
            val dailyPercent = PolicyAlertPolicy.percentUsed(
                runtimeDecision.usage.dailyBytesFor(policy.networkScope),
                policy.dailyQuotaBytes
            )
            val monthlyPercent = PolicyAlertPolicy.percentUsed(
                runtimeDecision.usage.monthlyBytesFor(policy.networkScope),
                policy.monthlyQuotaBytes
            )
            val dailyReached = PolicyAlertPolicy.isReached(
                runtimeDecision.usage.dailyBytesFor(policy.networkScope),
                policy.dailyQuotaBytes
            )
            val monthlyReached = PolicyAlertPolicy.isReached(
                runtimeDecision.usage.monthlyBytesFor(policy.networkScope),
                policy.monthlyQuotaBytes
            )
            if (!PolicyAlertPolicy.shouldWarn(
                    runtimeDecision.usage.dailyBytesFor(policy.networkScope), policy.dailyQuotaBytes
                ) && !PolicyAlertPolicy.shouldWarn(
                    runtimeDecision.usage.monthlyBytesFor(policy.networkScope), policy.monthlyQuotaBytes
                ) && !dailyReached && !monthlyReached
            ) return@mapNotNull null

            QuotaAlert(
                packageName = app.packageName,
                appLabel = app.label,
                dailyPercent = dailyPercent,
                monthlyPercent = monthlyPercent,
                dailyReached = dailyReached,
                monthlyReached = monthlyReached,
                dailyQuotaBytes = policy.dailyQuotaBytes,
                monthlyQuotaBytes = policy.monthlyQuotaBytes,
                dailyUsedBytes = runtimeDecision.usage.dailyBytesFor(policy.networkScope),
                monthlyUsedBytes = runtimeDecision.usage.monthlyBytesFor(policy.networkScope)
            )
        }
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

data class QuotaAlert(
    val packageName: String,
    val appLabel: String,
    val dailyPercent: Int,
    val monthlyPercent: Int,
    val dailyReached: Boolean,
    val monthlyReached: Boolean,
    val dailyQuotaBytes: Long,
    val monthlyQuotaBytes: Long,
    val dailyUsedBytes: Long,
    val monthlyUsedBytes: Long
)

private fun com.dstwr.flow.domain.policy.PolicyUsage.dailyBytesFor(scope: NetworkScope): Long = when (scope) {
    NetworkScope.ALL -> dailyBytes
    NetworkScope.WIFI -> wifiBytes
    NetworkScope.MOBILE -> mobileBytes
}

private fun com.dstwr.flow.domain.policy.PolicyUsage.monthlyBytesFor(scope: NetworkScope): Long = when (scope) {
    NetworkScope.ALL -> monthlyBytes
    NetworkScope.WIFI -> monthlyWifiBytes
    NetworkScope.MOBILE -> monthlyMobileBytes
}
