package com.dstwr.flow.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dstwr.flow.data.settings.FlowSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Exposes persistent global protection and notification state to Compose. */
data class FlowProtectionState(
    val protectionEnabled: Boolean = false,
    val emergencyBlockEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true
)

class FlowSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FlowSettingsRepository(application.applicationContext)

    val state: StateFlow<FlowProtectionState> = combine(
        repository.protectionEnabled,
        repository.emergencyBlockEnabled,
        repository.notificationsEnabled
    ) { protection, emergency, notifications ->
        FlowProtectionState(
            protectionEnabled = protection,
            emergencyBlockEnabled = emergency,
            notificationsEnabled = notifications
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FlowProtectionState()
    )

    fun setProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setProtectionEnabled(enabled)
        }
    }

    fun setEmergencyBlockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setEmergencyBlockEnabled(enabled)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setNotificationsEnabled(enabled)
        }
    }

    fun disableAllProtection() {
        viewModelScope.launch {
            repository.disableAllProtection()
        }
    }
}
