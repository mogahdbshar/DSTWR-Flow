package com.dstwr.flow.ui.apps

import com.dstwr.flow.domain.model.NetworkScope

/** Pure filtering used by the applications screen. */
object AppListFilter {
    fun filter(
        apps: List<AppRow>,
        query: String,
        blockedOnly: Boolean = false,
        configuredOnly: Boolean = false
    ): List<AppRow> {
        val normalized = query.trim().lowercase()
        return apps.filter { row ->
            val matchesQuery = normalized.isEmpty() ||
                row.app.label.lowercase().contains(normalized) ||
                row.app.packageName.lowercase().contains(normalized)

            val policy = row.policy
            val matchesBlocked = !blockedOnly || policy.blocked
            val matchesConfigured = !configuredOnly || (
                policy.blocked ||
                    policy.downloadLimitBytesPerSecond > 0L ||
                    policy.uploadLimitBytesPerSecond > 0L ||
                    policy.dailyQuotaBytes > 0L ||
                    policy.monthlyQuotaBytes > 0L ||
                    policy.scheduleEnabled ||
                    policy.networkScope != NetworkScope.ALL
            )

            matchesQuery && matchesBlocked && matchesConfigured
        }
    }
}
