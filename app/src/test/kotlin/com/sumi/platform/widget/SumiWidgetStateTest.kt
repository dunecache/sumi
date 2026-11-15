package com.sumi.platform.widget

import com.sumi.core.database.entity.AppCategory
import com.sumi.core.database.entity.Application
import com.sumi.core.datastore.DailyGoalConfig
import com.sumi.domain.usage.DailyAppUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the widget state machine. No Robolectric here: every function exercised
 * below is pure Kotlin by design (docs/WIDGET.md §12-13).
 */
class SumiWidgetStateTest {

    private val goal180 = DailyGoalConfig(goalMinutes = 180, enabled = true)
    private val noGoal = DailyGoalConfig(goalMinutes = null, enabled = true)
    private val pausedGoal = DailyGoalConfig(goalMinutes = 180, enabled = false)

    private fun app(pkg: String, name: String, categoryId: String? = null) =
        Application(packageName = pkg, displayName = name, categoryId = categoryId)

    private fun perApp(vararg pairs: Pair<String, Long>): List<DailyAppUsage> =
        pairs.map { DailyAppUsage("2026-11-15", it.first, it.second, "SESSIONS") }

    private val categories = listOf(
        AppCategory("social", "Social", "matcha-4", sortOrder = 0, isUserCreated = false),
        AppCategory("video", "Video", "ochre", sortOrder = 1, isUserCreated = false),
    )

    // ------------------------------------------------------------------ ranked breakdown

    @Test
    fun `rows are ranked by duration descending and durations reconcile with the bar`() {
        val apps = listOf(
            app("a", "Alpha", "video"),
            app("b", "Beta", "social"),
            app("c", "Gamma", "video"),
            app("d", "Delta"),
        )
        val usage = perApp("a" to 3_000_000L, "b" to 1_500_000L, "c" to 600_000L, "d" to 300_000L)
        val total = 5_400_000L // 100% of the headline; rows cover it
        val breakdown = rankedBreakdown(total, usage, apps, categories, limit = 3)

        assertEquals(listOf("a", "b", "c"), breakdown.rows.map { it.packageName })
        assertEquals(5_400_000L, breakdown.trackedMs)
        // top 3 sum = 5_100_000 → remainder = 300_000
        assertEquals(300_000L, breakdown.remainderMs)
        // bar segments: three apps + one remainder, fractions sum to 1.0
        val fractions = breakdown.segments.map { it.fraction }
        assertEquals(4, breakdown.segments.size)
        assertEquals(1.0, fractions.sum().toDouble(), 0.0001)
        // order is list order, descending by duration
        assertEquals(listOf("a", "b", "c", null), breakdown.segments.map { it.colorToken })
        assertEquals("ochre", breakdown.segments[0].colorToken)
        assertEquals("matcha-4", breakdown.segments[1].colorToken)
    }

    @Test
    fun `tiny segments below 4 percent merge into the remainder`() {
        val apps = listOf(
            app("a", "Alpha", "video"),
            app("b", "Beta", "social"),
            app("c", "Gamma", null),
        )
        val usage = perApp("a" to 9_000_000L, "b" to 900_000L, "c" to 100_000L)
        val total = 10_000_000L
        val breakdown = rankedBreakdown(total, usage, apps, categories, limit = 3)

        // c is 1% of the total → merges; bar = [a, b, remainder]
        assertEquals(3, breakdown.segments.size)
        assertEquals("ochre", breakdown.segments[0].colorToken)
        assertEquals("matcha-4", breakdown.segments[1].colorToken)
        // gamma's merged 1% lands entirely in the stone remainder segment
        val remainder = breakdown.segments.last()
        assertNull(remainder.colorToken)
        assertEquals(0.01f, remainder.fraction, 0.001f)
    }

