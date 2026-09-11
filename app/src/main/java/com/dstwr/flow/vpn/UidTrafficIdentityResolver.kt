package com.dstwr.flow.vpn

/**
 * Resolves a package only from a previously trusted flow binding.
 * Android does not attach the originating package name to each TUN packet,
 * so unresolved traffic remains unresolved rather than being guessed.
 *
 * The historical class name is retained for source compatibility, but the
 * implementation is flow-based, not UID-based.
 */
class UidTrafficIdentityResolver(
    private val flows: TrafficFlowTable
) : TrafficIdentityResolver {
    override fun resolve(packet: ParsedPacket, direction: TrafficDirection): String? =
        flows.find(packet.toFlowKey())?.packageName

    fun bind(packet: ParsedPacket, packageName: String) {
        flows.bind(packet.toFlowKey(), packageName)
    }
}
