package com.dstwr.flow.vpn

import android.content.Context
import android.net.VpnService
import com.dstwr.flow.data.apps.AppInventoryRepository
import com.dstwr.flow.data.apps.AppPolicyRepository
import com.dstwr.flow.data.local.FlowDatabaseProvider
import com.dstwr.flow.data.settings.FlowSettingsRepository
import com.dstwr.flow.data.usage.UsageStatsRepository
import com.dstwr.flow.data.usage.UsageWindowRepository
import com.dstwr.flow.domain.model.NetworkScope
import com.dstwr.flow.domain.policy.AppPolicyRuntimeCoordinator
import com.dstwr.flow.domain.policy.PolicyAlertPolicy
import com.dstwr.flow.domain.policy.RuntimeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Builds the local VPN policy from persisted application rules. */
class VpnPolicyEngine(private val context: Context) {
    private val appContext = context.applicationContext
    private val database = FlowDatabaseProvider.get(appContext)
    private val inventory = AppInventoryRepository(appContext)
    private val policyRepository = AppPolicyRepository(database)
    private val settings = FlowSettingsRepository(appContext)
    private val usageRepository = UsageStatsRepository(appContext)
    private val runtime = AppPolicyRuntimeCoordinator(
        policyRepository = policyRepository,
        usageWindowRepository = UsageWindowRepository(usageRepository)
    )

    suspend fun currentEmergencyState(): Boolean = settings.emergencyBlockEnabled.first()

    suspend fun notificationsEnabled(): Boolean = settings.notificationsEnabled.first()

    suspend fun activeBlockedPackages(
        emergencyBlock: Boolean,
        networkType: NetworkState.Type = NetworkState.Type.OTHER
    ): List<String> = withContext(Dispatchers.IO) {
        if (emergencyBlock) {
            return@withContext inventory.getLaunchableApps().map { it.packageName }
        }

        val persistedPolicies = policyRepository.getAll().associateBy { it.packageName }
        if (persistedPolicies.isEmpty()) return@withContext emptyList()

        val installedByPackage = inventory.getLaunchableApps().associateBy { it.packageName }
        val candidates = persistedPolicies.values
            .filter { it.networkScope.matches(networkType) }
            .mapNotNull { policy ->
                installedByPackage[policy.packageName]?.let { app -> RuntimeApp(app.packageName, app.uid) }
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
        val policiesByPackage = policies.associateBy { it.packageName }
        val apps = policies.mapNotNull { policy ->
            installed[policy.packageName]?.let { RuntimeApp(it.packageName, it.uid) }
        }
        if (apps.isEmpty()) return@withContext emptyList()

        runtime.evaluateAll(apps, emergencyBlock = false).mapNotNull { runtimeDecision ->
            val policy = policiesByPackage[runtimeDecision.packageName] ?: return@mapNotNull null
            val app = installed[runtimeDecision.packageName] ?: return@mapNotNull null
            val dailyUsed = runtimeDecision.usage.dailyBytesFor(policy.networkScope)
            val monthlyUsed = runtimeDecision.usage.monthlyBytesFor(policy.networkScope)
            val dailyPercent = PolicyAlertPolicy.percentUsed(dailyUsed, policy.dailyQuotaBytes)
            val monthlyPercent = PolicyAlertPolicy.percentUsed(monthlyUsed, policy.monthlyQuotaBytes)
            val dailyReached = PolicyAlertPolicy.isReached(dailyUsed, policy.dailyQuotaBytes)
            val monthlyReached = PolicyAlertPolicy.isReached(monthlyUsed, policy.monthlyQuotaBytes)

            if (!PolicyAlertPolicy.shouldWarn(dailyUsed, policy.dailyQuotaBytes) &&
                !PolicyAlertPolicy.shouldWarn(monthlyUsed, policy.monthlyQuotaBytes) &&
                !dailyReached && !monthlyReached
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
                dailyUsedBytes = dailyUsed,
                monthlyUsedBytes = monthlyUsed
            )
        }
    }

    suspend fun blockedPackages(): List<String> = withContext(Dispatchers.IO) {
        policyRepository.getAll().filter { it.blocked }.map { it.packageName }
    }

    fun buildBlockingTunnel(blockedPackages: List<String>, emergencyBlock: Boolean): VpnService.Builder {
        val builder = VpnService.Builder()
            .setSession("DSTWR Flow")
            .setMtu(1500)
            .addAddress("10.10.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addAddress("fd00:dstwr:flow::2", 128)
            .addRoute("::", 0)

        if (!emergencyBlock) {
            blockedPackages.forEach { packageName ->
                runCatching { builder.addAllowedApplication(packageName) }
            }
        }
        return builder
    }

    /**
     * Full-device forwarding tunnel. DSTWR Flow itself is excluded so the native
     * engine's direct outbound sockets cannot be routed back into its own TUN.
     */
    fun buildForwardingTunnel(): VpnService.Builder = VpnService.Builder()
        .setSession("DSTWR Flow")
        .setMtu(1500)
        .addAddress("10.10.0.2", 32)
        .addRoute("0.0.0.0", 0)
        .addAddress("fd00:dstwr:flow::2", 128)
        .addRoute("::", 0)
        .also { builder ->
            runCatching { builder.addDisallowedApplication(appContext.packageName) }
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

private fun NetworkScope.matches(type: NetworkState.Type): Boolean = when (this) {
    NetworkScope.ALL -> true
    NetworkScope.WIFI -> type == NetworkState.Type.WIFI
    NetworkScope.MOBILE -> type == NetworkState.Type.MOBILE
}

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
