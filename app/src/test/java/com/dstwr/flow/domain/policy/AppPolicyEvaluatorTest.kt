package com.dstwr.flow.domain.policy

import com.dstwr.flow.domain.model.AppPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPolicyEvaluatorTest {
    private val evaluator = AppPolicyEvaluator()
    private val base = AppPolicy(packageName = "com.test")

    @Test
    fun manualBlockHasPriority() {
        val result = evaluator.evaluate(base.copy(blocked = true), 500, 0, 0, false)
        assertTrue(result.blocked)
        assertEquals(AppPolicyEvaluator.BlockReason.MANUAL, result.reason)
    }

    @Test
    fun scheduleBlocksInsideWindow() {
        val result = evaluator.evaluate(base.copy(scheduleEnabled = true, scheduleStartMinutes = 60, scheduleEndMinutes = 120), 90, 0, 0, false)
        assertTrue(result.blocked)
        assertEquals(AppPolicyEvaluator.BlockReason.SCHEDULE, result.reason)
    }

    @Test
    fun overnightScheduleBlocksAcrossMidnight() {
        val policy = base.copy(scheduleEnabled = true, scheduleStartMinutes = 23 * 60, scheduleEndMinutes = 30)
        assertTrue(evaluator.evaluate(policy, 23 * 60 + 30, 0, 0, false).blocked)
        assertTrue(evaluator.evaluate(policy, 15, 0, 0, false).blocked)
        assertFalse(evaluator.evaluate(policy, 12 * 60, 0, 0, false).blocked)
    }

    @Test
    fun dailyQuotaBlocksAtLimit() {
        val result = evaluator.evaluate(base.copy(dailyQuotaBytes = 1000), 500, 1000, 0, false)
        assertTrue(result.blocked)
        assertEquals(AppPolicyEvaluator.BlockReason.DAILY_QUOTA, result.reason)
    }

    @Test
    fun monthlyQuotaBlocksAtLimit() {
        val result = evaluator.evaluate(base.copy(monthlyQuotaBytes = 5000), 500, 0, 5000, false)
        assertTrue(result.blocked)
        assertEquals(AppPolicyEvaluator.BlockReason.MONTHLY_QUOTA, result.reason)
    }

    @Test
    fun limitsPassThroughWhenAllowed() {
        val result = evaluator.evaluate(
            base.copy(downloadLimitBytesPerSecond = 100_000, uploadLimitBytesPerSecond = 25_000),
            500,
            0,
            0,
            false
        )
        assertFalse(result.blocked)
        assertEquals(100_000L, result.downloadLimitBytesPerSecond)
        assertEquals(25_000L, result.uploadLimitBytesPerSecond)
    }

    @Test
    fun emergencyOverridesEverything() {
        val result = evaluator.evaluate(base, 500, 0, 0, true)
        assertTrue(result.blocked)
        assertEquals(AppPolicyEvaluator.BlockReason.EMERGENCY, result.reason)
    }
}
