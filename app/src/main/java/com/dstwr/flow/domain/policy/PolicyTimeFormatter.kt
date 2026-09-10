package com.dstwr.flow.domain.policy

/** Formats policy minutes for a stable 24-hour display. */
object PolicyTimeFormatter {
    fun format(minutes: Int): String {
        val safe = minutes.coerceIn(0, 1439)
        val hour = safe / 60
        val minute = safe % 60
        return "%02d:%02d".format(hour, minute)
    }
}
