package com.dstwr.flow.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionTrackerTest {
    private val packet = ParsedPacket(4, 17, "10.0.0.2", "8.8.8.8", 50000, 53, 20, 48)

    @Test
    fun tracksAndAccumulatesFlow() {
        val tracker = ConnectionTracker()
        assertNotNull(tracker.touch(packet, 1_000L))
        val second = tracker.touch(packet.copy(totalBytes = 20), 2_000L)
        assertEquals(68L, second!!.bytes)
        assertEquals(1, tracker.size())
    }

    @Test
    fun expiresIdleFlow() {
        val tracker = ConnectionTracker(idleTimeoutMillis = 100L)
        tracker.touch(packet, 1_000L)
        tracker.touch(packet.copy(sourcePort = 50001), 1_101L)
        assertEquals(1, tracker.size())
    }

    @Test
    fun enforcesConnectionCap() {
        val tracker = ConnectionTracker(maxConnections = 1)
        assertNotNull(tracker.touch(packet, 1_000L))
        assertNull(tracker.touch(packet.copy(sourcePort = 50001), 1_001L))
    }
}
