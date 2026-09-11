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

/** Builds the local VPN policy and runtime traffic policy snapshot. */
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

    suspend fun currentEmergencyState(): Boolean = settings.emergencyBlockEnabled.first()
    suspend fun notificationsEnabled(): Boolean = settings.notificationsEnabled.first()

    suspend fun activeBlockedPackages(
        emergencyBlock: Boolean,
        networkType: NetworkState.Type = NetworkState.Type.OTHER
    ): List<String> = withContext(Dispatchers.IO) {
        if (emergencyBlock) return@withContext inventory.getLaunchableApps().map { it.packageName }

        val persistedPolicies = policyRepository.getAll().associateBy { it.packageName }
        if (persistedPolicies.isEmpty()) return@withContext emptyList()
        val installedByPackage = inventory.getLaunchableApps().associateBy { it.packageName }
        val candidates = persistedPolicies.values
            .filter { it.networkScope.matches(networkType) }
            .mapNotNull { policy -> installedByPackage[policy.packageName]?.let { RuntimeApp(it.packageName, it.uid) } }
        if (candidates.isEmpty()) return@withContext emptyList()

        runtime.evaluateAll(candidates, emergencyBlock = false)
            .filter { it.decision.blocked }
            .map { it.packageName }
            .distinct()
    }

    suspend fun managedPackages(emergencyBlock: Boolean): List<String> = withContext(Dispatchers.IO) {
        if (emergencyBlock) return@withContext inventory.getLaunchableApps().map { it.packageName }
        policyRepository.getAll().map { it.packageName }.distinct()
    }

    suspend fun trafficPolicies(
        emergencyBlock: Boolean,
        networkType: NetworkState.Type
    ): List<TrafficPolicySnapshot> = withContext(Dispatchers.IO) {
        val persisted = policyRepository.getAll()
        if (emergencyBlock) {
            return@withContext inventory.getLaunchableApps().map {
                TrafficPolicySnapshot(packageName = it.packageName, blocked = true)
            }
        }

        val blocked = activeBlockedPackages(false, networkType).toSet()
        persisted.map {
            TrafficPolicySnapshot(
                packageName = it.packageName,
                blocked = it.blocked || it.packageName in blocked,
                downloadLimitBytesPerSecond = it.downloadLimitBytesPerSecond,
                uploadLimitBytesPerSecond = it.uploadLimitBytesPerSecond
            )
        }
    }

    suspend fun quotaAlerts(): List<QuotaAlert> = withContext(Dispatchers.IO) {
        val policies = policyRepository.getAll().filter { it.dailyQuotaBytes > 0L || it.monthlyQuotaBytes > 0L }
        if (policies.isEmpty()) return@withContext emptyList()
        val installed = inventory.getLaunchableApps().associateBy { it.packageName }
        val policiesByPackage = policies.associateBy { it.packageName }
        val apps = policies.mapNotNull { policy -> installed[policy.packageName]?.let { RuntimeApp(it.packageName, it.uid) } }
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

    fun buildBlockingTunnel(managedPackages: List<String>, emergencyBlock: Boolean): VpnService.Builder {
        val builder = VpnService.Builder()
            .setSession("DSTWR Flow")
            .setMtu(1500)
            .setBlocking(true)
            .addAddress("10.10.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addAddress("fd00:dstwr:flow::2", 128)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("2606:4700:4700::1111")

        if (!emergencyBlock) {
            managedPackages.forEach { packageName -> runCatching { builder.addAllowedApplication(packageName) } }
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
