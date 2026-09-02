package com.dstwr.flow.vpn

/** Commands understood by the local traffic-control service. */
sealed interface VpnCommand {
    data class Apply(val emergencyBlock: Boolean = false) : VpnCommand
    data object Stop : VpnCommand
}
