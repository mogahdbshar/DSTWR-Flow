package com.dstwr.flow.vpn

import java.net.InetAddress
import kotlin.random.Random

/** Low-level IPv4/IPv6 TCP/UDP packet codec used by the local forwarder. */
object IpPacketCodec {
    data class TransportPacket(
        val ipVersion: Int,
        val protocol: Int,
        val sourceAddress: String,
        val destinationAddress: String,
        val sourcePort: Int,
        val destinationPort: Int,
        val sequence: Long = 0L,
        val acknowledgement: Long = 0L,
        val flags: Int = 0,
        val window: Int = 65535,
        val payload: ByteArray = ByteArray(0)
    )

    const val TCP_FIN = 0x01
    const val TCP_SYN = 0x02
    const val TCP_RST = 0x04
    const val TCP_PSH = 0x08
    const val TCP_ACK = 0x10

    fun decode(buffer: ByteArray, length: Int): TransportPacket? {
        val parsed = PacketParser.parse(buffer, length) ?: return null
        if (parsed.protocol != 6 && parsed.protocol != 17) return null
        val ipHeader = when (parsed.ipVersion) {
            4 -> (buffer[0].toInt() and 0x0f) * 4
            6 -> ipv6TransportOffset(buffer, length)
            else -> return null
        }
        if (ipHeader < 0 || ipHeader >= length) return null
        return if (parsed.protocol == 6) decodeTcp(buffer, length, parsed, ipHeader) else decodeUdp(buffer, length, parsed, ipHeader)
    }

    fun tcp(
        ipVersion: Int,
        source: String,
        destination: String,
        sourcePort: Int,
        destinationPort: Int,
        sequence: Long,
        acknowledgement: Long,
        flags: Int,
        window: Int = 65535,
        payload: ByteArray = ByteArray(0)
    ): ByteArray = buildTransport(
        ipVersion, 6, source, destination, sourcePort, destinationPort,
        sequence, acknowledgement, flags, window, payload
    )

    fun udp(
        ipVersion: Int,
        source: String,
        destination: String,
        sourcePort: Int,
        destinationPort: Int,
        payload: ByteArray
    ): ByteArray = buildTransport(
        ipVersion, 17, source, destination, sourcePort, destinationPort,
        0L, 0L, 0, 65535, payload
    )

    fun randomSequence(): Long = Random.nextLong(1L, 0x7fff_ffffL)

    private fun decodeTcp(b: ByteArray, length: Int, p: ParsedPacket, offset: Int): TransportPacket? {
        if (offset + 20 > length) return null
        val dataOffset = ((b[offset + 12].toInt() ushr 4) and 0x0f) * 4
        if (dataOffset < 20 || offset + dataOffset > length) return null
        val flags = b[offset + 13].toInt() and 0xff
        val sequence = u32(b, offset + 4)
        val acknowledgement = u32(b, offset + 8)
        val window = u16(b, offset + 14)
        val payloadStart = offset + dataOffset
        val payloadLength = (p.totalBytes - payloadStart).coerceAtLeast(0)
        if (payloadStart + payloadLength > length) return null
        return TransportPacket(
            p.ipVersion, 6, p.sourceAddress, p.destinationAddress,
            p.sourcePort, p.destinationPort, sequence, acknowledgement,
            flags, window, b.copyOfRange(payloadStart, payloadStart + payloadLength)
        )
    }

    private fun decodeUdp(b: ByteArray, length: Int, p: ParsedPacket, offset: Int): TransportPacket? {
        if (offset + 8 > length) return null
        val declared = u16(b, offset + 4)
        if (declared < 8 || offset + declared > p.totalBytes || offset + declared > length) return null
        return TransportPacket(
            p.ipVersion, 17, p.sourceAddress, p.destinationAddress,
            p.sourcePort, p.destinationPort, payload = b.copyOfRange(offset + 8, offset + declared)
        )
    }

