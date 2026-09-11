package com.dstwr.flow.vpn

import com.dstwr.flow.domain.model.NetworkScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkScopeTest {
    @Test
    fun allMatchesEveryNetwork() {
        assertTrue(NetworkScope.ALL.matchesForTest(NetworkState.Type.WIFI))
        assertTrue(NetworkScope.ALL.matchesForTest(NetworkState.Type.MOBILE))
        assertTrue(NetworkScope.ALL.matchesForTest(NetworkState.Type.OTHER))
    }

    @Test
    fun wifiOnlyMatchesWifi() {
        assertTrue(NetworkScope.WIFI.matchesForTest(NetworkState.Type.WIFI))
        assertFalse(NetworkScope.WIFI.matchesForTest(NetworkState.Type.MOBILE))
    }

    @Test
    fun mobileOnlyMatchesMobile() {
        assertTrue(NetworkScope.MOBILE.matchesForTest(NetworkState.Type.MOBILE))
        assertFalse(NetworkScope.MOBILE.matchesForTest(NetworkState.Type.WIFI))
    }

    private fun NetworkScope.matchesForTest(type: NetworkState.Type): Boolean = when (this) {
        NetworkScope.ALL -> true
        NetworkScope.WIFI -> type == NetworkState.Type.WIFI
        NetworkScope.MOBILE -> type == NetworkState.Type.MOBILE
    }
}
