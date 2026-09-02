package com.dstwr.flow.domain.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenBucketTest {
    @Test
    fun unlimitedRateAlwaysAllows() {
        val bucket = TokenBucket(0L)
        assertTrue(bucket.tryConsume(Long.MAX_VALUE, 1_000_000_000L).allowed)
    }

    @Test
    fun initialCapacityAllowsBurst() {
        val bucket = TokenBucket(1_000L, capacityBytes = 2_000L)
        assertTrue(bucket.tryConsume(2_000L, 1_000_000_000L).allowed)
        assertFalse(bucket.tryConsume(1L, 1_000_000_000L).allowed)
    }

    @Test
    fun refillAllowsTrafficAfterElapsedTime() {
        val bucket = TokenBucket(1_000L, capacityBytes = 1_000L)
        assertTrue(bucket.tryConsume(1_000L, 1_000_000_000L).allowed)
        assertFalse(bucket.tryConsume(1L, 1_000_000_000L).allowed)
        assertTrue(bucket.tryConsume(1_000L, 2_000_000_000L).allowed)
    }

    @Test
    fun throttledResultProvidesRetryDelay() {
        val bucket = TokenBucket(1_000L, capacityBytes = 1_000L)
        bucket.tryConsume(1_000L, 1_000_000_000L)
        val result = bucket.tryConsume(500L, 1_000_000_000L)
        assertFalse(result.allowed)
        assertTrue(result.retryAfterMillis >= 1L)
    }
}
