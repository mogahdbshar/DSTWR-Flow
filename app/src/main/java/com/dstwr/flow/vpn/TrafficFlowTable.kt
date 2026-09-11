package com.dstwr.flow.vpn

import java.util.concurrent.ConcurrentHashMap

/** Thread-safe flow table for stable packet-to-session bookkeeping. */
class TrafficFlowTable(
    private val idleTimeoutMillis: Long = DEFAULT_IDLE_TIMEOUT_MILLIS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    private val flows = ConcurrentHashMap<TrafficFlowKey, Entry>()

    fun touch(packet: ParsedPacket, packageName: String? = null, nowMillis: Long = System.currentTimeMillis()) {
        val key = packet.toFlowKey()
        flows[key] = Entry(packageName = packageName, lastSeenMillis = nowMillis)
        if (flows.size > maxEntries) prune(nowMillis)
    }

    fun find(key: TrafficFlowKey, nowMillis: Long = System.currentTimeMillis()): Entry? {
        val entry = flows[key] ?: return null
        if (nowMillis - entry.lastSeenMillis > idleTimeoutMillis) {
            flows.remove(key, entry)
            return null
        }
        return entry
    }

    fun remove(key: TrafficFlowKey) {
        flows.remove(key)
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
