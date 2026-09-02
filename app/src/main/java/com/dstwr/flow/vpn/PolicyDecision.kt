package com.dstwr.flow.vpn

import com.dstwr.flow.domain.model.AppPolicy

/** Result of evaluating one application's current network policy. */
data class PolicyDecision(
    val packageName: String,
    val blocked: Boolean,
    val reason: Reason
) {
    enum class Reason {
        NONE,
        MANUAL_BLOCK,
        SCHEDULE,
        DAILY_QUOTA,
        MONTHLY_QUOTA,
        EMERGENCY
    }
}

class PolicyEvaluator {
    fun evaluate(
        policy: AppPolicy?,
        nowMinutes: Int,
        dailyUsedBytes: Long,
        monthlyUsedBytes: Long,
        emergencyBlock: Boolean
    ): PolicyDecision {
        val packageName = policy?.packageName.orEmpty()
        if (emergencyBlock) return PolicyDecision(packageName, true, PolicyDecision.Reason.EMERGENCY)
        if (policy == null) return PolicyDecision(packageName, false, PolicyDecision.Reason.NONE)
        if (policy.blocked) return PolicyDecision(packageName, true, PolicyDecision.Reason.MANUAL_BLOCK)
        if (policy.scheduleEnabled && isWithinSchedule(policy.scheduleStartMinutes, policy.scheduleEndMinutes, nowMinutes)) {
            return PolicyDecision(packageName, true, PolicyDecision.Reason.SCHEDULE)
        }
        if (policy.dailyQuotaBytes > 0 && dailyUsedBytes >= policy.dailyQuotaBytes) {
            return PolicyDecision(packageName, true, PolicyDecision.Reason.DAILY_QUOTA)
        }
        if (policy.monthlyQuotaBytes > 0 && monthlyUsedBytes >= policy.monthlyQuotaBytes) {
            return PolicyDecision(packageName, true, PolicyDecision.Reason.MONTHLY_QUOTA)
        }
        return PolicyDecision(packageName, false, PolicyDecision.Reason.NONE)
    }

    fun isWithinSchedule(start: Int, end: Int, now: Int): Boolean {
        val s = start.coerceIn(0, 1439)
        val e = end.coerceIn(0, 1439)
        val n = now.coerceIn(0, 1439)
        return if (s <= e) n in s..e else n >= s || n <= e
    }
}
