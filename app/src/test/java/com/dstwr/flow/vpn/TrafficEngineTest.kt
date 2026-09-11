package com.dstwr.flow.vpn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TrafficEngineTest {
    @Test
    fun maxPacketSizeMatchesIpv4Maximum() {
        assertEquals(65_535, TrafficEngine.MAX_PACKET_SIZE)
    }

    @Test
    fun transportCanBeClosedSafely() {
        val transport = FakeTransport()
        transport.close()
        transport.close()
        assertTrue(transport.closed)
    }

    private class FakeTransport : TrafficEngine.PacketTransport {
        var closed = false
        override fun forwardUpload(buffer: ByteArray, length: Int, packet: ParsedPacket) = Unit
        override fun readDownload(): ByteArray? = null
        override fun close() { closed = true }
    }
}
