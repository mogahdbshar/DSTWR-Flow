package com.dstwr.flow.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DataFormatterTest {
    @Test
    fun formatsBytesAndBinaryUnits() {
        assertEquals("0 B", DataFormatter.bytes(0L))
        assertEquals("1023 B", DataFormatter.bytes(1023L))
        assertEquals("1.0 KB", DataFormatter.bytes(1024L))
        assertEquals("1.0 MB", DataFormatter.bytes(1024L * 1024L))
        assertEquals("1.0 GB", DataFormatter.bytes(1024L * 1024L * 1024L))
    }

    @Test
    fun preservesNegativeByteValue() {
        assertEquals("-1 B", DataFormatter.bytes(-1L))
        assertEquals("-1.0 KB", DataFormatter.bytes(-1024L))
    }

    @Test
    fun formatsRateUsingBytesFormatter() {
        assertEquals("1.0 KB/s", DataFormatter.rate(1024L))
        assertEquals("2.0 KB/s", DataFormatter.rate(2048L))
    }
}
