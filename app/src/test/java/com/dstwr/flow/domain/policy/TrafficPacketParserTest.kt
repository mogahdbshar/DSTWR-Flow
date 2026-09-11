package com.dstwr.flow.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrafficPacketParserTest {
    @Test
    fun parsesIpv4TcpPorts() {
        val packet = ByteArray(24)
        packet[0] = 0x45
        packet[9] = 6
        packet[20] = 0x1B
        packet[21] = 0x58
        packet[22] = 0x00
        packet[23] = 0x50
        val result = TrafficPacketParser.parse(packet)
        assertEquals(4, result?.ipVersion)
        assertEquals(TrafficPacket.Protocol.TCP, result?.protocol)
        assertEquals(7000, result?.sourcePort)
        assertEquals(80, result?.destinationPort)
    }

    @Test
    fun parsesIpv4Udp() {
        val packet = ByteArray(28)
        packet[0] = 0x45
        packet[9] = 17
        packet[20] = 0x00
        packet[21] = 53
        packet[22] = 0x30.toByte()
        packet[23] = 0x39
        assertEquals(TrafficPacket.Protocol.UDP, TrafficPacketParser.parse(packet)?.protocol)
        assertEquals(12345, TrafficPacketParser.parse(packet)?.destinationPort)
    }

    @Test
    fun rejectsInvalidPacket() {
        assertNull(TrafficPacketParser.parse(ByteArray(4)))
        assertNull(TrafficPacketParser.parse(byteArrayOf(0x70)))
    }
}
