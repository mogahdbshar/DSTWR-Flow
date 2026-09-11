package com.dstwr.flow.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Low-overhead in-memory traffic counters for the active VPN session. */
class TrafficMeter {
    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow(TrafficSnapshot())
    val snapshot: StateFlow<TrafficSnapshot> = _snapshot.asStateFlow()

    suspend fun record(packet: ParsedPacket, dropped: Boolean = false, throttled: Boolean = false) {
        mutex.withLock {
            _snapshot.value = _snapshot.value.plus(packet, dropped, throttled)
        }
    }

    suspend fun reset() {
        mutex.withLock { _snapshot.value = TrafficSnapshot() }
    }
}
