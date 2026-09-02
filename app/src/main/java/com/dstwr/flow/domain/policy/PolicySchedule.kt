package com.dstwr.flow.domain.policy

/** Returns whether a minute-of-day falls inside a possibly overnight schedule. */
object PolicySchedule {
    fun isActive(
        minuteOfDay: Int,
        enabled: Boolean,
        startMinutes: Int,
        endMinutes: Int
    ): Boolean {
        if (!enabled) return false
        val minute = minuteOfDay.coerceIn(0, 1439)
        val start = startMinutes.coerceIn(0, 1439)
        val end = endMinutes.coerceIn(0, 1439)

        return if (start <= end) {
            minute in start..end
        } else {
            minute >= start || minute <= end
        }
    }
}
