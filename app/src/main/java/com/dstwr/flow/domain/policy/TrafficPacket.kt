package com.dstwr.flow.domain.policy

/** Lightweight packet metadata extracted from an IPv4/IPv6 packet on the TUN interface. */
data class TrafficPacket(
    val ipVersion: Int,
    val protocol: Protocol,
    val sourcePort: Int = 0,
    val destinationPort: Int = 0,
    val payloadBytes: Int = 0,
    val totalBytes: Int = 0
) {
    enum class Protocol { TCP, UDP, ICMP, OTHER }
}

object TrafficPacketParser {
    fun parse(packet: ByteArray, length: Int = packet.size): TrafficPacket? {
        if (length < 1) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F
        return when (version) {
            4 -> parseIpv4(packet, length)
            6 -> parseIpv6(packet, length)
            else -> null
        }
    }

    private fun parseIpv4(packet: ByteArray, length: Int): TrafficPacket? {
        if (length < 20) return null
        val headerLength = (packet[0].toInt() and 0x0F) * 4
        if (headerLength < 20 || headerLength > length) return null
        val protocol = protocol(packet[9].toInt() and 0xFF)
        val ports = if (protocol == TrafficPacket.Protocol.TCP || protocol == TrafficPacket.Protocol.UDP) {
            if (length < headerLength + 4) return null
            ((u16(packet, headerLength) to u16(packet, headerLength + 2)))
        } else 0 to 0
        return TrafficPacket(4, protocol, ports.first, ports.second, (length - headerLength).coerceAtLeast(0), length)
    }

    private fun parseIpv6(packet: ByteArray, length: Int): TrafficPacket? {
        if (length < 40) return null
        var nextHeader = packet[6].toInt() and 0xFF
        var offset = 40
        var guard = 0
        while (nextHeader in EXTENSION_HEADERS && offset + 2 <= length && guard++ < 8) {
            val extensionLength = ((packet[offset + 1].toInt() and 0xFF) + 1) * 8
            nextHeader = packet[offset].toInt() and 0xFF
            offset += extensionLength
            if (offset > length) return null
        }
        val protocol = protocol(nextHeader)
        val ports = if (protocol == TrafficPacket.Protocol.TCP || protocol == TrafficPacket.Protocol.UDP) {
            if (length < offset + 4) return null
            u16(packet, offset) to u16(packet, offset + 2)
        } else 0 to 0
        return TrafficPacket(6, protocol, ports.first, ports.second, (length - offset).coerceAtLeast(0), length)
    }

    private fun protocol(value: Int): TrafficPacket.Protocol = when (value) {
        6 -> TrafficPacket.Protocol.TCP
        17 -> TrafficPacket.Protocol.UDP
        1, 58 -> TrafficPacket.Protocol.ICMP
        else -> TrafficPacket.Protocol.OTHER
    }

    private fun u16(packet: ByteArray, offset: Int): Int =
        ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)

    private val EXTENSION_HEADERS = setOf(0, 43, 44, 50, 51, 60, 135, 139)
}
