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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

data class AppRow(
    val app: InstalledApp,
    val policy: AppPolicy,
    val usage: AppUsage = AppUsage(uid = app.uid, packageName = app.packageName, rxBytes = 0L, txBytes = 0L)
) {
    val blocked: Boolean get() = policy.blocked
}

enum class AppFilterMode {
    ALL,
    BLOCKED,
    CONFIGURED
}

class AppsViewModel(application: Application) : AndroidViewModel(application) {
    private val inventory = AppInventoryRepository(application)
    private val policyRepository = AppPolicyRepository(FlowDatabaseProvider.get(application))
    private val usageRepository = UsageStatsRepository(application)
    private val protectionController = FlowProtectionController(application)
    private val policyWriteMutex = Mutex()

    private val _apps = MutableStateFlow<List<AppRow>>(emptyList())
    val apps: StateFlow<List<AppRow>> = _apps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterMode = MutableStateFlow(AppFilterMode.ALL)
    val filterMode: StateFlow<AppFilterMode> = _filterMode.asStateFlow()

    val visibleApps: StateFlow<List<AppRow>> = combine(
        _apps,
        _searchQuery,
        _filterMode
    ) { rows, query, mode ->
        AppListFilter.filter(
            apps = rows,
            query = query,
            blockedOnly = mode == AppFilterMode.BLOCKED,
            configuredOnly = mode == AppFilterMode.CONFIGURED
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        refresh()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterMode(mode: AppFilterMode) {
        _filterMode.value = mode
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            _errorMessage.value = null
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
            } catch (_: SecurityException) {
                _errorMessage.value = "تعذر قراءة إحصائيات التطبيقات. تحقق من صلاحية إحصائيات الاستخدام."
            } catch (_: Exception) {
                _errorMessage.value = "تعذر تحديث قائمة التطبيقات حاليًا. حاول مرة أخرى."
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
            policyWriteMutex.withLock {
                val current = _apps.value.firstOrNull { it.app.packageName == packageName }?.policy
                    ?: policyRepository.get(packageName)
                    ?: AppPolicy(packageName = packageName)
                val updated = transform(current)

                if (updated.blocked != current.blocked) {
                    policyRepository.setBlocked(packageName, updated.blocked)
                }
                if (updated.downloadLimitBytesPerSecond != current.downloadLimitBytesPerSecond ||
                    updated.uploadLimitBytesPerSecond != current.uploadLimitBytesPerSecond
                ) {
                    policyRepository.setSpeedLimits(
                        packageName,
                        updated.downloadLimitBytesPerSecond,
                        updated.uploadLimitBytesPerSecond
                    )
                }
                if (updated.dailyQuotaBytes != current.dailyQuotaBytes ||
                    updated.monthlyQuotaBytes != current.monthlyQuotaBytes
                ) {
                    policyRepository.setQuotas(
                        packageName,
                        updated.dailyQuotaBytes,
                        updated.monthlyQuotaBytes
                    )
                }
                if (updated.scheduleEnabled != current.scheduleEnabled ||
                    updated.scheduleStartMinutes != current.scheduleStartMinutes ||
                    updated.scheduleEndMinutes != current.scheduleEndMinutes
                ) {
                    policyRepository.setSchedule(
                        packageName,
                        updated.scheduleEnabled,
                        updated.scheduleStartMinutes,
                        updated.scheduleEndMinutes
                    )
                }
                if (updated.networkScope != current.networkScope) {
                    policyRepository.setNetworkScope(packageName, updated.networkScope)
                }

                _apps.value = _apps.value.map { row ->
                    if (row.app.packageName == packageName) row.copy(policy = updated) else row
                }
                runCatching { protectionController.reapply() }
            }
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
