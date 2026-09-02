package com.dstwr.flow.data.apps

import com.dstwr.flow.data.local.AppPolicyEntity
import com.dstwr.flow.data.local.FlowDatabase
import com.dstwr.flow.domain.model.AppPolicy

class AppPolicyRepository(private val database: FlowDatabase) {
    private val dao = database.appPolicyDao()

    suspend fun getAll(): List<AppPolicy> = dao.getAll().map { it.toDomain() }

    suspend fun setBlocked(packageName: String, blocked: Boolean) {
        val current = dao.get(packageName)
        dao.upsert(
            (current ?: AppPolicyEntity(packageName = packageName)).copy(
                blocked = blocked,
                updatedAt = System.currentTimeMillis()
            )
        )
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
        scheduleEndMinutes = scheduleEndMinutes
    )
}
