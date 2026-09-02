package com.dstwr.flow.data.usage

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process

 data class AppUsage(
    val uid: Int,
    val packageName: String,
    val rxBytes: Long,
    val txBytes: Long
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

class UsageStatsRepository(private val context: Context) {
    private val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

    fun queryUid(uid: Int, startTime: Long, endTime: Long): AppUsage {
        val bucket = NetworkStats.Bucket()
        var rx = 0L
        var tx = 0L
        for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            try {
                val stats = manager.queryDetailsForUid(type, null, startTime, endTime, uid)
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    rx += bucket.rxBytes
                    tx += bucket.txBytes
                }
                stats.close()
            } catch (_: SecurityException) {
                // PACKAGE_USAGE_STATS is granted by the user in system settings.
            }
        }
        return AppUsage(uid, "", rx, tx)
    }

    fun querySelf(startTime: Long, endTime: Long): AppUsage = queryUid(Process.myUid(), startTime, endTime)
}
