package com.dstwr.flow.vpn

import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded flow-to-application table for packet/session bookkeeping.
 *
 * Entries are indexed by the exact five-tuple. Replies can be resolved by
 * using the reversed key, so callers do not need to duplicate entries.
 */
class TrafficFlowTable(
    private val idleTimeoutMillis: Long = DEFAULT_IDLE_TIMEOUT_MILLIS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    init {
        require(idleTimeoutMillis > 0L) { "idleTimeoutMillis must be positive" }
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private val flows = ConcurrentHashMap<TrafficFlowKey, Entry>()

    fun touch(
        packet: ParsedPacket,
        packageName: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ) = touch(packet.toFlowKey(), packageName, nowMillis)

    fun touch(
        key: TrafficFlowKey,
        packageName: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        cleanup(nowMillis)
        if (packageName.isNullOrBlank() && flows[key]?.packageName != null) {
            flows.computeIfPresent(key) { _, old -> old.copy(lastSeenMillis = nowMillis) }
        } else {
            flows[key] = Entry(packageName = packageName, lastSeenMillis = nowMillis)
        }
        if (flows.size > maxEntries) prune(nowMillis)
    }

    fun find(key: TrafficFlowKey, nowMillis: Long = System.currentTimeMillis()): Entry? {
        val entry = flows[key] ?: flows[key.reversed()] ?: return null
        if (nowMillis - entry.lastSeenMillis > idleTimeoutMillis) {
            flows.remove(key, entry)
            flows.remove(key.reversed(), entry)
            return null
        }
        flows.computeIfPresent(key) { _, old -> old.copy(lastSeenMillis = nowMillis) }
        return entry
    }

    fun find(packet: ParsedPacket, nowMillis: Long = System.currentTimeMillis()): Entry? =
        find(packet.toFlowKey(), nowMillis)

    fun bind(key: TrafficFlowKey, packageName: String, nowMillis: Long = System.currentTimeMillis()) {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        touch(key, packageName, nowMillis)
    }

    fun remove(key: TrafficFlowKey) {
        flows.remove(key)
        flows.remove(key.reversed())
    }

    fun size(): Int = flows.size

    fun clear() = flows.clear()

    fun prune(nowMillis: Long = System.currentTimeMillis()) {
        flows.entries.removeIf { nowMillis - it.value.lastSeenMillis > idleTimeoutMillis }
        while (flows.size > maxEntries) {
            val oldest = flows.entries.minByOrNull { it.value.lastSeenMillis } ?: break
            flows.remove(oldest.key, oldest.value)
        }
    }

    data class Entry(
        val packageName: String?,
        val lastSeenMillis: Long
    )

    companion object {
        const val DEFAULT_IDLE_TIMEOUT_MILLIS = 120_000L
        const val DEFAULT_MAX_ENTRIES = 4096
    }
}
