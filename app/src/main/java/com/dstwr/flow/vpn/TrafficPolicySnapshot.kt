package com.dstwr.flow.vpn

/** Immutable runtime snapshot used by the traffic layer without touching storage. */
data class TrafficPolicySnapshot(
    val packageName: String,
    val blocked: Boolean = false,
    val downloadLimitBytesPerSecond: Long = 0L,
    val uploadLimitBytesPerSecond: Long = 0L
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(downloadLimitBytesPerSecond >= 0L) { "download limit must not be negative" }
        require(uploadLimitBytesPerSecond >= 0L) { "upload limit must not be negative" }
    }
}

class TrafficPolicyRegistry {
    private val policies = java.util.concurrent.ConcurrentHashMap<String, TrafficPolicySnapshot>()
    @Volatile private var globalBlocked = false

    fun replaceAll(items: Collection<TrafficPolicySnapshot>) {
        policies.clear()
        items.forEach { policy -> put(policy) }
    }

    fun put(policy: TrafficPolicySnapshot) {
        policies[policy.packageName] = policy
    }

    fun remove(packageName: String) {
        policies.remove(packageName)
    }

    fun get(packageName: String): TrafficPolicySnapshot? = policies[packageName]

    fun snapshot(): List<TrafficPolicySnapshot> = policies.values.toList()

    fun setGlobalBlocked(blocked: Boolean) {
        globalBlocked = blocked
    }

    fun isGlobalBlocked(): Boolean = globalBlocked

    fun clear() {
        policies.clear()
        globalBlocked = false
    }
}
