package com.sumi.feature.insights

import com.sumi.core.database.entity.AppCategory
import com.sumi.core.database.entity.Application
import com.sumi.domain.insights.InsightsRange
import com.sumi.domain.usage.DailyAppUsage
import com.sumi.domain.usage.DailyAppUsageSource
import com.sumi.domain.usage.DailyUsage
import com.sumi.domain.usage.HourlyUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Feature-logic derivation for the Insights screen — the rules that shape the numbers. */
class InsightsLogicTest {

    private val todayKey = "2026-11-14" // Saturday
    private val weekKeys = listOf(
        "2026-11-08", // Sunday
        "2026-11-09",
        "2026-11-10",
        "2026-11-11",
        "2026-11-12",
        "2026-11-13",
        todayKey,
    )

    private fun usage(key: String, pkg: String, ms: Long) =
        DailyAppUsage(key, pkg, ms, DailyAppUsageSource.SESSIONS)

    // --- dayBars ---

    @Test
    fun `day bars mark only today as partial`() {
        val totals = weekKeys.zip(weekKeys.indices.map { 1_000_000L }).toMap()
        val bars = dayBars(weekKeys, totals, todayKey)
        assertEquals(listOf(false, false, false, false, false, false, true), bars.map { it.isPartial })
    }

    @Test
    fun `day bars speak the takeaway for screen readers`() {
        val totals = weekKeys.zip(weekKeys.indices.map { 1_000_000L }).toMap()
        val bars = dayBars(weekKeys, totals, todayKey)
        assertEquals(7, bars.size)
        assertTrue(bars.last().contentDescription.endsWith("so far"))
        assertTrue(!bars.first().contentDescription.contains("so far"))
    }

    @Test
    fun `week bars use short weekday labels, month bars day numbers`() {
        val weekBars = dayBars(weekKeys, emptyMap(), todayKey)
        assertEquals("Su", weekBars.first().label)

        val monthKeys = (1..30).map { "2026-11-${it.toString().padStart(2, '0')}" }
        val monthBars = dayBars(monthKeys, emptyMap(), "2026-11-30")
        assertEquals("1", monthBars.first().label)
        assertEquals("30", monthBars.last().label)
    }

    // --- coverage ---

    @Test
    fun `coverage counts days with any recorded row`() {
        val daily = listOf(DailyUsage("2026-11-10", 1L, 0L))
        val perApp = listOf(usage("2026-11-12", "com.a", 1L))
        val hourly = listOf(HourlyUsage("2026-11-09", 10, "com.a", 1L))
        val coverage = coverageDays(weekKeys, daily, perApp, hourly, emptySet())
        assertEquals(3, coverage)
    }

    @Test
    fun `coverage note shows only when history is thinner than the window`() {
        assertNull(coverageNote(7, 7))
        assertNull(coverageNote(30, 30))
        assertEquals(
            "Based on the 2 days Sumi has recorded in this range.",
            coverageNote(2, 7),
        )
    }

    // --- window totals ---

    @Test
    fun `window total includes zero days`() {
        val totals = mapOf("2026-11-10" to 1_000_000L, "2026-11-12" to 2_000_000L)
        assertEquals(3_000_000L, windowTotalMs(weekKeys, totals))
    }

    // --- headline comparison ---

    @Test
    fun `headline comparison for TODAY needs a clipped previous value`() {
        assertNull(headlineComparison(InsightsRange.TODAY, 5_000L, null, previousWindowRecorded = true))
        assertNotNull(headlineComparison(InsightsRange.TODAY, 5_000L, 4_000L, previousWindowRecorded = true))
    }

    @Test
    fun `complete windows compare zeros-included or not at all`() {
        // No row in the previous window at all → omitted.
        assertNull(headlineComparison(InsightsRange.WEEK, 5_000L, null, previousWindowRecorded = false))
        // Recorded window, zeros included → shown even when previous total is 0.
        assertNotNull(headlineComparison(InsightsRange.WEEK, 5_000L, 0L, previousWindowRecorded = true))
    }

    // --- pattern facts ---

    @Test
    fun `pattern facts split morning and evening from hourly buckets`() {
        val hourly = listOf(
            HourlyUsage("2026-11-09", 7, "com.a", 600_000L),  // morning hour
            HourlyUsage("2026-11-09", 18, "com.a", 900_000L), // evening hour
            HourlyUsage("2026-11-10", 21, "com.a", 900_000L), // evening hour
        )
        val facts = patternFacts(weekKeys, hourly, emptyMap())
        assertEquals(600_000L, facts.morningMs)
        assertEquals(1_800_000L, facts.eveningMs)
        assertEquals(21, facts.busiestHour)
        assertEquals(900_000L, facts.busiestHourMs)
    }

