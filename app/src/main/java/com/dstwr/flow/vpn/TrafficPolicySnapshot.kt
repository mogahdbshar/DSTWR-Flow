package com.dstwr.flow.vpn

import com.dstwr.flow.domain.policy.SpeedLimit

/** Immutable runtime snapshot used by the traffic layer without touching storage. */
data class TrafficPolicySnapshot(
    val packageName: String,
    val blocked: Boolean = false,
    val downloadLimitBytesPerSecond: Long = 0L,
    val uploadLimitBytesPerSecond: Long = 0L
) {
    fun downloadLimit(): SpeedLimit = SpeedLimitFormatter.fromBytesPerSecond(downloadLimitBytesPerSecond)
    fun uploadLimit(): SpeedLimit = SpeedLimitFormatter.fromBytesPerSecond(uploadLimitBytesPerSecond)
}

class TrafficPolicyRegistry {
    private val policies = java.util.concurrent.ConcurrentHashMap<String, TrafficPolicySnapshot>()

    fun replaceAll(items: Collection<TrafficPolicySnapshot>) {
        policies.clear()
        items.forEach { policy -> policies[policy.packageName] = policy }
    }

    fun put(policy: TrafficPolicySnapshot) {
        policies[policy.packageName] = policy
    }

    fun remove(packageName: String) {
        policies.remove(packageName)
    }

    fun get(packageName: String): TrafficPolicySnapshot? = policies[packageName]

    fun snapshot(): List<TrafficPolicySnapshot> = policies.values.toList()

    fun clear() = policies.clear()
}
