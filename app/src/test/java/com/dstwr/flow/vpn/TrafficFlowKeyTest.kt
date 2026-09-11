package com.dstwr.flow.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficFlowKeyTest {
    @Test
    fun reversedSwapsEndpoints() {
        val key = TrafficFlowKey(4, 6, "10.0.0.2", 1234, "1.1.1.1", 443)
        assertEquals("1.1.1.1", key.reversed().sourceAddress)
        assertEquals(443, key.reversed().sourcePort)
        assertEquals("10.0.0.2", key.reversed().destinationAddress)
        assertEquals(1234, key.reversed().destinationPort)
    }

    @Test
    fun packetConvertsToStableKey() {
        val packet = ParsedPacket(4, 17, "10.0.0.2", "8.8.8.8", 5000, 53, 20, 48)
        assertEquals(packet.ipVersion, packet.toFlowKey().ipVersion)
        assertEquals(packet.protocol, packet.toFlowKey().protocol)
        assertEquals(packet.sourcePort, packet.toFlowKey().sourcePort)
        assertEquals(packet.destinationPort, packet.toFlowKey().destinationPort)
    }
}
