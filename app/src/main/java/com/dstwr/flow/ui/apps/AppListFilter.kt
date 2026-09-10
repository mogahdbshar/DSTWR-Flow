package com.dstwr.flow.ui.apps

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
            val matchesBlocked = !blockedOnly || row.policy.blocked
            val matchesConfigured = !configuredOnly || row.policy != com.dstwr.flow.domain.model.AppPolicy(row.app.packageName)
            matchesQuery && matchesBlocked && matchesConfigured
        }
    }
}
