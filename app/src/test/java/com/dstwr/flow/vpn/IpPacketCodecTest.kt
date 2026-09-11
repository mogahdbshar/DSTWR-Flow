package com.dstwr.flow.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class IpPacketCodecTest {
    @Test
    fun ipv4TcpRoundTripPreservesHeaderAndPayload() {
        val payload = "hello".toByteArray()
        val packet = IpPacketCodec.tcp(
            ipVersion = 4,
            source = "10.10.0.2",
            destination = "1.1.1.1",
            sourcePort = 45000,
            destinationPort = 443,
            sequence = 100L,
            acknowledgement = 200L,
            flags = IpPacketCodec.TCP_PSH or IpPacketCodec.TCP_ACK,
            payload = payload
        )

        val decoded = IpPacketCodec.decode(packet, packet.size)
        requireNotNull(decoded)
        assertEquals(4, decoded.ipVersion)
        assertEquals(6, decoded.protocol)
        assertEquals(45000, decoded.sourcePort)
        assertEquals(443, decoded.destinationPort)
        assertEquals(100L, decoded.sequence)
        assertEquals(200L, decoded.acknowledgement)
        assertEquals(IpPacketCodec.TCP_PSH or IpPacketCodec.TCP_ACK, decoded.flags)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun ipv6UdpRoundTripPreservesPayload() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val packet = IpPacketCodec.udp(
            ipVersion = 6,
            source = "fd00:dstwr:flow::2",
            destination = "2606:4700:4700::1111",
            sourcePort = 53000,
            destinationPort = 53,
            payload = payload
        )

        val decoded = IpPacketCodec.decode(packet, packet.size)
        requireNotNull(decoded)
        assertEquals(6, decoded.ipVersion)
        assertEquals(17, decoded.protocol)
        assertEquals(53000, decoded.sourcePort)
        assertEquals(53, decoded.destinationPort)
        assertArrayEquals(payload, decoded.payload)
    }
}
