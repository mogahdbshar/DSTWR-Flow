package com.dstwr.flow.vpn

/**
 * Package resolver backed by an already-known flow binding. Android does not
 * provide a package name directly with every TUN packet, so unresolved flows
 * must remain unresolved rather than being guessed.
 */
class UidTrafficIdentityResolver(
    private val sessions: TrafficSessionTable
) : TrafficIdentityResolver {
    override fun resolve(packet: ParsedPacket, direction: TrafficDirection): String? =
        sessions.resolve(packet.toFlowKey())

    fun bind(packet: ParsedPacket, packageName: String) {
        sessions.bind(packet.toFlowKey(), packageName)
    }
}
