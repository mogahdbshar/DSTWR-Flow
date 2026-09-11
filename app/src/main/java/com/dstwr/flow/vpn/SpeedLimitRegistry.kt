package com.dstwr.flow.vpn

import com.dstwr.flow.domain.policy.TokenBucket
import java.util.concurrent.ConcurrentHashMap

/** Holds independent download/upload buckets per application package. */
class SpeedLimitRegistry {
    private val download = ConcurrentHashMap<String, TokenBucket>()
    private val upload = ConcurrentHashMap<String, TokenBucket>()

    fun configure(packageName: String, downloadBytesPerSecond: Long, uploadBytesPerSecond: Long) {
        configureBucket(download, packageName, downloadBytesPerSecond)
        configureBucket(upload, packageName, uploadBytesPerSecond)
    }

    fun consumeDownload(packageName: String, bytes: Long): TokenBucket.ConsumeResult =
        consume(download, packageName, bytes)

    fun consumeUpload(packageName: String, bytes: Long): TokenBucket.ConsumeResult =
        consume(upload, packageName, bytes)

    fun remove(packageName: String) {
        download.remove(packageName)
        upload.remove(packageName)
    }

    fun clear() {
        download.clear()
        upload.clear()
    }

    private fun configureBucket(map: ConcurrentHashMap<String, TokenBucket>, packageName: String, rate: Long) {
        if (rate <= 0L) map.remove(packageName)
        else map.compute(packageName) { _, old ->
            (old ?: TokenBucket(rate)).also { it.updateRate(rate) }
        }
    }

    private fun consume(map: ConcurrentHashMap<String, TokenBucket>, packageName: String, bytes: Long): TokenBucket.ConsumeResult =
        map[packageName]?.tryConsume(bytes) ?: TokenBucket.ConsumeResult(true, 0L)
}
