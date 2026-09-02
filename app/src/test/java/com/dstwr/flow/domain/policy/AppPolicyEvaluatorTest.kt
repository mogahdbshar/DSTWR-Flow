package com.dstwr.flow.domain.policy

import com.dstwr.flow.domain.model.AppPolicy
import com.dstwr.flow.domain.model.NetworkScope
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
    fun wifiQuotaUsesWifiBytesOnly() {
        val policy = base.copy(dailyQuotaBytes = 1000, networkScope = NetworkScope.WIFI)
        val usage = PolicyUsage(dailyBytes = 5000, monthlyBytes = 5000, wifiBytes = 1000, mobileBytes = 5000)
        val result = evaluator.evaluate(policy, 500, usage, false)
        assertTrue(result.blocked)
        assertEquals(AppPolicyEvaluator.BlockReason.DAILY_QUOTA, result.reason)
    }

    @Test
    fun mobileQuotaIgnoresWifiBytes() {
        val policy = base.copy(dailyQuotaBytes = 1000, networkScope = NetworkScope.MOBILE)
        val usage = PolicyUsage(dailyBytes = 5000, monthlyBytes = 5000, wifiBytes = 5000, mobileBytes = 999)
        val result = evaluator.evaluate(policy, 500, usage, false)
        assertFalse(result.blocked)
    }

    @Test
    fun monthlyScopedQuotaUsesMonthlyNetworkCounter() {
        val policy = base.copy(monthlyQuotaBytes = 2000, networkScope = NetworkScope.MOBILE)
        val usage = PolicyUsage(monthlyBytes = 9000, monthlyMobileBytes = 2000, mobileBytes = 100)
        val result = evaluator.evaluate(policy, 500, usage, false)
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
