package com.dstwr.flow.domain.policy

import com.dstwr.flow.data.usage.AppUsage

/** Network usage values needed by the policy engine. */
data class PolicyUsage(
    val dailyBytes: Long = 0L,
    val monthlyBytes: Long = 0L,
    val wifiBytes: Long = 0L,
    val mobileBytes: Long = 0L,
    val monthlyWifiBytes: Long = 0L,
    val monthlyMobileBytes: Long = 0L
) {
    fun totalBytes(): Long = dailyBytes.coerceAtLeast(0L)

    companion object {
        fun from(daily: AppUsage, monthly: AppUsage): PolicyUsage = PolicyUsage(
            dailyBytes = daily.totalBytes.coerceAtLeast(0L),
            monthlyBytes = monthly.totalBytes.coerceAtLeast(0L),
            wifiBytes = daily.wifiBytes.coerceAtLeast(0L),
            mobileBytes = daily.mobileBytes.coerceAtLeast(0L),
            monthlyWifiBytes = monthly.wifiBytes.coerceAtLeast(0L),
            monthlyMobileBytes = monthly.mobileBytes.coerceAtLeast(0L)
        )
    }
}
