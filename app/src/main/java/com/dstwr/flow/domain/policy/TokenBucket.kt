package com.dstwr.flow.domain.policy

import kotlin.math.ceil

/**
 * Small, thread-safe token bucket used by the future VPN forwarding layer.
 * A limit of zero means unlimited and therefore never throttles.
 */
class TokenBucket(
    private var rateBytesPerSecond: Long,
    private val capacityBytes: Long = defaultCapacity(rateBytesPerSecond)
) {
    private var tokens = normalizedCapacity()
    private var lastNanos = System.nanoTime()

    @Synchronized
    fun updateRate(newRateBytesPerSecond: Long) {
        refill(System.nanoTime())
        rateBytesPerSecond = newRateBytesPerSecond.coerceAtLeast(0L)
        tokens = tokens.coerceAtMost(normalizedCapacity())
    }

    @Synchronized
    fun tryConsume(byteCount: Long, nowNanos: Long = System.nanoTime()): ConsumeResult {
        val requested = byteCount.coerceAtLeast(0L)
        if (requested == 0L || rateBytesPerSecond <= 0L) {
            refill(nowNanos)
            return ConsumeResult(true, 0L)
        }

        refill(nowNanos)
        if (requested <= tokens) {
            tokens -= requested
            return ConsumeResult(true, 0L)
        }

        val missing = requested - tokens
        val waitMillis = ceil(missing * 1000.0 / rateBytesPerSecond.toDouble()).toLong().coerceAtLeast(1L)
        return ConsumeResult(false, waitMillis)
    }

    @Synchronized
    fun reset(nowNanos: Long = System.nanoTime()) {
        tokens = normalizedCapacity()
        lastNanos = nowNanos
    }

    private fun refill(nowNanos: Long) {
        val elapsed = (nowNanos - lastNanos).coerceAtLeast(0L)
        if (rateBytesPerSecond > 0L) {
            val added = elapsed.toDouble() * rateBytesPerSecond.toDouble() / 1_000_000_000.0
            tokens = (tokens + added).coerceAtMost(normalizedCapacity().toDouble())
        } else {
            tokens = normalizedCapacity().toDouble()
        }
        lastNanos = nowNanos
    }

    private fun normalizedCapacity(): Double = normalizedCapacity(rateBytesPerSecond).toDouble()

    data class ConsumeResult(
        val allowed: Boolean,
        val retryAfterMillis: Long
    )

    companion object {
        private fun defaultCapacity(rate: Long): Long =
            if (rate <= 0L) 1L else rate.coerceAtMost(10L * 1024L * 1024L)
    }
}
