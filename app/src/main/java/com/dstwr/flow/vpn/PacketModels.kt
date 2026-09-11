package com.dstwr.flow.vpn

/** Immutable description of one IP packet observed on the local TUN interface. */
data class ParsedPacket(
    val ipVersion: Int,
    val protocol: Int,
    val sourceAddress: String,
    val destinationAddress: String,
    val sourcePort: Int,
    val destinationPort: Int,
    val payloadBytes: Int,
    val totalBytes: Int
)

data class TrafficSnapshot(
    val packets: Long = 0L,
    val bytes: Long = 0L,
    val tcpPackets: Long = 0L,
    val udpPackets: Long = 0L,
    val otherPackets: Long = 0L,
    val droppedPackets: Long = 0L,
    val throttledPackets: Long = 0L
) {
    fun plus(packet: ParsedPacket, dropped: Boolean = false, throttled: Boolean = false): TrafficSnapshot =
        copy(
            packets = packets + 1,
            bytes = bytes + packet.totalBytes,
            tcpPackets = tcpPackets + if (packet.protocol == 6) 1 else 0,
            udpPackets = udpPackets + if (packet.protocol == 17) 1 else 0,
            otherPackets = otherPackets + if (packet.protocol != 6 && packet.protocol != 17) 1 else 0,
            droppedPackets = droppedPackets + if (dropped) 1 else 0,
            throttledPackets = throttledPackets + if (throttled) 1 else 0
        )
}
