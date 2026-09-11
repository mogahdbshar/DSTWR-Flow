package com.dstwr.flow.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrafficFlowTableTest {
    private val flow = TrafficFlowKey(4, 6, "10.0.0.2", 50000, "1.1.1.1", 443)

    @Test
    fun bindResolvesBothDirections() {
        val table = TrafficFlowTable()
        table.bind(flow, "com.example.app", 1_000L)

        assertEquals("com.example.app", table.find(flow, 1_001L)?.packageName)
        assertEquals("com.example.app", table.find(flow.reversed(), 1_002L)?.packageName)
    }

    @Test
    fun touchWithoutPackagePreservesKnownIdentity() {
        val table = TrafficFlowTable()
        table.bind(flow, "com.example.app", 1_000L)
        table.touch(flow, packageName = null, nowMillis = 1_050L)

        assertEquals("com.example.app", table.find(flow, 1_051L)?.packageName)
    }

    @Test
    fun expiredEntriesDisappear() {
        val table = TrafficFlowTable(idleTimeoutMillis = 100L)
        table.bind(flow, "com.example.app", 1_000L)

        assertNull(table.find(flow, 1_101L))
        assertEquals(0, table.size())
    }

    @Test
    fun invalidConfigurationIsRejected() {
        var rejected = false
        try {
            TrafficFlowTable(maxEntries = 0)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertEquals(true, rejected)
    }
}
