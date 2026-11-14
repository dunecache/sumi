package com.sumi.platform.widget

import com.sumi.core.datastore.DailyGoalConfig
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.domain.goal.DailyGoal
import com.sumi.domain.goal.DailyGoalStatus

/**
 * Pure presentation state for the Sumi home-screen widget (docs/WIDGET.md). No Android or
 * Compose imports on purpose: the whole state machine is unit-tested on the JVM without
 * Robolectric. The Glance layer only renders what this file derives.
 *
 * Product rules (widget design reference §5 and §7):
 * - The daily goal is optional and informational; exceeding it is never alarmist and never red.
 * - Remaining time is clamped at zero; the over-goal line shows the overage in neutral wording.
 * - No goal configured → no progress bar, no invented target.
 * - No Usage Access → permission explanation, never a stale total.
 * - Access granted but no aggregate row yet → honest pending placeholder.
 * - Recorded zero stays a zero state ("0m"), distinct from sub-minute usage and from pending.
 */
internal sealed interface WidgetView {
    /** Tracked time exists (possibly zero) with or without a configured goal. */
    data class Total(val totalMs: Long, val goal: GoalProgressView?) : WidgetView

    /** Access granted, no aggregate row yet (first launch / import pending). */
    data object Pending : WidgetView

    /** Usage Access missing; the app is the fix. Never shows an old total. */
    data object PermissionMissing : WidgetView
}

/** Derived goal presentation. All strings are final; the renderer lays them out. */
internal data class GoalProgressView(
    /** Whole percent of the goal used; unclamped — "120% of daily goal" stays honest. */
    val percentText: String,
    /** Short form for narrow rows ("50% of goal"); pairs with the status line on one line. */
    val percentShort: String,
    /** Neutral status line: remaining / met / over. Never negative, never punitive. */
    val statusLine: String,
    /** Progress for the bar, clamped to [0f, 1f] for drawing. */
    val fraction: Float,
    /** Accessibility-friendly sentence with the same values. */
    val a11yText: String,
)

/** Widget breakpoint, picked from the size the launcher actually allotted. */
internal enum class WidgetBreakpoint { SLIM, COMPACT, MEDIUM, LARGE }

internal fun pickBreakpoint(widthDp: Int, heightDp: Int): WidgetBreakpoint = when {
    heightDp < 80 -> WidgetBreakpoint.SLIM // one-row tile (any width)
    widthDp < 210 -> WidgetBreakpoint.COMPACT
    heightDp >= 140 -> WidgetBreakpoint.LARGE
    else -> WidgetBreakpoint.MEDIUM
}

/**
 * @param totalMs today's total, or null when no aggregate row exists yet.
 * @param hasUsageAccess see UsageAccessChecker.
 */
internal fun resolveWidgetView(
    totalMs: Long?,
    hasUsageAccess: Boolean,
    goal: DailyGoalConfig,
): WidgetView {
    if (!hasUsageAccess) return WidgetView.PermissionMissing
    val total = totalMs ?: return WidgetView.Pending
    return WidgetView.Total(total, goalView(total, goal))
}

private fun goalView(totalMs: Long, goal: DailyGoalConfig): GoalProgressView? {
    val goalMs = goal.activeGoalMinutes?.times(MILLIS_PER_MINUTE) ?: return null
    val progress = DailyGoal.evaluate(totalMs, goalMs)
    val percent = (progress.fraction * 100f).toInt()
    val statusLine = when (progress.status) {
        DailyGoalStatus.ON_TRACK -> "${formatDuration(progress.remainingMs)} remaining"
        DailyGoalStatus.REACHED -> "Goal met today"
        DailyGoalStatus.EXCEEDED ->
            "${formatDuration(progress.overMs)} over your ${formatDuration(progress.goalMs)} goal"
    }
    val fraction = progress.fraction.coerceIn(0f, 1f)
    return GoalProgressView(
        percentText = "$percent% of daily goal",
        percentShort = "$percent% of goal",
        statusLine = statusLine,
        fraction = fraction,
        a11yText = "$percent percent of daily goal, $statusLine",
    )
}

/** Sumi wordmark used at the top of every state. */
internal const val WORD_MARK = "SUMI"

/** Calm line shown when no goal is configured (we never invent a target). */
internal const val NO_GOAL_LINE = "Usage today"

/** Calm empty state; the date and tap-through stay obvious. */
internal const val ZERO_USAGE_LINE = "No usage recorded yet"

/** Permission-missing copy; direct and actionable, no stale numbers. */
internal const val PERMISSION_TITLE = "Sumi needs Usage Access to show today."
internal const val PERMISSION_ACTION = "Open Sumi to enable it in Settings"
internal const val PERMISSION_SHORT = "Sumi needs Usage Access"

/** Data-pending placeholder; honest about the first sync. */
internal const val PENDING_PLACEHOLDER = "—"
internal const val PENDING_LINE = "Waiting for today's first sync"
internal const val PENDING_SHORT = "Waiting for first sync"

/** Tap route label for accessibility services (design reference §9). */
internal const val OPEN_SUMI_LABEL = "Open Sumi — today's screen time"

/** Full announcement for the tappable surface: action first, then the values. */
internal fun accessibilitySummary(view: WidgetView): String = when (view) {
    is WidgetView.Total -> {
        val base = "Open Sumi — today's screen time. ${formatDuration(view.totalMs)}."
        when {
            view.totalMs == 0L -> "$base $ZERO_USAGE_LINE."
            view.goal != null -> "$base ${view.goal.a11yText}."
            else -> "$base $NO_GOAL_LINE."
        }
    }
    WidgetView.Pending -> "$OPEN_SUMI_LABEL. $PENDING_LINE."
    WidgetView.PermissionMissing -> "Open Sumi — restore Usage Access."
}

private const val MILLIS_PER_MINUTE = 60_000L
