package com.sumi.feature.appdetail

import com.sumi.domain.insights.ActiveWindow
import com.sumi.domain.usage.DailyAppUsage
import com.sumi.domain.usage.UsageSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure-logic coverage for the app detail screen (charts, summaries, honest-window copy). */
class AppDetailLogicTest {

    private fun usage(dateKey: String, ms: Long) =
        DailyAppUsage(dateKey = dateKey, packageName = "com.app", durationMs = ms)

    @Test
    fun `dailySeries is dense, oldest-first, zero-filled`() {
        val daily = listOf(
            usage("2026-11-10", 10L),
            usage("2026-11-12", 30L),
        )
        val series = dailySeries(daily, "2026-11-12", days = 7)
        assertEquals(7, series.size)
        // Index 4 = 2026-11-10, index 5 = 2026-11-11, index 6 = today.
        assertEquals(30L, series.last())
        assertEquals(0L, series[5])
        assertEquals(10L, series[4])
    }

    @Test
    fun `sessionSummary with one session names it`() {
        val sessions = listOf(
            UsageSession("com.app", 0L, 10L * 60_000L, isEstimated = false),
        )
        assertEquals("One session today, 10m long.", sessionSummary(sessions, nowUtcMs = 20L * 60_000L))
    }

    @Test
    fun `sessionSummary with several sessions reports the count and the longest`() {
        val sessions = listOf(
            UsageSession("com.app", 0L, 10L * 60_000L, isEstimated = false),
            UsageSession("com.app", 100_000L, 500_000L, isEstimated = false), // ~6m40s, sub-minute rounds down
        )
        assertEquals("2 sessions today, longest 10m.", sessionSummary(sessions, nowUtcMs = 1_000_000L))
    }

    @Test
    fun `sessionSummary with no sessions is null`() {
        assertNull(sessionSummary(emptyList(), nowUtcMs = 1_000_000L))
    }

    @Test
    fun `sessionSummary measures open sessions up to now`() {
        val sessions = listOf(
            UsageSession("com.app", 1_000L, null, isEstimated = false),
        )
        val summary = sessionSummary(sessions, nowUtcMs = 61_000L) // 1m open
        assertEquals("One session today, 1m long.", summary)
    }

    @Test
    fun `sessionSummary clips a spill-over session to the day when dayStart is given`() {
        val dayStart = 12L * 60L * 60_000L
        val sessions = listOf(
            // Started 30 minutes before the day boundary, 20 minutes into the day.
            UsageSession("com.app", dayStart - 30L * 60_000L, dayStart + 20L * 60_000L, isEstimated = false),
        )
        val summary = sessionSummary(sessions, nowUtcMs = dayStart + 60L * 60_000L, dayStartUtcMs = dayStart)
        assertEquals("One session today, 20m long.", summary)
    }

    @Test
    fun `formatHourWindow pads and renders midnight for 24`() {
        assertEquals("21:00–00:00", formatHourWindow(ActiveWindow(21, 24, 5)))
        assertEquals("08:00–11:00", formatHourWindow(ActiveWindow(8, 11, 5)))
    }
}
