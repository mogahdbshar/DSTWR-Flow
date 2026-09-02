package com.dstwr.flow.domain.util

import kotlin.math.abs

object DataFormatter {
    fun bytes(value: Long): String {
        val bytes = abs(value.toDouble())
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var n = bytes
        var i = 0
        while (n >= 1024 && i < units.lastIndex) { n /= 1024; i++ }
        return if (i == 0) "${value} B" else "%.1f %s".format(n, units[i])
    }

    fun rate(bytesPerSecond: Long): String = "${bytes(bytesPerSecond)}/s"
}
