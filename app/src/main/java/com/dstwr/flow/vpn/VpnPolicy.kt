package com.dstwr.flow.vpn

/** Pure policy decisions kept separate from Android VPN APIs. */
data class VpnPolicy(
    val blockedPackages: Set<String> = emptySet(),
    val emergencyBlock: Boolean = false
) {
    fun shouldBlock(packageName: String): Boolean =
        emergencyBlock || packageName in blockedPackages

    fun isEmpty(): Boolean = !emergencyBlock && blockedPackages.isEmpty()
}

object VpnPolicyEvaluator {
    fun fromBlockedPackages(packages: Collection<String>, emergencyBlock: Boolean): VpnPolicy =
        VpnPolicy(
            blockedPackages = packages.filter { it.isNotBlank() }.toSet(),
            emergencyBlock = emergencyBlock
        )
}
