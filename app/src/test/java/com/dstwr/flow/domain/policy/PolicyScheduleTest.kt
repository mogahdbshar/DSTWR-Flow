package com.dstwr.flow.domain.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyScheduleTest {
    @Test
    fun normalScheduleIncludesBoundaries() {
        assertTrue(PolicySchedule.isActive(60, true, 60, 120))
        assertTrue(PolicySchedule.isActive(120, true, 60, 120))
        assertFalse(PolicySchedule.isActive(121, true, 60, 120))
    }

    @Test
    fun overnightScheduleCrossesMidnight() {
        assertTrue(PolicySchedule.isActive(23 * 60 + 30, true, 23 * 60, 30))
        assertTrue(PolicySchedule.isActive(15, true, 23 * 60, 30))
        assertFalse(PolicySchedule.isActive(12 * 60, true, 23 * 60, 30))
    }

    @Test
    fun disabledScheduleIsNeverActive() {
        assertFalse(PolicySchedule.isActive(90, false, 60, 120))
    }
}
