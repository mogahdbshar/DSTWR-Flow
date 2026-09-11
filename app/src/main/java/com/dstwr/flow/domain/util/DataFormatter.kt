package com.dstwr.flow.domain.util

import java.util.Locale
import kotlin.math.abs

object DataFormatter {
    fun bytes(value: Long): String {
        val magnitude = abs(value.toDouble())
        val sign = if (value < 0L) "-" else ""
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var n = magnitude
        var i = 0
        while (n >= 1024 && i < units.lastIndex) {
            n /= 1024
            i++
        }
        return if (i == 0) "${value} B"
        else "$sign%.1f %s".format(Locale.US, n, units[i])
    }

    fun rate(bytesPerSecond: Long): String = "${bytes(bytesPerSecond)}/s"
}
