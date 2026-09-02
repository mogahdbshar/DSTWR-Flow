package com.dstwr.flow

import com.dstwr.flow.domain.model.AppPolicy
import com.dstwr.flow.vpn.PolicyEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyEvaluatorTest {
    private val evaluator = PolicyEvaluator()

    @Test fun manualBlockWins() {
        val result = evaluator.evaluate(AppPolicy("com.test", blocked = true), 600, 0, 0, false)
        assertTrue(result.blocked)
        assertEquals(PolicyEvaluator.PolicyDecision.Reason.MANUAL_BLOCK, result.reason)
    }

    @Test fun scheduleHandlesMidnight() {
        assertTrue(evaluator.isWithinSchedule(23 * 60, 30, 10))
        assertFalse(evaluator.isWithinSchedule(23 * 60, 30, 120))
    }

    @Test fun dailyQuotaBlocksAfterLimit() {
        val result = evaluator.evaluate(AppPolicy("com.test", dailyQuotaBytes = 1000), 600, 1000, 0, false)
        assertTrue(result.blocked)
        assertEquals(PolicyEvaluator.PolicyDecision.Reason.DAILY_QUOTA, result.reason)
    }

    @Test fun emergencyBlocksWithoutPolicy() {
        val result = evaluator.evaluate(null, 600, 0, 0, true)
        assertTrue(result.blocked)
        assertEquals(PolicyEvaluator.PolicyDecision.Reason.EMERGENCY, result.reason)
    }
}