    @Test
    fun `pattern facts split weekdays and weekends of the window`() {
        // 2026-11-09..13 are Mon–Fri (5 days); 2026-11-08 is Sunday; 2026-11-14 is Saturday.
        val totals = mapOf(
            "2026-11-08" to 300L,
            "2026-11-09" to 100L,
            "2026-11-10" to 100L,
            "2026-11-11" to 100L,
            "2026-11-12" to 100L,
            "2026-11-13" to 100L,
            todayKey to 200L,
        )
        val facts = patternFacts(weekKeys, emptyList(), totals)
        assertEquals(500L, facts.weekdayMs)
        assertEquals(500L, facts.weekendMs)
        assertEquals(5, facts.weekdayDays)
        assertEquals(2, facts.weekendDays)
    }

    // --- social facts ---

    private val socialCategories = listOf(AppCategory("social", "Social", "matcha-4", sortOrder = 0))
    private val socialApps = listOf(
        Application(packageName = "com.feed", displayName = "Feed", categoryId = "social"),
        Application(packageName = "com.chat", displayName = "Chat", categoryId = "social"),
        Application(packageName = "com.other", displayName = "Other", categoryId = null),
    )

    @Test
    fun `social card hides when the Social category no longer exists`() {
        val result = socialFacts(
            range = InsightsRange.WEEK,
            rangeKeys = weekKeys,
            trackedMs = 1_000_000L,
            perApp = listOf(usage(todayKey, "com.feed", 500_000L)),
            comparisonPerApp = emptyList(),
            comparisonRecorded = false,
            applications = socialApps,
            categories = emptyList(), // user deleted the Social category
        )
        assertNull(result)
    }

    @Test
    fun `social facts use the current assignment as membership`() {
        val result = socialFacts(
            range = InsightsRange.WEEK,
            rangeKeys = weekKeys,
            trackedMs = 1_000_000L,
            perApp = listOf(
                usage(todayKey, "com.feed", 300_000L),
                usage(todayKey, "com.other", 700_000L), // uncategorized — never counts as social
            ),
            comparisonPerApp = emptyList(),
            comparisonRecorded = false,
            applications = socialApps,
            categories = socialCategories,
        )
        assertNotNull(result)
        assertEquals(300_000L, result!!.totalMs)
        assertEquals(0.3f, result.share, 1e-6f)
        assertEquals(300_000L, result.dailyMs.last())
        assertEquals(listOf("Feed"), result.topApps.map { it.displayName })
    }

    @Test
    fun `social comparison needs a recorded previous window and never shows for today`() {
        val week = socialFacts(
            range = InsightsRange.WEEK,
            rangeKeys = weekKeys,
            trackedMs = 1_000_000L,
            perApp = listOf(usage(todayKey, "com.feed", 300_000L)),
            comparisonPerApp = listOf(usage("2026-11-01", "com.feed", 100_000L)),
            comparisonRecorded = true,
            applications = socialApps,
            categories = socialCategories,
        )
        assertNotNull(week!!.comparison)

        val weekNoHistory = socialFacts(
            range = InsightsRange.WEEK,
            rangeKeys = weekKeys,
            trackedMs = 1_000_000L,
            perApp = listOf(usage(todayKey, "com.feed", 300_000L)),
            comparisonPerApp = emptyList(),
            comparisonRecorded = false,
            applications = socialApps,
            categories = socialCategories,
        )
        assertNull(weekNoHistory!!.comparison)

        val today = socialFacts(
            range = InsightsRange.TODAY,
            rangeKeys = listOf(todayKey),
            trackedMs = 1_000_000L,
            perApp = listOf(usage(todayKey, "com.feed", 300_000L)),
            comparisonPerApp = listOf(usage("2026-11-13", "com.feed", 100_000L)),
            comparisonRecorded = true,
            applications = socialApps,
            categories = socialCategories,
        )
        assertNull(today!!.comparison)
    }

    @Test
    fun `social facts carry a per-day series across the range`() {
        val result = socialFacts(
            range = InsightsRange.WEEK,
            rangeKeys = weekKeys,
            trackedMs = 1_000_000L,
            perApp = listOf(
                usage("2026-11-09", "com.chat", 200_000L),
                usage("2026-11-12", "com.feed", 400_000L),
            ),
            comparisonPerApp = emptyList(),
            comparisonRecorded = true,
            applications = socialApps,
            categories = socialCategories,
        )
        assertEquals(listOf(0L, 200_000L, 0L, 0L, 400_000L, 0L, 0L), result!!.dailyMs)
    }

    @Test
    fun `range caption covers single day and 7-day windows`() {
        assertEquals("Saturday, Nov 14, 2026", rangeCaption(listOf(todayKey)))
        assertEquals("Nov 8 – Nov 14, 2026", rangeCaption(weekKeys))
    }
}
