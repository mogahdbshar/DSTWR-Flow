package com.dstwr.flow.vpn

import com.dstwr.flow.domain.policy.TokenBucket

/** Combines packet classification, connection tracking and directional rate decisions. */
class PacketDecisionEngine(
    private val connections: ConnectionTracker,
    private val speeds: SpeedLimitRegistry,
    private val meter: TrafficMeter
) {
    suspend fun inspect(buffer: ByteArray, length: Int, packageName: String?, upload: Boolean): Decision {
        val packet = PacketParser.parse(buffer, length)
            ?: return Decision.Malformed
        connections.touch(packet)
        val result = if (packageName == null) TokenBucket.ConsumeResult(true, 0L)
        else if (upload) speeds.consumeUpload(packageName, packet.totalBytes)
        else speeds.consumeDownload(packageName, packet.totalBytes)
        val throttled = !result.allowed
        meter.record(packet, dropped = false, throttled = throttled)
        return if (throttled) Decision.Throttled(result.retryAfterMillis, packet) else Decision.Forward(packet)
    }

    sealed interface Decision {
        data object Malformed : Decision
        data class Forward(val packet: ParsedPacket) : Decision
        data class Throttled(val retryAfterMillis: Long, val packet: ParsedPacket) : Decision
    }
}
