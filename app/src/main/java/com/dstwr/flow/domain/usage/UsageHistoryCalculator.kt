package com.dstwr.flow.domain.usage

import com.dstwr.flow.ui.stats.UsageHistoryPoint

/** Converts cumulative network readings into interval usage values for charts. */
object UsageHistoryCalculator {
    fun toIntervals(points: List<UsageHistoryPoint>): List<UsageHistoryPoint> {
        val sorted = points.sortedBy { it.time }
        if (sorted.size < 2) return emptyList()

        return sorted.zipWithNext { previous, current ->
            val wifi = (current.wifiBytes - previous.wifiBytes).coerceAtLeast(0L)
            val mobile = (current.mobileBytes - previous.mobileBytes).coerceAtLeast(0L)
            UsageHistoryPoint(
                time = current.time,
                wifiBytes = wifi,
                mobileBytes = mobile,
                totalBytes = wifi + mobile
            )
        }
    }
}
