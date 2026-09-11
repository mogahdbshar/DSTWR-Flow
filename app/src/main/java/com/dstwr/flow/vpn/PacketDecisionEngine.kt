package com.dstwr.flow.vpn

import com.dstwr.flow.domain.policy.TokenBucket

/** Combines packet classification, app identity, blocking, rate decisions and metering. */
class PacketDecisionEngine(
    private val connections: ConnectionTracker,
    private val speeds: SpeedLimitRegistry,
    private val meter: TrafficMeter,
    private val identityResolver: TrafficIdentityResolver = TrafficIdentityResolver.Unresolved,
    private val policies: TrafficPolicyRegistry = TrafficPolicyRegistry()
) {
    suspend fun inspect(
        buffer: ByteArray,
        length: Int,
        direction: TrafficDirection
    ): Decision {
        val packet = PacketParser.parse(buffer, length)
            ?: return Decision.Malformed

        connections.touch(packet)
        val packageName = identityResolver.resolve(packet, direction)
        val policy = packageName?.let(policies::get)
        if (policy?.blocked == true) {
            meter.record(packet, dropped = true, throttled = false)
            return Decision.Blocked(packet, packageName, direction)
        }

        val result = when (packageName) {
            null -> TokenBucket.ConsumeResult(true, 0L)
            else -> when (direction) {
                TrafficDirection.UPLOAD -> speeds.consumeUpload(packageName, packet.totalBytes)
                TrafficDirection.DOWNLOAD -> speeds.consumeDownload(packageName, packet.totalBytes)
            }
        }

        val throttled = !result.allowed
        meter.record(packet, dropped = false, throttled = throttled)
        return if (throttled) {
            Decision.Throttled(result.retryAfterMillis, packet, packageName, direction)
        } else {
            Decision.Forward(packet, packageName, direction)
        }
    }

    /** Compatibility entry point for callers that still provide a package name. */
    suspend fun inspect(
        buffer: ByteArray,
        length: Int,
        packageName: String?,
        upload: Boolean
    ): Decision {
        val packet = PacketParser.parse(buffer, length)
            ?: return Decision.Malformed
        connections.touch(packet)
        val direction = if (upload) TrafficDirection.UPLOAD else TrafficDirection.DOWNLOAD
        if (packageName?.let(policies::get)?.blocked == true) {
            meter.record(packet, dropped = true, throttled = false)
            return Decision.Blocked(packet, packageName, direction)
        }
        val result = packageName?.let {
            if (upload) speeds.consumeUpload(it, packet.totalBytes)
            else speeds.consumeDownload(it, packet.totalBytes)
        } ?: TokenBucket.ConsumeResult(true, 0L)
        val throttled = !result.allowed
        meter.record(packet, dropped = false, throttled = throttled)
        return if (throttled) {
            Decision.Throttled(result.retryAfterMillis, packet, packageName, direction)
        } else {
            Decision.Forward(packet, packageName, direction)
        }
    }

    sealed interface Decision {
        data object Malformed : Decision
        data class Blocked(
            val packet: ParsedPacket,
            val packageName: String,
            val direction: TrafficDirection
        ) : Decision
        data class Forward(
            val packet: ParsedPacket,
            val packageName: String?,
            val direction: TrafficDirection
        ) : Decision
        data class Throttled(
            val retryAfterMillis: Long,
            val packet: ParsedPacket,
            val packageName: String?,
            val direction: TrafficDirection
        ) : Decision
    }
}