    private fun buildTransport(
        ipVersion: Int, protocol: Int, source: String, destination: String,
        sourcePort: Int, destinationPort: Int, sequence: Long, acknowledgement: Long,
        flags: Int, window: Int, payload: ByteArray
    ): ByteArray {
        require(ipVersion == 4 || ipVersion == 6)
        val src = InetAddress.getByName(source).address
        val dst = InetAddress.getByName(destination).address
        require(src.size == if (ipVersion == 4) 4 else 16)
        require(dst.size == src.size)

        val transportLength = if (protocol == 6) 20 + payload.size else 8 + payload.size
        val ipLength = if (ipVersion == 4) 20 + transportLength else 40 + transportLength
        val out = ByteArray(ipLength)
        if (ipVersion == 4) {
            out[0] = 0x45
            putU16(out, 2, ipLength)
            putU16(out, 4, Random.nextInt(0, 65536))
            out[8] = 64
            out[9] = protocol.toByte()
            src.copyInto(out, 12)
            dst.copyInto(out, 16)
            putU16(out, 10, checksum(out, 0, 20))
        } else {
            out[0] = 0x60
            putU16(out, 4, transportLength)
            out[6] = protocol.toByte()
            out[7] = 64
            src.copyInto(out, 8)
            dst.copyInto(out, 24)
        }

        val t = if (ipVersion == 4) 20 else 40
        putU16(out, t, sourcePort)
        putU16(out, t + 2, destinationPort)
        if (protocol == 6) {
            putU32(out, t + 4, sequence)
            putU32(out, t + 8, acknowledgement)
            out[t + 12] = (5 shl 4).toByte()
            out[t + 13] = flags.toByte()
            putU16(out, t + 14, window)
            payload.copyInto(out, t + 20)
        } else {
            putU16(out, t + 4, transportLength)
            payload.copyInto(out, t + 8)
        }

        val checksum = transportChecksum(ipVersion, src, dst, protocol, out, t, transportLength)
        putU16(out, t + if (protocol == 6) 16 else 6, checksum)
        return out
    }

    private fun transportChecksum(
        version: Int, src: ByteArray, dst: ByteArray, protocol: Int,
        packet: ByteArray, offset: Int, length: Int
    ): Int {
        var sum = 0L
        fun add(bytes: ByteArray) {
            var i = 0
            while (i + 1 < bytes.size) {
                sum += ((bytes[i].toInt() and 255) shl 8) or (bytes[i + 1].toInt() and 255)
                i += 2
            }
            if (i < bytes.size) sum += (bytes[i].toInt() and 255) shl 8
        }
        add(src); add(dst)
        if (version == 4) sum += protocol.toLong() else sum += (length ushr 16).toLong() + (length and 0xffff).toLong()
        if (version == 4) sum += length.toLong()
        if (version == 6) sum += protocol.toLong()
        val body = packet.copyOfRange(offset, offset + length)
        body[if (protocol == 6) 16 else 6] = 0
        body[if (protocol == 6) 17 else 7] = 0
        add(body)
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return (sum.inv() and 0xffff).toInt()
    }

    private fun checksum(b: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((b[i].toInt() and 255) shl 8) or (b[i + 1].toInt() and 255)
            i += 2
        }
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return (sum.inv() and 0xffff).toInt()
    }

    private fun ipv6TransportOffset(b: ByteArray, length: Int): Int {
        if (length < 40) return -1
        var next = b[6].toInt() and 255
        var offset = 40
        repeat(16) {
            if (next !in setOf(0, 43, 44, 51, 60)) return offset
            if (offset + 2 > length) return -1
            val nextHeader = b[offset].toInt() and 255
            val size = if (next == 44) 8 else if (next == 51) ((b[offset + 1].toInt() and 255) + 2) * 4 else ((b[offset + 1].toInt() and 255) + 1) * 8
            if (offset + size > length) return -1
            next = nextHeader
            offset += size
        }
        return -1
    }

    private fun u16(b: ByteArray, o: Int): Int = ((b[o].toInt() and 255) shl 8) or (b[o + 1].toInt() and 255)
    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 255) shl 24) or ((b[o + 1].toLong() and 255) shl 16) or
            ((b[o + 2].toLong() and 255) shl 8) or (b[o + 3].toLong() and 255)
    private fun putU16(b: ByteArray, o: Int, v: Int) { b[o] = (v ushr 8).toByte(); b[o + 1] = v.toByte() }
    private fun putU32(b: ByteArray, o: Int, v: Long) {
        b[o] = (v ushr 24).toByte(); b[o + 1] = (v ushr 16).toByte(); b[o + 2] = (v ushr 8).toByte(); b[o + 3] = v.toByte()
    }
}
