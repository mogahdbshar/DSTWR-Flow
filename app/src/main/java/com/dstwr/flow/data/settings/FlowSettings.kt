package com.dstwr.flow.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.flowDataStore by preferencesDataStore(name = "dstwr_flow_settings")

data class FlowSettings(
    val protectionEnabled: Boolean = false,
    val emergencyBlockEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val language: String = "ar",
    val refreshSeconds: Int = 15,
    val dailyLimitBytes: Long = 0L
)

/** Single local DataStore repository for all global DSTWR Flow settings. */
class FlowSettingsRepository(private val context: Context) {
    private object Keys {
        val protection = booleanPreferencesKey("protection_enabled")
        val emergency = booleanPreferencesKey("emergency_block_enabled")
        val notifications = booleanPreferencesKey("notifications_enabled")
        val language = stringPreferencesKey("language")
        val refresh = intPreferencesKey("refresh_seconds")
        val daily = longPreferencesKey("daily_limit_bytes")
    }

    val settings: Flow<FlowSettings> = context.flowDataStore.data.map { p ->
        FlowSettings(
            protectionEnabled = p[Keys.protection] ?: false,
            emergencyBlockEnabled = p[Keys.emergency] ?: false,
            notificationsEnabled = p[Keys.notifications] ?: true,
            language = p[Keys.language] ?: "ar",
            refreshSeconds = p[Keys.refresh] ?: 15,
            dailyLimitBytes = p[Keys.daily] ?: 0L
        )
    }

    val protectionEnabled: Flow<Boolean> = settings.map { it.protectionEnabled }
    val emergencyBlockEnabled: Flow<Boolean> = settings.map { it.emergencyBlockEnabled }
    val notificationsEnabled: Flow<Boolean> = settings.map { it.notificationsEnabled }

    suspend fun isProtectionEnabled(): Boolean = protectionEnabled.first()
    suspend fun isEmergencyBlockEnabled(): Boolean = emergencyBlockEnabled.first()
    suspend fun areNotificationsEnabled(): Boolean = notificationsEnabled.first()

    suspend fun setProtectionEnabled(enabled: Boolean) {
        context.flowDataStore.edit { it[Keys.protection] = enabled }
    }

    suspend fun setEmergencyBlockEnabled(enabled: Boolean) {
        context.flowDataStore.edit { it[Keys.emergency] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.flowDataStore.edit { it[Keys.notifications] = enabled }
    }

    suspend fun disableAllProtection() {
        context.flowDataStore.edit {
            it[Keys.protection] = false
            it[Keys.emergency] = false
        }
    }

    suspend fun setProtection(enabled: Boolean) = setProtectionEnabled(enabled)
    suspend fun setEmergency(enabled: Boolean) = setEmergencyBlockEnabled(enabled)
    suspend fun setLanguage(language: String) {
        context.flowDataStore.edit { it[Keys.language] = language }
    }

    suspend fun setRefreshSeconds(seconds: Int) {
        context.flowDataStore.edit { it[Keys.refresh] = seconds.coerceIn(5, 300) }
    }

    suspend fun setDailyLimit(bytes: Long) {
        context.flowDataStore.edit { it[Keys.daily] = bytes.coerceAtLeast(0L) }
    }
}
