package com.dstwr.flow.vpn

/**
 * Compatibility facade for the flow-to-application table.
 * TrafficFlowTable is the single implementation used by the traffic layer.
 */
class TrafficSessionTable(
    maxEntries: Int = TrafficFlowTable.DEFAULT_MAX_ENTRIES,
    idleTimeoutMillis: Long = TrafficFlowTable.DEFAULT_IDLE_TIMEOUT_MILLIS
) {
    private val table = TrafficFlowTable(idleTimeoutMillis = idleTimeoutMillis, maxEntries = maxEntries)

    fun bind(flow: TrafficFlowKey, packageName: String, nowMillis: Long = System.currentTimeMillis()) =
        table.bind(flow, packageName, nowMillis)

    fun resolve(flow: TrafficFlowKey, nowMillis: Long = System.currentTimeMillis()): String? =
        table.find(flow, nowMillis)?.packageName

    fun remove(flow: TrafficFlowKey) = table.remove(flow)

    fun clear() = table.clear()

    fun size(): Int = table.size()
}
