package com.dstwr.flow.vpn

import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded flow-to-application table. A real resolver can populate it when a
 * flow is first observed, while replies are matched through the reversed key.
 */
class TrafficSessionTable(
    private val maxEntries: Int = 4096,
    private val idleTimeoutMillis: Long = 120_000L
) {
    private val entries = ConcurrentHashMap<TrafficFlowKey, Entry>()

    fun bind(flow: TrafficFlowKey, packageName: String, nowMillis: Long = System.currentTimeMillis()) {
        cleanup(nowMillis)
        if (packageName.isBlank()) return
        if (entries.size >= maxEntries && entries[flow] == null) {
            entries.entries.minByOrNull { it.value.lastSeenMillis }?.let { entries.remove(it.key) }
        }
        entries[flow] = Entry(packageName, nowMillis)
    }

    fun resolve(flow: TrafficFlowKey, nowMillis: Long = System.currentTimeMillis()): String? {
        val entry = entries[flow] ?: entries[flow.reversed()] ?: return null
        if (nowMillis - entry.lastSeenMillis > idleTimeoutMillis) {
            entries.remove(flow)
            entries.remove(flow.reversed())
            return null
        }
        entry.lastSeenMillis = nowMillis
        return entry.packageName
    }

    fun remove(flow: TrafficFlowKey) {
        entries.remove(flow)
        entries.remove(flow.reversed())
    }

    fun clear() = entries.clear()

    fun size(): Int = entries.size

    private fun cleanup(nowMillis: Long) {
        entries.entries.removeIf { nowMillis - it.value.lastSeenMillis > idleTimeoutMillis }
    }

    private data class Entry(
        val packageName: String,
        @Volatile var lastSeenMillis: Long
    )
}
