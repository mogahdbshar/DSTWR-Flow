package com.dstwr.flow.ui.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dstwr.flow.data.apps.AppInventoryRepository
import com.dstwr.flow.data.apps.InstalledApp
import com.dstwr.flow.data.apps.AppPolicyRepository
import com.dstwr.flow.data.local.FlowDatabaseProvider
import com.dstwr.flow.data.usage.AppUsage
import com.dstwr.flow.data.usage.UsageStatsRepository
import com.dstwr.flow.domain.model.AppPolicy
import com.dstwr.flow.domain.model.NetworkScope
import com.dstwr.flow.vpn.FlowProtectionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class AppRow(
    val app: InstalledApp,
    val policy: AppPolicy,
    val usage: AppUsage = AppUsage(uid = app.uid, packageName = app.packageName, rxBytes = 0L, txBytes = 0L)
) {
    val blocked: Boolean get() = policy.blocked
}

class AppsViewModel(application: Application) : AndroidViewModel(application) {
    private val inventory = AppInventoryRepository(application)
    private val policyRepository = AppPolicyRepository(FlowDatabaseProvider.get(application))
    private val usageRepository = UsageStatsRepository(application)
    private val protectionController = FlowProtectionController(application)

    private val _apps = MutableStateFlow<List<AppRow>>(emptyList())
    val apps: StateFlow<List<AppRow>> = _apps.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            try {
                val installed = inventory.getLaunchableApps()
                val policies = policyRepository.getAll().associateBy { it.packageName }
                val usage = usageRepository.queryApps(
                    apps = installed,
                    startTime = startOfDayMillis(),
                    endTime = System.currentTimeMillis()
                )
                _apps.value = installed.map { app ->
                    AppRow(
                        app = app,
                        policy = policies[app.packageName] ?: AppPolicy(packageName = app.packageName),
                        usage = usage[app.packageName]
                            ?: AppUsage(app.uid, app.packageName, 0L, 0L)
                    )
                }
            } finally {
                _loading.value = false
            }
        }
    }

    fun setBlocked(packageName: String, blocked: Boolean) {
        updatePolicy(packageName) { copy(blocked = blocked) }
    }

    fun setSpeedLimits(packageName: String, downloadBytesPerSecond: Long, uploadBytesPerSecond: Long) {
        updatePolicy(packageName) {
            copy(
                downloadLimitBytesPerSecond = downloadBytesPerSecond.coerceAtLeast(0L),
                uploadLimitBytesPerSecond = uploadBytesPerSecond.coerceAtLeast(0L)
            )
        }
    }

    fun setQuotas(packageName: String, dailyBytes: Long, monthlyBytes: Long) {
        updatePolicy(packageName) {
            copy(
                dailyQuotaBytes = dailyBytes.coerceAtLeast(0L),
                monthlyQuotaBytes = monthlyBytes.coerceAtLeast(0L)
            )
        }
    }

    fun setSchedule(packageName: String, enabled: Boolean, startMinutes: Int, endMinutes: Int) {
        updatePolicy(packageName) {
            copy(
                scheduleEnabled = enabled,
                scheduleStartMinutes = startMinutes.coerceIn(0, 1439),
                scheduleEndMinutes = endMinutes.coerceIn(0, 1439)
            )
        }
    }

    fun setNetworkScope(packageName: String, scope: NetworkScope) {
        updatePolicy(packageName) { copy(networkScope = scope) }
    }

    private fun updatePolicy(packageName: String, transform: AppPolicy.() -> AppPolicy) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _apps.value.firstOrNull { it.app.packageName == packageName }?.policy
                ?: policyRepository.get(packageName)
                ?: AppPolicy(packageName = packageName)
            val updated = transform(current)
            when {
                updated.blocked != current.blocked ->
                    policyRepository.setBlocked(packageName, updated.blocked)
                updated.downloadLimitBytesPerSecond != current.downloadLimitBytesPerSecond ||
                    updated.uploadLimitBytesPerSecond != current.uploadLimitBytesPerSecond ->
                    policyRepository.setSpeedLimits(
                        packageName,
                        updated.downloadLimitBytesPerSecond,
                        updated.uploadLimitBytesPerSecond
                    )
                updated.dailyQuotaBytes != current.dailyQuotaBytes ||
                    updated.monthlyQuotaBytes != current.monthlyQuotaBytes ->
                    policyRepository.setQuotas(packageName, updated.dailyQuotaBytes, updated.monthlyQuotaBytes)
                updated.scheduleEnabled != current.scheduleEnabled ||
                    updated.scheduleStartMinutes != current.scheduleStartMinutes ||
                    updated.scheduleEndMinutes != current.scheduleEndMinutes ->
                    policyRepository.setSchedule(
                        packageName,
                        updated.scheduleEnabled,
                        updated.scheduleStartMinutes,
                        updated.scheduleEndMinutes
                    )
                updated.networkScope != current.networkScope ->
                    policyRepository.setNetworkScope(packageName, updated.networkScope)
            }
            _apps.value = _apps.value.map { row ->
                if (row.app.packageName == packageName) row.copy(policy = updated) else row
            }
            runCatching { protectionController.reapply() }
        }
    }

    private fun startOfDayMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
