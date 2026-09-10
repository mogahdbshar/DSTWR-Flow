package com.dstwr.flow.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageHistoryCalculatorTest {
    @Test
    fun fewerThanTwoPointsReturnsEmpty() {
        val points = listOf(UsageHistoryPoint(1L, 100L, 50L, 150L))

        assertTrue(UsageHistoryCalculator.toIntervals(points).isEmpty())
    }

    @Test
    fun increasingCumulativeCountersBecomeIntervalDeltas() {
        val points = listOf(
            UsageHistoryPoint(1L, 100L, 50L, 150L),
            UsageHistoryPoint(2L, 160L, 90L, 250L),
            UsageHistoryPoint(3L, 220L, 130L, 350L)
        )

        assertEquals(
            listOf(
                UsageHistoryPoint(2L, 60L, 40L, 100L),
                UsageHistoryPoint(3L, 60L, 40L, 100L)
            ),
            UsageHistoryCalculator.toIntervals(points)
        )
    }

    @Test
    fun counterResetDoesNotProduceNegativeUsage() {
        val points = listOf(
            UsageHistoryPoint(1L, 500L, 300L, 800L),
            UsageHistoryPoint(2L, 100L, 120L, 220L)
        )

        assertEquals(
            listOf(UsageHistoryPoint(2L, 0L, 0L, 0L)),
            UsageHistoryCalculator.toIntervals(points)
        )
    }

    @Test
    fun unsortedPointsAreSortedBeforeCalculatingDeltas() {
        val points = listOf(
            UsageHistoryPoint(3L, 300L, 150L, 450L),
            UsageHistoryPoint(1L, 100L, 50L, 150L),
            UsageHistoryPoint(2L, 220L, 90L, 310L)
        )

        assertEquals(
            listOf(
                UsageHistoryPoint(2L, 120L, 40L, 160L),
                UsageHistoryPoint(3L, 80L, 60L, 140L)
            ),
            UsageHistoryCalculator.toIntervals(points)
        )
    }
}
