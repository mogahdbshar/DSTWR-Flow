package com.dstwr.flow

import com.dstwr.flow.domain.util.DataFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class DataFormatterTest {
    @Test fun bytes_formatsBytes() { assertEquals("512 B", DataFormatter.bytes(512)) }
    @Test fun bytes_formatsMegabytes() { assertEquals("1.0 MB", DataFormatter.bytes(1024L * 1024L)) }
    @Test fun rate_formatsRate() { assertEquals("1.0 KB/s", DataFormatter.rate(1024)) }
}
