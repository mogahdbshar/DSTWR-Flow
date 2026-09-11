package com.dstwr.flow.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedLimitTest {
    @Test
    fun convertsAllSupportedUnits() {
        assertEquals(1024L, SpeedLimit(1, SpeedUnit.KBPS).bytesPerSecond)
        assertEquals(1024L * 1024L, SpeedLimit(1, SpeedUnit.MBPS).bytesPerSecond)
        assertEquals(1024L * 1024L * 1024L, SpeedLimit(1, SpeedUnit.GBPS).bytesPerSecond)
    }

    @Test
    fun zeroDisablesLimit() {
        assertTrue(!SpeedLimit().enabled)
        assertEquals(0L, SpeedLimit().bytesPerSecond)
    }

    @Test
    fun overflowIsCapped() {
        assertEquals(Long.MAX_VALUE, SpeedLimit(Long.MAX_VALUE, SpeedUnit.GBPS).bytesPerSecond)
    }
}
