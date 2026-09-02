package com.dstwr.flow.ui.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dstwr.flow.data.apps.AppInventoryRepository
import com.dstwr.flow.data.apps.InstalledApp
import com.dstwr.flow.data.apps.AppPolicyRepository
import com.dstwr.flow.data.local.FlowDatabaseProvider
import com.dstwr.flow.domain.model.AppPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

 data class AppRow(
    val app: InstalledApp,
    val policy: AppPolicy
) {
    val blocked: Boolean get() = policy.blocked
}

class AppsViewModel(application: Application) : AndroidViewModel(application) {
    private val inventory = AppInventoryRepository(application)
    private val policyRepository = AppPolicyRepository(FlowDatabaseProvider.get(application))

    private val _apps = MutableStateFlow<List<AppRow>>(emptyList())
    val apps: StateFlow<List<AppRow>> = _apps.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            val installed = inventory.getLaunchableApps()
            val policies = policyRepository.getAll().associateBy { it.packageName }
            _apps.value = installed.map { app ->
                AppRow(
                    app = app,
                    policy = policies[app.packageName] ?: AppPolicy(packageName = app.packageName)
                )
            }
            _loading.value = false
        }
    }

    fun setBlocked(packageName: String, blocked: Boolean) {
        viewModelScope.launch {
            policyRepository.setBlocked(packageName, blocked)
            _apps.value = _apps.value.map { row ->
                if (row.app.packageName == packageName) {
                    row.copy(policy = row.policy.copy(blocked = blocked))
                } else row
            }
        }
    }
}
