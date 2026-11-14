package com.sumi.domain.insights

import kotlin.math.roundToInt

/**
 * Shared average/percentage math for the whole app.
 *
 * Rules (ARCHITECTURE §13, DATA_MODEL §5 rule 7):
 * - Every average states its window. The window is the caller's responsibility; these
 *   functions only do deterministic integer math. Zero-usage days are included —
 *   a quiet day is still a day (the old engine silently excluded them).
 * - Percentages share one denominator — the bucket's window total tracked usage
 *   (uncategorized included) — and one rounding rule, the shared whole percent of
 *   [percentOf] / [percentOfShare] (Kotlin `roundToInt`, round-half-even at exact .5),
 *   clamped at 0..100.
 */
object Averages {

    /** Average per day over [windowDays]; [windowDays] <= 0 yields 0. */
    fun dailyAverage(totalMs: Long, windowDays: Int): Long =
        if (windowDays <= 0) 0L else totalMs / windowDays

    /** Same, computed from a list of daily totals (window = list size). */
    fun dailyAverage(totals: List<Long>): Long =
        if (totals.isEmpty()) 0L else totals.sum() / totals.size

    /** Whole-percent share of [part] in [total]; 0 when total is 0. */
    fun percentOf(part: Long, total: Long): Int =
        if (total <= 0L) 0 else (part.toFloat() / total.toFloat() * 100f).roundToInt().coerceIn(0, 100)

    /** Whole percent from a 0..1 share fraction — same rule as [percentOf]. */
    fun percentOfShare(share: Float): Int = (share * 100f).roundToInt().coerceIn(0, 100)
}
