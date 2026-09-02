package com.dstwr.flow.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dstwr.flow.data.apps.AppInventoryRepository
import com.dstwr.flow.data.apps.InstalledApp
import com.dstwr.flow.data.local.FlowDatabaseProvider
import com.dstwr.flow.data.usage.AppUsage
import com.dstwr.flow.data.usage.UsageSnapshotRepository
import com.dstwr.flow.data.usage.UsageStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class UsageSummary(
    val total: AppUsage = AppUsage(0, "", 0L, 0L),
    val wifi: Long = 0L,
    val mobile: Long = 0L,
    val appCount: Int = 0,
    val topApps: List<AppUsageRow> = emptyList()
)

data class AppUsageRow(val app: InstalledApp, val usage: AppUsage)

data class UsageHistoryPoint(val time: Long, val wifiBytes: Long, val mobileBytes: Long, val totalBytes: Long)

class UsageViewModel(application: Application) : AndroidViewModel(application) {
    private val inventory = AppInventoryRepository(application)
    private val repository = UsageStatsRepository(application)
    private val snapshotRepository = UsageSnapshotRepository(FlowDatabaseProvider.get(application))

    private val _today = MutableStateFlow(UsageSummary())
    val today: StateFlow<UsageSummary> = _today.asStateFlow()
    private val _week = MutableStateFlow(UsageSummary())
    val week: StateFlow<UsageSummary> = _week.asStateFlow()
    private val _month = MutableStateFlow(UsageSummary())
    val month: StateFlow<UsageSummary> = _month.asStateFlow()
    private val _history = MutableStateFlow<List<UsageHistoryPoint>>(emptyList())
    val history: StateFlow<List<UsageHistoryPoint>> = _history.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try {
                val apps = inventory.getLaunchableApps()
                val now = System.currentTimeMillis()
                val todayStart = startOfDay(now)
                val todayUsages = repository.queryApps(apps, todayStart, now)
                _today.value = summarize(apps, todayUsages)
                _week.value = load(apps, startOfWeek(now), now)
                _month.value = load(apps, startOfMonth(now), now)
                snapshotRepository.save(apps, todayUsages, todayStart, now)
                snapshotRepository.cleanup(startOfMonth(now - 90L * DAY_MILLIS))
                _history.value = buildHistory(snapshotRepository.history(startOfDay(now - 6L * DAY_MILLIS)))
            } finally { _loading.value = false }
        }
    }

    private fun load(apps: List<InstalledApp>, start: Long, end: Long): UsageSummary = summarize(apps, repository.queryApps(apps, start, end))

    private fun summarize(apps: List<InstalledApp>, usages: Map<String, AppUsage>): UsageSummary {
        val rows = apps.mapNotNull { app -> usages[app.packageName]?.let { AppUsageRow(app, it) } }
        val total = rows.fold(AppUsage(0, "", 0L, 0L)) { acc, row -> acc + row.usage }
        return UsageSummary(total, total.wifiBytes, total.mobileBytes, rows.count { it.usage.totalBytes > 0L }, rows.sortedByDescending { it.usage.totalBytes }.take(5))
    }

    private fun buildHistory(rows: List<com.dstwr.flow.data.local.UsageSnapshotEntity>): List<UsageHistoryPoint> =
        rows.groupBy { it.startTime }.map { (time, entries) ->
            val wifi = entries.filter { it.networkType == UsageSnapshotRepository.NETWORK_WIFI }.sumOf { it.rxBytes + it.txBytes }
            val mobile = entries.filter { it.networkType == UsageSnapshotRepository.NETWORK_MOBILE }.sumOf { it.rxBytes + it.txBytes }
            UsageHistoryPoint(time, wifi, mobile, wifi + mobile)
        }.sortedBy { it.time }.takeLast(48)

    private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfWeek(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time; set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfMonth(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time; set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private operator fun AppUsage.plus(other: AppUsage) = AppUsage(
        uid = 0, packageName = "", rxBytes = rxBytes + other.rxBytes, txBytes = txBytes + other.txBytes,
        wifiRxBytes = wifiRxBytes + other.wifiRxBytes, wifiTxBytes = wifiTxBytes + other.wifiTxBytes,
        mobileRxBytes = mobileRxBytes + other.mobileRxBytes, mobileTxBytes = mobileTxBytes + other.mobileTxBytes
    )

    companion object { private const val DAY_MILLIS = 24L * 60L * 60L * 1000L }
}
