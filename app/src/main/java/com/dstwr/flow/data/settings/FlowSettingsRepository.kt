package com.dstwr.flow.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.flowSettingsDataStore by preferencesDataStore(name = "dstwr_flow_settings")

/** Persists global protection settings locally on the device. */
class FlowSettingsRepository(private val context: Context) {
    private object Keys {
        val protectionEnabled = booleanPreferencesKey("protection_enabled")
        val emergencyBlockEnabled = booleanPreferencesKey("emergency_block_enabled")
    }

    val protectionEnabled: Flow<Boolean> = context.flowSettingsDataStore.data
        .map { it[Keys.protectionEnabled] ?: false }

    val emergencyBlockEnabled: Flow<Boolean> = context.flowSettingsDataStore.data
        .map { it[Keys.emergencyBlockEnabled] ?: false }

    suspend fun setProtectionEnabled(enabled: Boolean) {
        context.flowSettingsDataStore.edit { it[Keys.protectionEnabled] = enabled }
    }

    suspend fun setEmergencyBlockEnabled(enabled: Boolean) {
        context.flowSettingsDataStore.edit { it[Keys.emergencyBlockEnabled] = enabled }
    }

    suspend fun disableAllProtection() {
        context.flowSettingsDataStore.edit {
            it[Keys.protectionEnabled] = false
            it[Keys.emergencyBlockEnabled] = false
        }
    }
}
