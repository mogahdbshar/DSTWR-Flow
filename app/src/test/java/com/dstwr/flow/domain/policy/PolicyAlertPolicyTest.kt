package com.dstwr.flow.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyAlertPolicyTest {
    @Test
    fun disabledQuotaHasZeroPercent() {
        assertEquals(0, PolicyAlertPolicy.percentUsed(500L, 0L))
        assertFalse(PolicyAlertPolicy.shouldWarn(500L, 0L))
        assertFalse(PolicyAlertPolicy.isReached(500L, 0L))
    }

    @Test
    fun warningStartsAtEightyPercent() {
        assertEquals(80, PolicyAlertPolicy.percentUsed(800L, 1_000L))
        assertTrue(PolicyAlertPolicy.shouldWarn(800L, 1_000L))
    }

    @Test
    fun reachedQuotaIsNotWarning() {
        assertTrue(PolicyAlertPolicy.isReached(1_000L, 1_000L))
        assertFalse(PolicyAlertPolicy.shouldWarn(1_000L, 1_000L))
    }

    @Test
    fun percentIsClampedToHundred() {
        assertEquals(100, PolicyAlertPolicy.percentUsed(2_000L, 1_000L))
    }

    @Test
    fun negativeUsageIsSafe() {
        assertEquals(0, PolicyAlertPolicy.percentUsed(-1L, 1_000L))
        assertFalse(PolicyAlertPolicy.shouldWarn(-1L, 1_000L))
    }
}
