package com.dstwr.flow.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficDirectionTest {
    @Test
    fun directionsAreExplicitAndStable() {
        assertEquals(listOf("UPLOAD", "DOWNLOAD"), TrafficDirection.entries.map { it.name })
    }
}
