package com.dstwr.flow.vpn

import java.util.concurrent.ConcurrentHashMap

/** Tracks active 5-tuples with bounded idle expiry. */
class ConnectionTracker(
    private val idleTimeoutMillis: Long = 120_000L,
    private val maxConnections: Int = 4_096
) {
    private val connections = ConcurrentHashMap<FlowKey, ConnectionState>()

    fun touch(packet: ParsedPacket, nowMillis: Long = System.currentTimeMillis()): ConnectionState? {
        cleanup(nowMillis)
        val key = FlowKey(packet.ipVersion, packet.protocol, packet.sourceAddress, packet.sourcePort, packet.destinationAddress, packet.destinationPort)
        if (connections.size >= maxConnections && !connections.containsKey(key)) return null
        val state = connections.compute(key) { _, old ->
            (old ?: ConnectionState(key)).copy(lastSeenMillis = nowMillis, bytes = (old?.bytes ?: 0L) + packet.totalBytes)
        } ?: return null
        return state
    }

    fun size(): Int = connections.size

    fun clear() = connections.clear()

    private fun cleanup(nowMillis: Long) {
        connections.entries.removeIf { nowMillis - it.value.lastSeenMillis > idleTimeoutMillis }
    }

    data class FlowKey(val ipVersion: Int, val protocol: Int, val source: String, val sourcePort: Int, val destination: String, val destinationPort: Int)
    data class ConnectionState(val key: FlowKey, val lastSeenMillis: Long = System.currentTimeMillis(), val bytes: Long = 0L)
}
