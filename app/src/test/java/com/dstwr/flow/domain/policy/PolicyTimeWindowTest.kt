package com.dstwr.flow.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class PolicyTimeWindowTest {
    @Test
    fun derivesDayAndMonthBoundariesInSameTimezone() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 3, 14, 27, 45)
            set(Calendar.MILLISECOND, 123)
        }
        val result = PolicyTimeWindowFactory.fromMillis(calendar.timeInMillis)

        val day = Calendar.getInstance().apply { timeInMillis = result.startOfDayMillis }
        val month = Calendar.getInstance().apply { timeInMillis = result.startOfMonthMillis }

        assertEquals(14 * 60 + 27, result.minuteOfDay)
        assertEquals(0, day.get(Calendar.HOUR_OF_DAY))
        assertEquals(3, day.get(Calendar.DAY_OF_MONTH))
        assertEquals(1, month.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SEPTEMBER, month.get(Calendar.MONTH))
    }
}
