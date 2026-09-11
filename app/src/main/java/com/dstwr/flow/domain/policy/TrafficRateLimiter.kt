package com.dstwr.flow.domain.policy

import kotlin.math.min

/** Thread-safe token bucket used by the traffic engine when forwarding is available. */
class TrafficRateLimiter(private val rateBytesPerSecond: Long, burstSeconds: Int = 2) {
    private val rate = rateBytesPerSecond.coerceAtLeast(0L)
    private val capacity = if (rate == 0L) 0L else rate * burstSeconds.coerceIn(1, 10)
    private var tokens = capacity.toDouble()
    private var lastNanos = System.nanoTime()

    @Synchronized
    fun awaitBytes(bytes: Int): Long {
        if (rate <= 0L || bytes <= 0) return 0L
        refill()
        val wanted = bytes.toLong().coerceAtMost(capacity)
        if (tokens >= wanted) {
            tokens -= wanted
            return 0L
        }
        val missing = wanted - tokens
        val waitNanos = ((missing / rate.toDouble()) * 1_000_000_000.0).toLong()
        tokens = 0.0
        lastNanos = System.nanoTime()
        return min(waitNanos, 60_000_000_000L)
    }

    @Synchronized
    private fun refill() {
        val now = System.nanoTime()
        val elapsed = (now - lastNanos).coerceAtLeast(0L) / 1_000_000_000.0
        tokens = min(capacity.toDouble(), tokens + elapsed * rate)
        lastNanos = now
    }
}
