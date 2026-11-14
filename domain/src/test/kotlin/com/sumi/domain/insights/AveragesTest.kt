package com.sumi.domain.insights

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One denominator rule (the window's total tracked usage), one rounding rule
 * (whole percents, half-up, clamped), and window-stated averages.
 */
class AveragesTest {

    @Test
    fun `average includes zero days in the window`() {
        // 8 hours across 7 days (4 non-zero days of 8h each) = 4,571,428 ms per day (integer floor).
        assertEquals(
            32_000_000L / 7,
            Averages.dailyAverage(listOf(0L, 0L, 0L, 8_000_000L, 8_000_000L, 8_000_000L, 8_000_000L)),
        )
        assertEquals(
            7_200_000L / 7,
            Averages.dailyAverage(listOf(0L, 0L, 0L, 0L, 0L, 0L, 7_200_000L)),
        )
    }

    @Test
    fun `average by explicit window overrides list size`() {
        assertEquals(3_600_000L, Averages.dailyAverage(25_200_000L, 7))
        assertEquals(0L, Averages.dailyAverage(25_200_000L, 0))
        assertEquals(0L, Averages.dailyAverage(25_200_000L, -1))
    }

    @Test
    fun `percent shares a single rule and clamps`() {
        assertEquals(50, Averages.percentOf(50L, 100L))
        assertEquals(0, Averages.percentOf(50L, 0L)) // guard against divide-by-zero
        assertEquals(100, Averages.percentOf(100L, 100L))
        assertEquals(0, Averages.percentOf(0L, 100L))
    }

    @Test
    fun `percent from float share matches the long rule`() {
        assertEquals(50, Averages.percentOfShare(0.5f))
        assertEquals(0, Averages.percentOfShare(0.0f))
        assertEquals(100, Averages.percentOfShare(1.0f))
        assertEquals(0, Averages.percentOfShare(-0.5f)) // clamped
        assertEquals(100, Averages.percentOfShare(1.5f)) // clamped
    }
}
