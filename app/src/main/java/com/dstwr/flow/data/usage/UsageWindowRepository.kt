package com.dstwr.flow.data.usage

import com.dstwr.flow.domain.policy.PolicyTimeWindowFactory

/** Combines Android network counters for the current day and month. */
class UsageWindowRepository(private val usageStatsRepository: UsageStatsRepository) {
    fun queryCurrentWindows(uid: Int, nowMillis: Long = System.currentTimeMillis()): UsageWindowResult {
        val window = PolicyTimeWindowFactory.fromMillis(nowMillis)
        val daily = usageStatsRepository.queryUid(uid, window.startOfDayMillis, nowMillis)
        val monthly = usageStatsRepository.queryUid(uid, window.startOfMonthMillis, nowMillis)
        return UsageWindowResult(daily = daily, monthly = monthly)
    }
}

data class UsageWindowResult(
    val daily: AppUsage,
    val monthly: AppUsage
)
