package com.dstwr.flow.domain.model

data class AppPolicy(
    val packageName: String,
    val blocked: Boolean = false,
    val downloadLimitBytesPerSecond: Long = 0L,
    val uploadLimitBytesPerSecond: Long = 0L,
    val dailyQuotaBytes: Long = 0L,
    val monthlyQuotaBytes: Long = 0L,
    val scheduleEnabled: Boolean = false,
    val scheduleStartMinutes: Int = 0,
    val scheduleEndMinutes: Int = 1439
)

enum class NetworkScope { WIFI, MOBILE, ALL }

enum class ControlMode { MONITOR_ONLY, ENFORCE_RULES }
