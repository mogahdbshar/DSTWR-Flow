package com.dstwr.flow.domain.policy

import com.dstwr.flow.domain.model.AppPolicy
import com.dstwr.flow.domain.model.NetworkScope

/**
 * Pure decision engine for application traffic policies.
 *
 * Schedule is a blocking window. A zero quota means unlimited.
 * Quotas respect the selected network scope.
 */
class AppPolicyEvaluator {
    enum class BlockReason {
        MANUAL,
        SCHEDULE,
        DAILY_QUOTA,
        MONTHLY_QUOTA,
        EMERGENCY
    }

    data class Decision(
        val blocked: Boolean,
        val reason: BlockReason? = null,
        val downloadLimitBytesPerSecond: Long = 0L,
        val uploadLimitBytesPerSecond: Long = 0L
    )

    fun evaluate(
        policy: AppPolicy?,
        minuteOfDay: Int,
        usage: PolicyUsage,
        emergencyBlock: Boolean
    ): Decision {
        if (emergencyBlock) return Decision(true, BlockReason.EMERGENCY)
        if (policy == null) return Decision(false)
        if (policy.blocked) return Decision(true, BlockReason.MANUAL)

        if (policy.scheduleEnabled && PolicySchedule.isActive(
                minuteOfDay,
                true,
                policy.scheduleStartMinutes,
                policy.scheduleEndMinutes
            )
        ) {
            return Decision(true, BlockReason.SCHEDULE)
        }

        val scopedDaily = usage.dailyBytesFor(policy.networkScope)
        val scopedMonthly = usage.monthlyBytesFor(policy.networkScope)

        if (policy.dailyQuotaBytes > 0L && scopedDaily >= policy.dailyQuotaBytes) {
            return Decision(true, BlockReason.DAILY_QUOTA)
        }

        if (policy.monthlyQuotaBytes > 0L && scopedMonthly >= policy.monthlyQuotaBytes) {
            return Decision(true, BlockReason.MONTHLY_QUOTA)
        }

        return Decision(
            blocked = false,
            downloadLimitBytesPerSecond = policy.downloadLimitBytesPerSecond.coerceAtLeast(0L),
            uploadLimitBytesPerSecond = policy.uploadLimitBytesPerSecond.coerceAtLeast(0L)
        )
    }

    /** Compatibility overload for callers that have only aggregate counters. */
    fun evaluate(
        policy: AppPolicy?,
        minuteOfDay: Int,
        dailyUsageBytes: Long,
        monthlyUsageBytes: Long,
        emergencyBlock: Boolean
    ): Decision = evaluate(
        policy = policy,
        minuteOfDay = minuteOfDay,
        usage = PolicyUsage(
            dailyBytes = dailyUsageBytes.coerceAtLeast(0L),
            monthlyBytes = monthlyUsageBytes.coerceAtLeast(0L)
        ),
        emergencyBlock = emergencyBlock
    )

    private fun PolicyUsage.dailyBytesFor(scope: NetworkScope): Long = when (scope) {
        NetworkScope.ALL -> dailyBytes.coerceAtLeast(0L)
        NetworkScope.WIFI -> wifiBytes.coerceAtLeast(0L)
        NetworkScope.MOBILE -> mobileBytes.coerceAtLeast(0L)
    }

    private fun PolicyUsage.monthlyBytesFor(scope: NetworkScope): Long = when (scope) {
        NetworkScope.ALL -> monthlyBytes.coerceAtLeast(0L)
        NetworkScope.WIFI -> monthlyWifiBytes.coerceAtLeast(0L)
        NetworkScope.MOBILE -> monthlyMobileBytes.coerceAtLeast(0L)
    }
}
