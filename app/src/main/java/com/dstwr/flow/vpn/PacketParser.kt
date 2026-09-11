package com.dstwr.flow.vpn

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Lightweight IPv4/IPv6 parser. It never allocates while inspecting addresses. */
object PacketParser {
    fun parse(buffer: ByteArray, length: Int): ParsedPacket? {
        if (length < 20) return null
        val version = (buffer[0].toInt() ushr 4) and 0x0f
        return when (version) {
            4 -> parseIpv4(buffer, length)
            6 -> parseIpv6(buffer, length)
            else -> null
        }
    }

    private fun parseIpv4(b: ByteArray, length: Int): ParsedPacket? {
        val ihl = (b[0].toInt() and 0x0f) * 4
        if (ihl < 20 || length < ihl) return null
        val total = u16(b, 2).coerceAtMost(length)
        if (total < ihl) return null
        val protocol = b[9].toInt() and 0xff
        val source = address4(b, 12)
        val destination = address4(b, 16)
        val (srcPort, dstPort, headerBytes) = ports(b, ihl, total, protocol)
        return ParsedPacket(4, protocol, source, destination, srcPort, dstPort, (total - headerBytes).coerceAtLeast(0), total)
    }

    private fun parseIpv6(b: ByteArray, length: Int): ParsedPacket? {
        if (length < 40) return null
        val payloadLength = u16(b, 4)
        val total = (40 + payloadLength).coerceAtMost(length)
        if (total < 40) return null
        var next = b[6].toInt() and 0xff
        var offset = 40
        var guard = 0
        while (next in EXTENSION_HEADERS && offset + 2 <= total && guard++ < 16) {
            val nextHeader = b[offset].toInt() and 0xff
            val size = when (next) {
                44 -> 8
                51 -> ((b[offset + 1].toInt() and 0xff) + 2) * 4
                else -> ((b[offset + 1].toInt() and 0xff) + 1) * 8
            }
            if (size <= 0 || offset + size > total) return null
            next = nextHeader
            offset += size
        }
        val source = address6(b, 8)
        val destination = address6(b, 24)
        val (srcPort, dstPort, headerBytes) = ports(b, offset, total, next)
        return ParsedPacket(6, next, source, destination, srcPort, dstPort, (total - headerBytes).coerceAtLeast(0), total)
    }

    private fun ports(b: ByteArray, offset: Int, total: Int, protocol: Int): Triple<Int, Int, Int> {
        if ((protocol == 6 || protocol == 17) && offset + 4 <= total) {
            return Triple(u16(b, offset), u16(b, offset + 2), offset)
        }
        return Triple(0, 0, offset)
    }

    private fun address4(b: ByteArray, offset: Int): String =
        "${b[offset].toInt() and 255}.${b[offset + 1].toInt() and 255}.${b[offset + 2].toInt() and 255}.${b[offset + 3].toInt() and 255}"

    private fun address6(b: ByteArray, offset: Int): String {
        val bytes = b.copyOfRange(offset, offset + 16)
        return InetAddress.getByAddress(bytes).hostAddress ?: "::"
    }

    private fun u16(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 255) shl 8) or (b[offset + 1].toInt() and 255)

    private val EXTENSION_HEADERS = setOf(0, 43, 44, 50, 51, 60)
}