    @Test
    fun `limit extends to five rows and the remainder covers what is not listed`() {
        val apps = (1..7).map { app("p$it", "App $it") }
        val usage = perApp(*(1..7).map { "p$it" to it * 600_000L }.toTypedArray())
        val total = usage.sumOf { it.durationMs }

        val breakdown3 = rankedBreakdown(total, usage, apps, categories, limit = 3)
        val breakdown5 = rankedBreakdown(total, usage, apps, categories, limit = 5)

        assertEquals(listOf("p7", "p6", "p5"), breakdown3.rows.map { it.packageName })
        assertEquals(listOf("p7", "p6", "p5", "p4", "p3"), breakdown5.rows.map { it.packageName })
        // total = 600_000 × 28 = 16_800_000; listed 3-row tops = 10_800_000 → 6_000_000 unlisted
        assertEquals(6_000_000L, breakdown3.remainderMs)
        // listed 5-row tops = 15_000_000 → p1+p2 = 1_800_000 unlisted
        assertEquals(1_800_000L, breakdown5.remainderMs)
    }

    @Test
    fun `zero total yields no rows and no segments`() {
        val breakdown = rankedBreakdown(0L, emptyList(), emptyList(), emptyList(), limit = 3)
        assertTrue(breakdown.rows.isEmpty())
        assertTrue(breakdown.segments.isEmpty())
        assertEquals(0L, breakdown.remainderMs)
    }

    @Test
    fun `unattributable total paints a full neutral remainder bar`() {
        // Aggregate row exists (total > 0) but no per-app rows resolved (all excluded):
        // the bar must not vanish and must never invent an app.
        val breakdown = rankedBreakdown(1_800_000L, emptyList(), emptyList(), emptyList(), limit = 3)
        assertEquals(1, breakdown.segments.size)
        assertNull(breakdown.segments.single().colorToken)
        assertEquals(1.0f, breakdown.segments.single().fraction, 0.0001f)
        assertEquals(1_800_000L, breakdown.remainderMs)
    }

    @Test
    fun `compact durations render in widget rows`() {
        val apps = listOf(app("a", "Alpha"), app("b", "Beta"), app("c", "Gamma"))
        val usage = perApp("a" to 8_000L, "b" to 2_700_000L, "c" to 7_980_000L)
        val breakdown = rankedBreakdown(10_688_000L, usage, apps, categories, limit = 3)
        val byPkg = breakdown.rows.associate { it.packageName to it.durationText }
        assertEquals("<1m", byPkg["a"])
        assertEquals("45m", byPkg["b"])
        assertEquals("2h 13m", byPkg["c"])
    }

    @Test
    fun `letter mark skips non-letters and uppercases`() {
        val apps = listOf(app("x", "3proxy"), app("y", "zilla"))
        val usage = perApp("x" to 5_000_000L, "y" to 1_000_000L)
        val breakdown = rankedBreakdown(6_000_000L, usage, apps, categories, limit = 2)
        val byPkg = breakdown.rows.associate { it.packageName to it.letterMark }
        assertEquals("P", byPkg["x"])
        assertEquals("Z", byPkg["y"])
    }

    // ------------------------------------------------------------------ totals and states

    @Test
    fun `missing usage access wins over any data state`() {
        val view = resolveWidgetView(totalMs = 90_000L, hasUsageAccess = false, goal = goal180)
        assertSame(WidgetView.PermissionMissing, view)
    }

    @Test
    fun `access granted but no aggregate row yet is pending`() {
        val view = resolveWidgetView(totalMs = null, hasUsageAccess = true, goal = goal180)
        assertSame(WidgetView.Pending, view)
    }

    @Test
    fun `recorded zero is a real zero total`() {
        val view = resolveWidgetView(totalMs = 0L, hasUsageAccess = true, goal = noGoal)
        assertEquals(WidgetView.Total(0L, "0m", null, null), view)
    }

    @Test
    fun `sub-minute total keeps exact semantics`() {
        val view = resolveWidgetView(totalMs = 8000L, hasUsageAccess = true, goal = noGoal)
        assertEquals("Under a minute", (view as WidgetView.Total).totalText)
    }

    @Test
    fun `no goal configured yields no goal line and never invents one`() {
        val view = resolveWidgetView(totalMs = 3_600_000L, hasUsageAccess = true, goal = noGoal)
        assertNull((view as WidgetView.Total).goal)
    }

