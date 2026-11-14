package com.sumi.feature.categories

import com.sumi.core.database.entity.AppCategory
import com.sumi.core.database.entity.Application
import com.sumi.domain.insights.InsightsRange
import com.sumi.domain.usage.DailyAppUsage
import com.sumi.domain.usage.DailyAppUsageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Derivation tests for the range-aware category detail screen — in particular the
 * reassignment policy: the series and the share denominator use the CURRENT assignment,
 * while raw per-app records are never rewritten. Comparison windows cover complete
 * days; TODAY is never compared against a complete day.
 */
class CategoryDetailLogicTest {

    private val categories = listOf(
        AppCategory("social", "Social", "matcha-4", sortOrder = 0),
        AppCategory("entertainment", "Entertainment", "ochre", sortOrder = 2),
    )

    private fun app(pkg: String, name: String, categoryId: String?) =
        Application(packageName = pkg, displayName = name, categoryId = categoryId)

    private fun usage(key: String, pkg: String, ms: Long) =
        DailyAppUsage(key, pkg, ms, DailyAppUsageSource.SESSIONS)

    private val today = "2026-11-13"
    private val rangeKeys = listOf(
        "2026-11-07",
        "2026-11-08",
        "2026-11-09",
        "2026-11-10",
        "2026-11-11",
        "2026-11-12",
        today,
    )
    private val comparisonKeys = listOf(
        "2026-10-31",
        "2026-11-01",
        "2026-11-02",
        "2026-11-03",
        "2026-11-04",
        "2026-11-05",
        "2026-11-06",
    )

    @Test
    fun `unknown category id yields the removed state`() {
        val result = deriveState(
            "missing",
            InsightsRange.WEEK,
            rangeKeys,
            comparisonKeys,
            today,
            categories,
            emptyList(),
            emptyList(),
        )
        assertFalse(result.exists)
    }

    @Test
    fun `unknown category with existing categories still reports removed`() {
        val result = deriveState(
            "missing",
            InsightsRange.WEEK,
            rangeKeys,
            comparisonKeys,
            today,
            categories,
            listOf(app("com.a", "A", "social")),
            emptyList(),
        )
        assertFalse(result.exists)
    }

    @Test
    fun `range total and share reflect the current assignment and tracked total`() {
        val apps = listOf(
            app("com.instagram.android", "Instagram", "social"),
            app("com.duolingo", "Duolingo", "education"),
        )
        val rangeUsage = listOf(
            usage(today, "com.instagram.android", 3_600_000L),
            usage(today, "com.duolingo", 1_000_000L),
            usage(today, "com.unknown", 400_000L),
        )
        val result = deriveState(
            "social", InsightsRange.WEEK, rangeKeys, comparisonKeys, today,
            categories, apps, rangeUsage,
        )
        assertTrue(result.exists)
        assertEquals(3_600_000L, result.rangeTotalMs)
        // Tracked total for the range = 3.6 + 1.0 + 0.4 = 5.0h → share 72%.
        assertEquals(0.72f, result.rangeShare, 1e-6f)
        assertEquals(1, result.apps.size)
    }

    @Test
    fun `series applies the current assignment - reassigned apps leave the old category`() {
        val apps = listOf(app("com.instagram.android", "Instagram", "entertainment")) // was social, now entertainment
        val rangeUsage = listOf(usage(today, "com.instagram.android", 2_000_000L))
        val result = deriveState(
            "social", InsightsRange.WEEK, rangeKeys, comparisonKeys, today,
            categories, apps, rangeUsage,
        )
        assertEquals(List(7) { 0L }, result.series)
        assertEquals(0L, result.rangeTotalMs)
    }

    @Test
    fun `uncategorized apps never leak into a category bucket`() {
        val apps = listOf(app("com.instagram.android", "Instagram", null))
        val rangeUsage = listOf(usage(today, "com.instagram.android", 2_000_000L))
        val result = deriveState(
            "social", InsightsRange.WEEK, rangeKeys, comparisonKeys, today,
            categories, apps, rangeUsage,
        )
        assertEquals(List(7) { 0L }, result.series)
        assertEquals(0L, result.rangeTotalMs)
    }

    @Test
    fun `apps rank by range usage with an alphabetical tiebreak`() {
        val apps = listOf(
            app("com.b", "Beta", "social"),
            app("com.a", "Alpha", "social"),
            app("com.c", "Gamma", "social"),
        )
        val rangeUsage = listOf(
            usage(today, "com.b", 1_000L),
            usage(today, "com.a", 1_000L),
            usage(today, "com.c", 5_000L),
        )
        val result = deriveState(
            "social", InsightsRange.WEEK, rangeKeys, comparisonKeys, today,
            categories, apps, rangeUsage,
        )
        assertEquals(listOf("Gamma", "Alpha", "Beta"), result.apps.map { it.displayName })
    }

    @Test
    fun `apps aggregate across days of the range`() {
        val apps = listOf(app("com.instagram.android", "Instagram", "social"))
        val rangeUsage = listOf(
            usage(rangeKeys[0], "com.instagram.android", 1_000_000L),
            usage(rangeKeys[3], "com.instagram.android", 2_000_000L),
            usage(today, "com.instagram.android", 500_000L),
        )
        val result = deriveState(
            "social", InsightsRange.WEEK, rangeKeys, comparisonKeys, today,
            categories, apps, rangeUsage,
        )
        assertEquals(3_500_000L, result.rangeTotalMs)
        assertEquals(listOf(1_000_000L, 0L, 0L, 2_000_000L, 0L, 0L, 500_000L), result.series)
        assertEquals(3_500_000L, result.apps.single().rangeMs)
    }

    @Test
    fun `comparison needs a recorded previous window and is omitted for TODAY`() {
        val apps = listOf(app("com.a", "A", "social"))
        val rangeUsage = listOf(usage(today, "com.a", 2_000_000L))
        val comparison = listOf(usage(comparisonKeys[0], "com.a", 1_000_000L))

        val week = deriveState(
            "social", InsightsRange.WEEK, rangeKeys, comparisonKeys, today,
            categories, apps, rangeUsage + comparison,
        )
        assertNotNull(week.comparison)

        val weekNoHistory = deriveState(
            "social", InsightsRange.WEEK, rangeKeys, comparisonKeys, today,
            categories, apps, rangeUsage,
        )
        assertNull(weekNoHistory.comparison)

        val todayResult = deriveState(
            "social", InsightsRange.TODAY, listOf(today), comparisonKeys, today,
            categories, apps, rangeUsage + comparison,
        )
        assertNull(todayResult.comparison)
    }

    @Test
    fun `most and quietest day are reported only with recorded data`() {
        val apps = listOf(app("com.a", "A", "social"))
        val rangeUsage = listOf(usage(rangeKeys[2], "com.a", 1_000_000L))
        val result = deriveState(
            "social", InsightsRange.WEEK, rangeKeys, comparisonKeys, today,
            categories, apps, rangeUsage,
        )
        assertEquals(2, result.mostActiveDayIndex)
        assertEquals(0, result.quietestDayIndex)

        val empty = deriveState(
            "social", InsightsRange.WEEK, rangeKeys, comparisonKeys, today,
            categories, apps, emptyList(),
        )
        assertNull(empty.mostActiveDayIndex)
        assertNull(empty.quietestDayIndex)
    }
}
