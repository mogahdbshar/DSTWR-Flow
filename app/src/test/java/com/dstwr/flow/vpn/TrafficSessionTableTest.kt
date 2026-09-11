package com.dstwr.flow.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrafficSessionTableTest {
    private val flow = TrafficFlowKey(4, 6, "10.0.0.2", 50000, "1.1.1.1", 443)

    @Test
    fun resolvesOriginalAndReversedFlow() {
        val table = TrafficSessionTable()
        table.bind(flow, "com.example.app", nowMillis = 1_000L)

        assertEquals("com.example.app", table.resolve(flow, nowMillis = 1_001L))
        assertEquals("com.example.app", table.resolve(flow.reversed(), nowMillis = 1_002L))
    }

    @Test
    fun expiredFlowIsRemoved() {
        val table = TrafficSessionTable(idleTimeoutMillis = 100L)
        table.bind(flow, "com.example.app", nowMillis = 1_000L)

        assertNull(table.resolve(flow, nowMillis = 1_101L))
        assertEquals(0, table.size())
    }

    @Test
    fun blankPackageIsIgnored() {
        val table = TrafficSessionTable()
        table.bind(flow, "   ", nowMillis = 1_000L)

        assertNull(table.resolve(flow, nowMillis = 1_001L))
        assertEquals(0, table.size())
    }

    @Test
    fun removeClearsBothDirections() {
        val table = TrafficSessionTable()
        table.bind(flow, "com.example.app", nowMillis = 1_000L)
        table.remove(flow.reversed())

        assertNull(table.resolve(flow, nowMillis = 1_001L))
        assertEquals(0, table.size())
    }
}
