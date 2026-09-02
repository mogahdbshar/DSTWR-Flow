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
        viewModelScope.launch(Dispatchers.IO) {
            policyRepository.setBlocked(packageName, blocked)
            _apps.value = _apps.value.map { row ->
                if (row.app.packageName == packageName) {
                    row.copy(policy = row.policy.copy(blocked = blocked))
                } else row
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
