package com.dstwr.flow.vpn

/** Stable five-tuple identity used for NAT/connection bookkeeping. */
data class TrafficFlowKey(
    val ipVersion: Int,
    val protocol: Int,
    val sourceAddress: String,
    val sourcePort: Int,
    val destinationAddress: String,
    val destinationPort: Int
) {
    fun reversed(): TrafficFlowKey = copy(
        sourceAddress = destinationAddress,
        sourcePort = destinationPort,
        destinationAddress = sourceAddress,
        destinationPort = sourcePort
    )
}

fun ParsedPacket.toFlowKey(): TrafficFlowKey = TrafficFlowKey(
    ipVersion = ipVersion,
    protocol = protocol,
    sourceAddress = sourceAddress,
    sourcePort = sourcePort,
    destinationAddress = destinationAddress,
    destinationPort = destinationPort
)