    @Test
    fun `paused goal behaves as no goal`() {
        val view = resolveWidgetView(totalMs = 3_600_000L, hasUsageAccess = true, goal = pausedGoal)
        assertNull((view as WidgetView.Total).goal)
    }

    @Test
    fun `on track goal reads with intention prefix`() {
        val view = resolveWidgetView(totalMs = 5_400_000L, hasUsageAccess = true, goal = goal180)
        val goal = (view as WidgetView.Total).goal!!
        assertEquals("Daily intention · 1h 30m remaining", goal.line)
        assertEquals(0.5f, goal.fraction, 0.001f)
        assertTrue(goal.a11ySentence.startsWith("1h 30m remaining"))
    }

    @Test
    fun `exceeded goal reads neutral never red`() {
        val view = resolveWidgetView(totalMs = 14_400_000L, hasUsageAccess = true, goal = goal180)
        val goal = (view as WidgetView.Total).goal!!
        assertEquals("Daily intention · 1h 30m over", goal.line)
        assertEquals(1.0f, goal.fraction, 0.001f)
    }

    // ------------------------------------------------------------------ comparison

    @Test
    fun `absent yesterday baseline omits the line`() {
        assertNull(comparisonLine(1_000_000L, null))
    }

    @Test
    fun `deltas under five minutes read as about the same`() {
        val same = comparisonLine(1_800_000L, 1_800_000L + 4 * 60_000L)
        assertEquals("About the same as yesterday at this time.", same)
    }

    @Test
    fun `deltas within ten percent read as about the same even beyond five minutes`() {
        // 30m vs 33m: 3m apart — trivially within both bands; craft a 10% case above five minutes
        val a = comparisonLine(3_600_000L, 3_600_000L + 330_000L) // 5m30s apart, 9% band
        assertEquals("About the same as yesterday at this time.", a)
    }

    @Test
    fun `meaningful deltas quote the shared phrasing`() {
        val less = comparisonLine(1_000_000L, 1_000_000L + 7_200_000L)
        assertEquals("About 2h less than yesterday at this time.", less)
        val more = comparisonLine(7_200_000L, 1_000_000L)
        assertEquals("About 1h 43m more than yesterday at this time.", more)
    }

    // ------------------------------------------------------------------ staleness & a11y

    @Test
    fun `staleness footer shows only beyond one hour`() {
        val hour = 60L * 60L * 1000L
        assertEquals(false, staleFooterText(1_000L, 1_000L + hour - 1))
        assertEquals(true, staleFooterText(1_000L, 1_000L + hour + 1))
        assertEquals(false, staleFooterText(null, 1_000L))
    }

    @Test
    fun `accessibility summary announces action then sentence then top apps`() {
        val apps = listOf(app("a", "Alpha", "video"), app("b", "Beta", "social"))
        val usage = perApp("a" to 4_000_000L, "b" to 1_000_000L)
        val view = resolveWidgetView(5_000_000L, true, noGoal, yesterdayClippedMs = 2_000_000L)
        val rows = rankedBreakdown(5_000_000L, usage, apps, categories, 3).rows
        val summary = accessibilitySummary(view as WidgetView.Total, rows)
        assertTrue(summary.startsWith("Screen time today 1h 23m."))
        assertTrue(summary.contains("About 50m more than yesterday at this time."))
        assertTrue(summary.contains("Top applications: Alpha 1h 6m, Beta 16m."))
    }

    @Test
    fun `zero usage announces zero and pending and permission speak for themselves`() {
        assertEquals(
            "Screen time today 0m. No usage recorded yet.",
            accessibilitySummary(WidgetView.Total(0L, "0m", null, null)),
        )
        assertEquals(
            "Open Sumi — waiting for data. Your screen time will appear here shortly.",
            accessibilitySummary(WidgetView.Pending),
        )
        assertEquals(
            "Open Sumi — usage access needed. Tap to review your permissions.",
            accessibilitySummary(WidgetView.PermissionMissing),
        )
    }
}
