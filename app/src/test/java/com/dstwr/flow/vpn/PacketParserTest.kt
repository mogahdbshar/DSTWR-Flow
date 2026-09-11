package com.dstwr.flow.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PacketParserTest {
    @Test
    fun parsesIpv4TcpPorts() {
        val p = ByteArray(40)
        p[0] = 0x45
        p[2] = 0
        p[3] = 40
        p[9] = 6
        p[12] = 10; p[13] = 0; p[14] = 0; p[15] = 2
        p[16] = 8; p[17] = 8; p[18] = 8; p[19] = 8
        p[20] = 0x1F; p[21] = 0x90
        p[22] = 0x00; p[23] = 0x35
        val parsed = PacketParser.parse(p, p.size)
        assertNotNull(parsed)
        assertEquals(4, parsed!!.ipVersion)
        assertEquals(6, parsed.protocol)
        assertEquals(8080, parsed.sourcePort)
        assertEquals(53, parsed.destinationPort)
    }

    @Test
    fun rejectsUnsupportedVersion() {
        val p = ByteArray(40)
        p[0] = 0x75
        assertNull(PacketParser.parse(p, p.size))
    }
}
