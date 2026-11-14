package com.sumi.platform.widget

import com.sumi.core.datastore.DailyGoalConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the widget state machine (docs/WIDGET.md §5 and §7). No Robolectric here:
 * [resolveWidgetView] and [pickBreakpoint] are pure Kotlin by design.
 */
class SumiWidgetStateTest {

    private val goal180 = DailyGoalConfig(goalMinutes = 180, enabled = true)
    private val noGoal = DailyGoalConfig(goalMinutes = null, enabled = true)
    private val pausedGoal = DailyGoalConfig(goalMinutes = 180, enabled = false)

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
    fun `recorded zero is a real zero total, not pending`() {
        val view = resolveWidgetView(totalMs = 0L, hasUsageAccess = true, goal = noGoal)
        assertEquals(WidgetView.Total(0L, null), view)
    }

    @Test
    fun `no goal configured yields no progress view and never invents one`() {
        val view = resolveWidgetView(totalMs = 3_600_000L, hasUsageAccess = true, goal = noGoal)
        assertEquals(WidgetView.Total(3_600_000L, null), view)
    }

    @Test
    fun `paused goal behaves as no goal`() {
        val view = resolveWidgetView(totalMs = 3_600_000L, hasUsageAccess = true, goal = pausedGoal)
        assertNull((view as WidgetView.Total).goal)
    }

    @Test
    fun `under the goal shows remaining and whole percents`() {
        // 1h 30m of a 3h goal -> 50%, 1h 30m remaining.
        val view = resolveWidgetView(totalMs = 5_400_000L, hasUsageAccess = true, goal = goal180)
        val goal = (view as WidgetView.Total).goal!!
        assertEquals("50% of daily goal", goal.percentText)
        assertEquals("50% of goal", goal.percentShort)
        assertEquals("1h 30m remaining", goal.statusLine)
        assertEquals(0.5f, goal.fraction, 0.001f)
    }

    @Test
    fun `reaching the goal is neutral completion, remaining clamps to zero`() {
        val view = resolveWidgetView(totalMs = 10_800_000L, hasUsageAccess = true, goal = goal180)
        val goal = (view as WidgetView.Total).goal!!
        assertEquals("100% of daily goal", goal.percentText)
        assertEquals("Goal met today", goal.statusLine)
        assertEquals(1f, goal.fraction, 0.001f)
    }

    @Test
    fun `exceeding the goal shows the overage in neutral language, fraction clamps for drawing`() {
        // 3h 45m of a 3h goal -> 125%, 45m over your 3h goal, never negative remaining.
        val view = resolveWidgetView(totalMs = 13_500_000L, hasUsageAccess = true, goal = goal180)
        val goal = (view as WidgetView.Total).goal!!
        assertEquals("125% of daily goal", goal.percentText)
        assertEquals("45m over your 3h goal", goal.statusLine)
        assertEquals(1f, goal.fraction, 0.001f)
    }

    @Test
    fun `breakpoint thresholds match the declared launcher breakpoints`() {
        assertEquals(WidgetBreakpoint.COMPACT, pickBreakpoint(140, 110))
        assertEquals(WidgetBreakpoint.COMPACT, pickBreakpoint(160, 150)) // narrow is compact
        assertEquals(WidgetBreakpoint.MEDIUM, pickBreakpoint(210, 110))
        assertEquals(WidgetBreakpoint.MEDIUM, pickBreakpoint(330, 110)) // short & wide stays medium
        assertEquals(WidgetBreakpoint.LARGE, pickBreakpoint(330, 148))
        assertEquals(WidgetBreakpoint.LARGE, pickBreakpoint(250, 200))
    }

    @Test
    fun `accessibility summary opens Sumi and carries the values`() {
        val total = resolveWidgetView(5_400_000L, hasUsageAccess = true, goal = goal180)
        val summary = accessibilitySummary(total)
        assertTrue(summary.startsWith("Open Sumi — today's screen time."))
        assertTrue(summary.contains("1h 30m"))
        assertTrue(summary.contains("50 percent of daily goal"))
        assertTrue(summary.contains("1h 30m remaining"))
    }

    @Test
    fun `accessibility summary for zero usage says so plainly`() {
        val total = resolveWidgetView(0L, hasUsageAccess = true, goal = noGoal)
        assertTrue(accessibilitySummary(total).contains("No usage recorded yet."))
    }

    @Test
    fun `accessibility summaries for pending and missing permission stay actionable`() {
        assertTrue(accessibilitySummary(WidgetView.Pending).contains("Open Sumi"))
        assertTrue(accessibilitySummary(WidgetView.PermissionMissing).contains("restore Usage Access"))
    }
}
