package com.dstwr.flow.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PacketParserBoundaryTest {
    @Test
    fun rejectsIpv4PacketWhoseDeclaredLengthExceedsBuffer() {
        val packet = ByteArray(20)
        packet[0] = 0x45
        packet[2] = 0
        packet[3] = 40

        assertNull(PacketParser.parse(packet, packet.size))
    }

    @Test
    fun rejectsIpv6PacketWhoseDeclaredPayloadExceedsBuffer() {
        val packet = ByteArray(40)
        packet[0] = 0x60
        packet[4] = 0
        packet[5] = 20

        assertNull(PacketParser.parse(packet, packet.size))
    }

    @Test
    fun parsesMinimalIpv4UdpPacket() {
        val packet = ByteArray(28)
        packet[0] = 0x45
        packet[2] = 0
        packet[3] = 28
        packet[9] = 17
        packet[12] = 10
        packet[13] = 0
        packet[14] = 0
        packet[15] = 2
        packet[16] = 8
        packet[17] = 8
        packet[18] = 8
        packet[19] = 8
        packet[20] = 0x13
        packet[21] = (0x88).toByte()
        packet[22] = 0
        packet[23] = 53

        val parsed = PacketParser.parse(packet, packet.size)
        assertEquals(4, parsed?.ipVersion)
        assertEquals(17, parsed?.protocol)
        assertEquals(5000, parsed?.sourcePort)
        assertEquals(53, parsed?.destinationPort)
    }
}
