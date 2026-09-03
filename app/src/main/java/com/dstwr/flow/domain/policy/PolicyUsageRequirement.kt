package com.dstwr.flow.domain.policy

import com.dstwr.flow.domain.model.AppPolicy

/** Keeps the decision to query Android usage counters small and testable. */
object PolicyUsageRequirement {
    fun needsCounters(policy: AppPolicy?): Boolean =
        policy != null && (policy.dailyQuotaBytes > 0L || policy.monthlyQuotaBytes > 0L)
}
