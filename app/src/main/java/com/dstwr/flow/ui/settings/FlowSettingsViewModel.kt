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

/** Exposes persistent global protection state to Compose. */
data class FlowProtectionState(
    val protectionEnabled: Boolean = false,
    val emergencyBlockEnabled: Boolean = false
)

class FlowSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FlowSettingsRepository(application.applicationContext)

    val state: StateFlow<FlowProtectionState> = combine(
        repository.protectionEnabled,
        repository.emergencyBlockEnabled
    ) { protection, emergency ->
        FlowProtectionState(protectionEnabled = protection, emergencyBlockEnabled = emergency)
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

    fun disableAllProtection() {
        viewModelScope.launch {
            repository.disableAllProtection()
        }
    }
}
