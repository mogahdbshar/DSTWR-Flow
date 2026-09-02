package com.dstwr.flow.domain.policy

import java.util.Calendar

/** Immutable time boundaries used when evaluating daily and monthly quotas. */
data class PolicyTimeWindow(
    val minuteOfDay: Int,
    val startOfDayMillis: Long,
    val startOfMonthMillis: Long
)

object PolicyTimeWindowFactory {
    fun fromMillis(nowMillis: Long): PolicyTimeWindow {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }

        val day = now.clone() as Calendar
        day.set(Calendar.HOUR_OF_DAY, 0)
        day.set(Calendar.MINUTE, 0)
        day.set(Calendar.SECOND, 0)
        day.set(Calendar.MILLISECOND, 0)

        val month = day.clone() as Calendar
        month.set(Calendar.DAY_OF_MONTH, 1)

        return PolicyTimeWindow(
            minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE),
            startOfDayMillis = day.timeInMillis,
            startOfMonthMillis = month.timeInMillis
        )
    }
}
