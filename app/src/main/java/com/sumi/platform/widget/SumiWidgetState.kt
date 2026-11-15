package com.sumi.platform.widget

import com.sumi.core.database.entity.AppCategory
import com.sumi.core.database.entity.Application
import com.sumi.core.datastore.DailyGoalConfig
import com.sumi.core.designsystem.component.formatCompactDuration
import com.sumi.core.designsystem.component.formatDuration
import com.sumi.core.usage.topApps
import com.sumi.domain.goal.DailyGoal
import com.sumi.domain.goal.DailyGoalStatus
import com.sumi.domain.insights.NeutralComparison
import com.sumi.domain.usage.DailyAppUsage

/**
 * Pure presentation state for the Sumi home-screen widget pair (docs/WIDGET.md v3 —
 * "Screen time" compact and expanded). No Android or Compose imports on purpose: the
 * whole state machine is unit-tested on the JVM without Robolectric. The Glance layer
 * ([SumiWidget.kt]) only lays out what this file derives; bitmaps are drawn by
 * [WidgetBrushes.kt] from these pure values.
 *
 * Product rules (widget reference §3, §4, §6):
 * - The total matches the pipeline row exactly; never fabricated, never zero-filled.
 * - Comparison uses a same-wall-clock-time clipped window for yesterday; the shared
 *   [NeutralComparison] rule phrases it (5-minute floor plus the widget's opt-in 10% band).
 * - The bar and the per-app rows both derive from the tracked per-app denominator the
 *   same way Today does; the bar additionally always sums to the headline total by
 *   construction (the remainder segment absorbs everything not listed).
 * - Rows are ranked top apps with exact durations; tiny slices merge into the remainder
 *   instead of vanishing; the remainder covers "the rest of the total".
 * - No Usage Access → explicit permission state; pending first sync → pending state;
 *   recorded zero → "0m". Nothing is ever shown as fresh when it is not.
 * - The daily goal line uses the same goal state as Today; exceeding it is never red.
 */

// ---------------------------------------------------------------------------
// Ranking and bar derivation (shared by both widget versions)
// ---------------------------------------------------------------------------

/** One ranked app row. Display-ready values only; colors resolve at the render layer. */
internal data class RankedRowView(
    val packageName: String,
    val displayName: String,
    val durationMs: Long,
    val durationText: String,
    /** Fraction of the headline total (also the bar's denominator), in (0, 1]. */
    val fraction: Float,
    /** Storage-only token of the app's category (user recolors honored); null = uncategorized. */
    val categoryColorToken: String?,
    /** Upper-cased initial for the monogram fallback when no icon resolves. */
    val letterMark: String,
)

/** One ordered bar segment. `colorToken == null` marks the neutral remainder segment. */
internal data class BarSegment(
    val fraction: Float,
    val colorToken: String?,
)

/** The reconciled content block shared by compact and expanded rendering. */
internal data class AppBreakdown(
    /** Σ per-app usage — the shared denominator with the Today screen. */
    val trackedMs: Long,
    val rows: List<RankedRowView>,
    /** Headline total minus the sum of the visible rows; never negative. */
    val remainderMs: Long,
    /** Segments in list order, tiny ones already merged into the remainder (≥ 4%). */
    val segments: List<BarSegment>,
)

/** Minimum visible segment share; below this a slice merges into the remainder. */
internal const val MIN_SEGMENT_FRACTION = 0.04f

internal fun rankedBreakdown(
    totalMs: Long,
    perApp: List<DailyAppUsage>,
    applications: List<Application>,
    categories: List<AppCategory>,
    limit: Int,
): AppBreakdown {
    val tracked = perApp.sumOf { it.durationMs }
    if (totalMs <= 0L) {
        return AppBreakdown(0L, emptyList(), 0L, emptyList())
    }
    if (tracked <= 0L) {
        // Genuine but unattributable time (aggregates with no per-app rows): the bar is
        // entirely the neutral remainder — never empty, never fabricated.
        return AppBreakdown(0L, emptyList(), totalMs, listOf(BarSegment(1f, null)))
    }
    val appByPackage = applications.associateBy { it.packageName }
    val categoryById = categories.associateBy { it.id }
    val rows = topApps(perApp, applications, limit = limit).map { app ->
        val categoryId = appByPackage[app.packageName]?.categoryId
        val token = categoryId?.let { categoryById[it]?.colorToken }
        RankedRowView(
            packageName = app.packageName,
            displayName = app.displayName,
            durationMs = app.durationMs,
            durationText = formatCompactDuration(app.durationMs),
            fraction = app.durationMs.toFloat() / totalMs.toFloat(),
            categoryColorToken = token,
            letterMark = letterMark(app.displayName),
        )
    }
    val rowsSum = rows.sumOf { it.durationMs }
    val remainderMs = (totalMs - rowsSum).coerceAtLeast(0L)
    // Tiny slices merge into the remainder instead of vanishing: the bar always
    // accounts for every millisecond of the visible total.
    var tinyShare = 0f
    val segments = rows.mapNotNull { row ->
        if (row.fraction < MIN_SEGMENT_FRACTION) {
            tinyShare += row.fraction
            null
        } else {
            BarSegment(row.fraction, row.categoryColorToken)
        }
    } + listOfNotNull(
        // Remainder merges both the unlisted remainder and the merged tiny slices;
        // it always exists so the bar sums to the headline total.
        ((remainderMs.toFloat() / totalMs.toFloat()) + tinyShare)
            .takeIf { it > 0f }
            ?.let { BarSegment(it, null) },
    )
    return AppBreakdown(tracked, rows, remainderMs, segments)
}

