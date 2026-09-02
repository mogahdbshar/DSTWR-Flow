package com.dstwr.flow.domain.policy

import java.util.Calendar

/** Pure date/time boundaries used by quota evaluation. */
data class PolicyTimeWindow(
    val nowMillis: Long,
    val minuteOfDay: Int,
    val dayStartMillis: Long,
    val monthStartMillis: Long
)

object PolicyTime {
    fun fromMillis(nowMillis: Long = System.currentTimeMillis()): PolicyTimeWindow {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val day = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val month = (day.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return PolicyTimeWindow(
            nowMillis = nowMillis,
            minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE),
            dayStartMillis = day.timeInMillis,
            monthStartMillis = month.timeInMillis
        )
    }
}
