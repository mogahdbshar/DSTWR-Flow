package com.dstwr.flow.vpn

/**
 * Resolves a parsed flow to an application identity.
 *
 * Android's TUN interface does not expose the originating package on each
 * packet. The resolver is therefore deliberately isolated from packet parsing
 * so a real UID/flow mapper can be introduced without changing the traffic
 * engine or policy model.
 */
interface TrafficIdentityResolver {
    fun resolve(packet: ParsedPacket, direction: TrafficDirection): String?

    object Unresolved : TrafficIdentityResolver {
        override fun resolve(packet: ParsedPacket, direction: TrafficDirection): String? = null
    }
}