private fun letterMark(displayName: String): String {
    displayName.firstOrNull { it.isLetter() }?.uppercaseChar()?.let { return it.toString() }
    displayName.firstOrNull { it.isDigit() }?.let { return it.toString() }
    return "?"
}

/**
 * "About 2h 56m less than yesterday at this time." via the shared neutral comparison
 * rule — five-minute floor plus the widget's opt-in ten-percent band (§4). Null when
 * yesterday holds no recorded sessions (line omitted, never quoted against an
 * unclippable window).
 */
internal fun comparisonLine(todayMs: Long, yesterdayClippedMs: Long?): String? {
    if (yesterdayClippedMs == null) return null
    return NeutralComparison.neutralComparison(
        todayMs,
        yesterdayClippedMs,
        "yesterday at this time",
        similarWithinPercent = 0.10f,
    )
}

// ---------------------------------------------------------------------------
// Goal line (widget-specific copy; same underlying goal state as Today)
// ---------------------------------------------------------------------------

/** The optional "Daily intention" line for the expanded layout. Neutral wording always. */
internal data class WidgetGoalLine(
    val line: String,
    val fraction: Float,
    val a11ySentence: String,
)

internal fun widgetGoalLine(config: DailyGoalConfig, usedMs: Long): WidgetGoalLine? {
    val goalMs = config.activeGoalMinutes?.times(MILLIS_PER_MINUTE) ?: return null
    val progress = DailyGoal.evaluate(usedMs, goalMs)
    val line = when (progress.status) {
        DailyGoalStatus.ON_TRACK -> "Daily intention · ${formatDuration(progress.remainingMs)} remaining"
        DailyGoalStatus.REACHED -> "Daily intention · goal met today"
        DailyGoalStatus.EXCEEDED -> "Daily intention · ${formatDuration(progress.overMs)} over"
    }
    return WidgetGoalLine(
        line = line,
        fraction = progress.fraction.coerceIn(0f, 1f),
        a11ySentence = line.removePrefix("Daily intention · ") + ".",
    )
}

// ---------------------------------------------------------------------------
// Widget states
// ---------------------------------------------------------------------------

internal sealed interface WidgetView {
    /** Tracked time exists (possibly zero). Everything reconciles to [totalMs]. */
    data class Total(
        val totalMs: Long,
        val totalText: String,
        val comparison: String?,
        val goal: WidgetGoalLine?,
    ) : WidgetView

    /** Access granted, no aggregate row yet (first launch / import pending). */
    data object Pending : WidgetView

    /** Usage Access missing; the app is the fix. Never shows an old total. */
    data object PermissionMissing : WidgetView
}

/**
 * @param totalMs today's total, or null when no aggregate row exists yet.
 * @param hasUsageAccess see UsageAccessChecker.
 * @param yesterdayClippedMs yesterday clipped to the same wall-clock time as now (sessions
 * only), or null when yesterday holds no recorded sessions.
 */
internal fun resolveWidgetView(
    totalMs: Long?,
    hasUsageAccess: Boolean,
    goal: DailyGoalConfig,
    yesterdayClippedMs: Long? = null,
): WidgetView {
    if (!hasUsageAccess) return WidgetView.PermissionMissing
    val total = totalMs ?: return WidgetView.Pending
    return WidgetView.Total(
        totalMs = total,
        totalText = formatDuration(total),
        comparison = comparisonLine(total, yesterdayClippedMs),
        goal = widgetGoalLine(goal, total),
    )
}

/**
 * Staleness footer visibility: only when honesty demands it. Older than one hour
 * since the last committed collection pass shows the time; otherwise nothing.
 */
internal fun staleFooterText(lastCollectionUtcMs: Long?, nowUtcMs: Long): Boolean =
    (lastCollectionUtcMs ?: 0L) <= nowUtcMs - STALE_AFTER_MS

// ---------------------------------------------------------------------------
// Accessibility
// ---------------------------------------------------------------------------

/** One-sentence announcement for the tappable surface; action first, values verbatim. */
internal fun accessibilitySummary(
    view: WidgetView,
    rows: List<RankedRowView>? = null,
): String = when (view) {
    is WidgetView.Total -> buildString {
        append("Screen time today ${view.totalText}.")
        view.comparison?.let { append(" $it") }
        if (view.totalMs == 0L) {
            append(" $ZERO_USAGE_LINE_A11Y")
        } else {
            rows?.takeIf { it.isNotEmpty() }?.let { ranked ->
                append(" Top applications: ")
                append(ranked.joinToString(", ") { "${it.displayName} ${it.durationText}" })
                append(".")
            }
        }
        view.goal?.let { append(" ${it.a11ySentence}") }
    }
    WidgetView.Pending -> "Open Sumi — waiting for data. Your screen time will appear here shortly."
    WidgetView.PermissionMissing -> "Open Sumi — usage access needed. Tap to review your permissions."
}

/** Permission-state copy (widget reference §9). */
internal const val PERMISSION_TITLE = "Usage access needed"
internal const val PERMISSION_SUBTITLE = "Tap to review your permissions."

/** Pending-state copy (widget reference §9). */
internal const val PENDING_TITLE = "Waiting for data"
internal const val PENDING_SUBTITLE = "Your screen time will appear here shortly."

/** Zero-usage support copy. */
private const val ZERO_USAGE_LINE_A11Y = "No usage recorded yet."

/** Eyebrow label shown only on the expanded widget. */
internal const val EYEBROW_TITLE = "Screen time"


private const val MILLIS_PER_MINUTE = 60_000L
private const val STALE_AFTER_MS = 60L * 60L * 1000L
