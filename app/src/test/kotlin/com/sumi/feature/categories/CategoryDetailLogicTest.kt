package com.sumi.feature.categories

import com.sumi.core.database.entity.AppCategory
import com.sumi.core.database.entity.Application
import com.sumi.domain.usage.DailyAppUsage
import com.sumi.domain.usage.DailyAppUsageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Derivation tests for the category detail screen — in particular the reassignment
 * policy: the week series and the share denominator use the CURRENT assignment, while
 * raw per-app records are never rewritten.
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
    private val weekKeys = listOf(
        "2026-11-07",
        "2026-11-08",
        "2026-11-09",
        "2026-11-10",
        "2026-11-11",
        "2026-11-12",
        today,
    )

    @Test
    fun `unknown category id yields the removed state`() {
        val result = deriveState("missing", weekKeys, categories, emptyList(), emptyList(), emptyList())
        assertFalse(result.exists)
    }

    @Test
    fun `unknown category with existing categories still reports removed`() {
        val result = deriveState(
            "missing",
            weekKeys,
            categories,
            listOf(app("com.a", "A", "social")),
            emptyList(),
            emptyList(),
        )
        assertFalse(result.exists)
    }

    @Test
    fun `today and share reflect the current assignment and tracked total`() {
        val apps = listOf(
            app("com.instagram.android", "Instagram", "social"),
            app("com.duolingo", "Duolingo", "education"),
        )
        val todayUsage = listOf(
            usage(today, "com.instagram.android", 3_600_000L),
            usage(today, "com.duolingo", 1_000_000L),
            usage(today, "com.unknown", 400_000L),
        )
        val result = deriveState("social", weekKeys, categories, apps, emptyList(), todayUsage)
        assertTrue(result.exists)
        assertEquals(3_600_000L, result.todayMs)
        // Tracked total = 3.6 + 1.0 + 0.4 = 5.0h → share 72%.
        assertEquals(0.72f, result.todayShare, 1e-6f)
        assertEquals(1, result.apps.size)
    }

    @Test
    fun `week series applies the current assignment - reassigned apps leave the old category`() {
        val apps = listOf(app("com.instagram.android", "Instagram", "entertainment")) // was social, now entertainment
        val weekUsage = listOf(usage(today, "com.instagram.android", 2_000_000L))
        val result = deriveState("social", weekKeys, categories, apps, weekUsage, emptyList())
        // Instagram is no longer in social → social's week stays zero.
        assertEquals(List(7) { 0L }, result.week)
        assertEquals(0L, result.weekTotalMs)
    }

    @Test
    fun `uncategorized apps never leak into a category bucket`() {
        val apps = listOf(app("com.instagram.android", "Instagram", null))
        val weekUsage = listOf(usage(today, "com.instagram.android", 2_000_000L))
        val result = deriveState("social", weekKeys, categories, apps, weekUsage, weekUsage)
        assertEquals(List(7) { 0L }, result.week)
        assertEquals(0L, result.todayMs)
    }

    @Test
    fun `apps rank by today's usage with an alphabetical tiebreak`() {
        val apps = listOf(
            app("com.b", "Beta", "social"),
            app("com.a", "Alpha", "social"),
            app("com.c", "Gamma", "social"),
        )
        val todayUsage = listOf(
            usage(today, "com.b", 1_000L),
            usage(today, "com.a", 1_000L),
            usage(today, "com.c", 5_000L),
        )
        val result = deriveState("social", weekKeys, categories, apps, emptyList(), todayUsage)
        assertEquals(listOf("Gamma", "Alpha", "Beta"), result.apps.map { it.displayName })
    }
}
