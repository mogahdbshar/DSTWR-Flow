package com.dstwr.flow.domain.policy

import com.dstwr.flow.domain.model.AppPolicy

/**
 * Pure decision engine for application traffic policies.
 *
 * Schedule is defined as a blocking window. A zero quota means unlimited.
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
        dailyUsageBytes: Long,
        monthlyUsageBytes: Long,
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

        if (policy.dailyQuotaBytes > 0L && dailyUsageBytes >= policy.dailyQuotaBytes) {
            return Decision(true, BlockReason.DAILY_QUOTA)
        }

        if (policy.monthlyQuotaBytes > 0L && monthlyUsageBytes >= policy.monthlyQuotaBytes) {
            return Decision(true, BlockReason.MONTHLY_QUOTA)
        }

        return Decision(
            blocked = false,
            downloadLimitBytesPerSecond = policy.downloadLimitBytesPerSecond.coerceAtLeast(0L),
            uploadLimitBytesPerSecond = policy.uploadLimitBytesPerSecond.coerceAtLeast(0L)
        )
    }
}
