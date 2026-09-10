package com.dstwr.flow.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyTimeFormatterTest {
    @Test
    fun formatsMidnight() {
        assertEquals("00:00", PolicyTimeFormatter.format(0))
    }

    @Test
    fun formatsRegularTime() {
        assertEquals("07:05", PolicyTimeFormatter.format(425))
        assertEquals("23:59", PolicyTimeFormatter.format(1439))
    }

    @Test
    fun clampsInvalidMinutes() {
        assertEquals("00:00", PolicyTimeFormatter.format(-1))
        assertEquals("23:59", PolicyTimeFormatter.format(2000))
    }
}
