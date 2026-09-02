package com.dstwr.flow.data.apps

import com.dstwr.flow.data.local.AppPolicyEntity
import com.dstwr.flow.data.local.FlowDatabase
import com.dstwr.flow.domain.model.AppPolicy
import com.dstwr.flow.domain.model.NetworkScope

class AppPolicyRepository(private val database: FlowDatabase) {
    private val dao = database.appPolicyDao()

    suspend fun getAll(): List<AppPolicy> = dao.getAll().map { it.toDomain() }

    suspend fun get(packageName: String): AppPolicy? = dao.get(packageName)?.toDomain()

    suspend fun setBlocked(packageName: String, blocked: Boolean) {
        update(packageName) { copy(blocked = blocked) }
    }

    suspend fun setSpeedLimits(
        packageName: String,
        downloadBytesPerSecond: Long,
        uploadBytesPerSecond: Long
    ) {
        update(packageName) {
            copy(
                downloadLimitBytesPerSecond = downloadBytesPerSecond.coerceAtLeast(0L),
                uploadLimitBytesPerSecond = uploadBytesPerSecond.coerceAtLeast(0L)
            )
        }
    }

    suspend fun setQuotas(
        packageName: String,
        dailyBytes: Long,
        monthlyBytes: Long
    ) {
        update(packageName) {
            copy(
                dailyQuotaBytes = dailyBytes.coerceAtLeast(0L),
                monthlyQuotaBytes = monthlyBytes.coerceAtLeast(0L)
            )
        }
    }

    suspend fun setSchedule(
        packageName: String,
        enabled: Boolean,
        startMinutes: Int,
        endMinutes: Int
    ) {
        update(packageName) {
            copy(
                scheduleEnabled = enabled,
                scheduleStartMinutes = startMinutes.coerceIn(0, 1439),
                scheduleEndMinutes = endMinutes.coerceIn(0, 1439)
            )
        }
    }

    suspend fun setNetworkScope(packageName: String, scope: NetworkScope) {
        update(packageName) { copy(networkScope = scope) }
    }

    suspend fun delete(packageName: String) = dao.delete(packageName)

    private suspend fun update(
        packageName: String,
        transform: AppPolicyEntity.() -> AppPolicyEntity
    ) {
        val current = dao.get(packageName) ?: AppPolicyEntity(packageName = packageName)
        dao.upsert(transform(current).copy(updatedAt = System.currentTimeMillis()))
    }

    private fun AppPolicyEntity.toDomain() = AppPolicy(
        packageName = packageName,
        blocked = blocked,
        downloadLimitBytesPerSecond = downloadLimitBytesPerSecond,
        uploadLimitBytesPerSecond = uploadLimitBytesPerSecond,
        dailyQuotaBytes = dailyQuotaBytes,
        monthlyQuotaBytes = monthlyQuotaBytes,
        scheduleEnabled = scheduleEnabled,
        scheduleStartMinutes = scheduleStartMinutes,
        scheduleEndMinutes = scheduleEndMinutes,
        networkScope = runCatching { NetworkScope.valueOf(networkScope) }.getOrDefault(NetworkScope.ALL)
    )

    private fun AppPolicyEntity.copy(networkScope: NetworkScope): AppPolicyEntity =
        copy(networkScope = networkScope.name)
}
