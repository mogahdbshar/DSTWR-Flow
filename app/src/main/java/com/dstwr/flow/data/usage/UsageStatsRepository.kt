package com.dstwr.flow.data.usage

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import com.dstwr.flow.data.apps.InstalledApp

/**
 * Reads Android's system network counters. No traffic is routed or modified here.
 * The user must grant Usage Access in Android settings before these counters are
 * expected to be available.
 */
data class AppUsage(
    val uid: Int,
    val packageName: String,
    val rxBytes: Long,
    val txBytes: Long,
    val wifiRxBytes: Long = 0L,
    val wifiTxBytes: Long = 0L,
    val mobileRxBytes: Long = 0L,
    val mobileTxBytes: Long = 0L
) {
    val totalBytes: Long get() = rxBytes + txBytes
    val wifiBytes: Long get() = wifiRxBytes + wifiTxBytes
    val mobileBytes: Long get() = mobileRxBytes + mobileTxBytes
}

class UsageStatsRepository(private val context: Context) {
    private val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

    fun queryUid(uid: Int, startTime: Long, endTime: Long): AppUsage {
        val wifi = queryNetwork(uid, ConnectivityManager.TYPE_WIFI, startTime, endTime)
        val mobile = queryNetwork(uid, ConnectivityManager.TYPE_MOBILE, startTime, endTime)
        return AppUsage(
            uid = uid,
            packageName = "",
            rxBytes = wifi.first + mobile.first,
            txBytes = wifi.second + mobile.second,
            wifiRxBytes = wifi.first,
            wifiTxBytes = wifi.second,
            mobileRxBytes = mobile.first,
            mobileTxBytes = mobile.second
        )
    }

    fun queryApps(
        apps: List<InstalledApp>,
        startTime: Long,
        endTime: Long
    ): Map<String, AppUsage> = buildMap {
        apps.forEach { app ->
            val usage = queryUid(app.uid, startTime, endTime)
            put(app.packageName, usage.copy(packageName = app.packageName))
        }
    }

    fun querySelf(startTime: Long, endTime: Long): AppUsage = queryUid(Process.myUid(), startTime, endTime)

    private fun queryNetwork(
        uid: Int,
        networkType: Int,
        startTime: Long,
        endTime: Long
    ): Pair<Long, Long> {
        val bucket = NetworkStats.Bucket()
        var rx = 0L
        var tx = 0L
        try {
            val stats = manager.queryDetailsForUid(
                networkType,
                null,
                startTime,
                endTime,
                uid
            )
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                rx += bucket.rxBytes
                tx += bucket.txBytes
            }
            stats.close()
        } catch (_: SecurityException) {
            // Usage Access has not been granted yet.
        } catch (_: IllegalArgumentException) {
            // Some Android/device combinations reject unavailable mobile details.
        }
        return rx to tx
    }
}
