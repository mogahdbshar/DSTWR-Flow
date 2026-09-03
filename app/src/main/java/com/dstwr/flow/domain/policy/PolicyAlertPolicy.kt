package com.dstwr.flow.domain.policy

/** Determines when a quota should produce a user-facing alert. */
object PolicyAlertPolicy {
    const val WARNING_PERCENT = 80

    fun percentUsed(usedBytes: Long, quotaBytes: Long): Int {
        if (quotaBytes <= 0L) return 0
        val used = usedBytes.coerceAtLeast(0L)
        return ((used.toDouble() / quotaBytes.toDouble()) * 100.0)
            .toInt()
            .coerceIn(0, 100)
    }

    fun shouldWarn(usedBytes: Long, quotaBytes: Long): Boolean =
        quotaBytes > 0L && percentUsed(usedBytes, quotaBytes) >= WARNING_PERCENT && usedBytes < quotaBytes

    fun isReached(usedBytes: Long, quotaBytes: Long): Boolean =
        quotaBytes > 0L && usedBytes.coerceAtLeast(0L) >= quotaBytes
}
