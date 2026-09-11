package com.dstwr.flow.domain.policy

/** User-facing speed units supported by the traffic policy model. */
enum class SpeedUnit(val bytesMultiplier: Long, val label: String) {
    KBPS(1024L, "KB/s"),
    MBPS(1024L * 1024L, "MB/s"),
    GBPS(1024L * 1024L * 1024L, "GB/s")
}

data class SpeedLimit(
    val value: Long = 0L,
    val unit: SpeedUnit = SpeedUnit.KBPS
) {
    val bytesPerSecond: Long
        get() = if (value <= 0L) 0L else try {
            Math.multiplyExact(value, unit.bytesMultiplier)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }

    val enabled: Boolean get() = bytesPerSecond > 0L
}

object SpeedLimitFormatter {
    fun fromBytesPerSecond(bytesPerSecond: Long): SpeedLimit {
        val safe = bytesPerSecond.coerceAtLeast(0L)
        return when {
            safe >= SpeedUnit.GBPS.bytesMultiplier -> SpeedLimit(safe / SpeedUnit.GBPS.bytesMultiplier, SpeedUnit.GBPS)
            safe >= SpeedUnit.MBPS.bytesMultiplier -> SpeedLimit(safe / SpeedUnit.MBPS.bytesMultiplier, SpeedUnit.MBPS)
            else -> SpeedLimit(safe / SpeedUnit.KBPS.bytesMultiplier, SpeedUnit.KBPS)
        }
    }
}
