package com.dstwr.flow.data.usage

import com.dstwr.flow.data.apps.InstalledApp
import com.dstwr.flow.data.local.FlowDatabase
import com.dstwr.flow.data.local.UsageSnapshotEntity

/** Persists periodic usage snapshots locally so historical charts can be built later. */
class UsageSnapshotRepository(private val database: FlowDatabase) {
    private val dao = database.usageSnapshotDao()

    suspend fun save(
        apps: List<InstalledApp>,
        usages: Map<String, AppUsage>,
        startTime: Long,
        endTime: Long
    ) {
        apps.forEach { app ->
            val usage = usages[app.packageName] ?: return@forEach
            dao.insert(
                UsageSnapshotEntity(
                    packageName = app.packageName,
                    startTime = startTime,
                    endTime = endTime,
                    networkType = NETWORK_WIFI,
                    rxBytes = usage.wifiRxBytes,
                    txBytes = usage.wifiTxBytes
                )
            )
            dao.insert(
                UsageSnapshotEntity(
                    packageName = app.packageName,
                    startTime = startTime,
                    endTime = endTime,
                    networkType = NETWORK_MOBILE,
                    rxBytes = usage.mobileRxBytes,
                    txBytes = usage.mobileTxBytes
                )
            )
        }
    }

    suspend fun cleanup(keepFrom: Long) {
        dao.deleteBefore(keepFrom)
    }

    companion object {
        const val NETWORK_WIFI = 1
        const val NETWORK_MOBILE = 2
    }
}
