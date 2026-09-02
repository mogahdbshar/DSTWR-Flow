package com.dstwr.flow.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnPolicyTest {
    @Test
    fun blockedPackageIsBlocked() {
        val policy = VpnPolicyEvaluator.fromBlockedPackages(listOf("com.example.app"), false)
        assertTrue(policy.shouldBlock("com.example.app"))
        assertFalse(policy.shouldBlock("com.example.other"))
    }

    @Test
    fun emergencyModeBlocksEverything() {
        val policy = VpnPolicyEvaluator.fromBlockedPackages(emptyList(), true)
        assertTrue(policy.shouldBlock("com.example.app"))
    }

    @Test
    fun blankPackagesAreIgnored() {
        val policy = VpnPolicyEvaluator.fromBlockedPackages(listOf("", " ", "com.example.app"), false)
        assertTrue(policy.shouldBlock("com.example.app"))
        assertFalse(policy.shouldBlock(""))
    }

    @Test
    fun emptyPolicyDoesNotBlock() {
        val policy = VpnPolicyEvaluator.fromBlockedPackages(emptyList(), false)
        assertTrue(policy.isEmpty())
        assertFalse(policy.shouldBlock("com.example.app"))
    }
}
