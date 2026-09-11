package com.dstwr.flow.vpn

/**
 * Resolves an application from an already-known flow binding.
 *
 * The TUN packet itself does not contain an Android package name, so an
 * unresolved flow is deliberately returned as null instead of guessed.
 */
class SessionTrafficIdentityResolver(
    private val sessions: TrafficSessionTable
) : TrafficIdentityResolver {
    override fun resolve(packet: ParsedPacket, direction: TrafficDirection): String? =
        sessions.resolve(packet.toFlowKey())

    fun bind(packet: ParsedPacket, packageName: String) {
        sessions.bind(packet.toFlowKey(), packageName)
    }
}
