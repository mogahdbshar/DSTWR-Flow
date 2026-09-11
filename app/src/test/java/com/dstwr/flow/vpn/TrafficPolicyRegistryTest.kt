package com.dstwr.flow.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrafficPolicyRegistryTest {
    @Test
    fun putReplaceAndRemoveAreDeterministic() {
        val registry = TrafficPolicyRegistry()
        val first = TrafficPolicySnapshot("com.example.one", blocked = true)
        val second = TrafficPolicySnapshot("com.example.two", uploadLimitBytesPerSecond = 1024L)

        registry.put(first)
        registry.put(second)
        assertEquals(first, registry.get("com.example.one"))
        assertEquals(second, registry.get("com.example.two"))

        registry.replaceAll(listOf(second))
        assertNull(registry.get("com.example.one"))
        assertEquals(second, registry.get("com.example.two"))

        registry.remove("com.example.two")
        assertNull(registry.get("com.example.two"))
    }

    @Test
    fun invalidLimitsAreRejected() {
        var rejected = false
        try {
            TrafficPolicySnapshot("com.example.app", downloadLimitBytesPerSecond = -1L)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertEquals(true, rejected)
    }
}
