package com.dstwr.flow.domain.policy

import kotlin.math.ceil

/**
 * Thread-safe token bucket used by the VPN forwarding layer.
 * A limit of zero means unlimited and therefore never throttles.
 */
class TokenBucket(
    private var rateBytesPerSecond: Long,
    capacityBytes: Long = defaultCapacity(rateBytesPerSecond)
) {
    private var capacity = capacityBytes.coerceAtLeast(1L).toDouble()
    private var tokens = capacity
    private var lastNanos = System.nanoTime()

    @Synchronized
    fun updateRate(newRateBytesPerSecond: Long) {
        refill(System.nanoTime())
        rateBytesPerSecond = newRateBytesPerSecond.coerceAtLeast(0L)
        capacity = if (rateBytesPerSecond <= 0L) {
            1.0
        } else {
            maxOf(capacity, defaultCapacity(rateBytesPerSecond).toDouble())
        }
        tokens = tokens.coerceAtMost(capacity)
    }

    @Synchronized
    fun tryConsume(byteCount: Long, nowNanos: Long = System.nanoTime()): ConsumeResult {
        val requested = byteCount.coerceAtLeast(0L)
        refill(nowNanos)
        if (requested == 0L || rateBytesPerSecond <= 0L) {
            return ConsumeResult(true, 0L)
        }

        if (requested <= tokens) {
            tokens -= requested
            return ConsumeResult(true, 0L)
        }

        val missing = requested - tokens
        val waitMillis = ceil(
            missing * 1000.0 / rateBytesPerSecond.toDouble()
        ).toLong().coerceAtLeast(1L)
        return ConsumeResult(false, waitMillis)
    }

    @Synchronized
    fun reset(nowNanos: Long = System.nanoTime()) {
        tokens = capacity
        lastNanos = nowNanos
    }

    private fun refill(nowNanos: Long) {
        val elapsed = (nowNanos - lastNanos).coerceAtLeast(0L)
        if (rateBytesPerSecond > 0L) {
            val added = elapsed.toDouble() * rateBytesPerSecond.toDouble() / 1_000_000_000.0
            tokens = (tokens + added).coerceAtMost(capacity)
        } else {
            tokens = capacity
        }
        lastNanos = nowNanos
    }

    data class ConsumeResult(
        val allowed: Boolean,
        val retryAfterMillis: Long
    )

    companion object {
        private fun defaultCapacity(rate: Long): Long =
            if (rate <= 0L) 1L else rate.coerceAtMost(10L * 1024L * 1024L)
    }
}
