package com.dstwr.flow.domain.policy

import com.dstwr.flow.domain.model.AppPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyUsageRequirementTest {
    @Test
    fun nullPolicyDoesNotNeedCounters() {
        assertFalse(PolicyUsageRequirement.needsCounters(null))
    }

    @Test
    fun policyWithoutQuotasDoesNotNeedCounters() {
        assertFalse(PolicyUsageRequirement.needsCounters(AppPolicy(packageName = "com.example.app")))
    }

    @Test
    fun dailyQuotaNeedsCounters() {
        assertTrue(
            PolicyUsageRequirement.needsCounters(
                AppPolicy(packageName = "com.example.app", dailyQuotaBytes = 1L)
            )
        )
    }

    @Test
    fun monthlyQuotaNeedsCounters() {
        assertTrue(
            PolicyUsageRequirement.needsCounters(
                AppPolicy(packageName = "com.example.app", monthlyQuotaBytes = 1L)
            )
        )
    }
}
