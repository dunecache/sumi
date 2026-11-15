package com.sumi.domain.insights

import kotlin.math.abs

/**
 * Neutral previous-period comparison wording. One shared rule so every surface — Today,
 * Insights, category detail, app detail — phrases deltas identically. No arrows, no hue,
 * no value judgements (DESIGN.md §5); deltas under five minutes read as unchanged.
 */
object NeutralComparison {

    private const val SIMILAR_THRESHOLD_MINUTES = 5L
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_HOUR = 60

    /**
     * "About the same as [previousLabel]." / "About 20m less than [previousLabel]." /
     * "About 2h 15m more than [previousLabel]."
     *
     * @param similarWithinPercent when set (e.g. `0.10f` for 10%), deltas within that fraction
     * of the larger side also read as "About the same", in addition to the five-minute floor.
     * Defaults to null — in-app surfaces are unaffected; the home-screen widget opts in.
     */
    fun neutralComparison(
        currentMs: Long,
        previousMs: Long,
        previousLabel: String,
        similarWithinPercent: Float? = null,
    ): String {
        val delta = currentMs - previousMs
        val absMinutes = abs(delta) / MILLIS_PER_MINUTE
        if (absMinutes < SIMILAR_THRESHOLD_MINUTES) return "About the same as $previousLabel."
        if (similarWithinPercent != null) {
            val larger = maxOf(currentMs, previousMs).toDouble().coerceAtLeast(1.0)
            if (abs(delta) / larger <= similarWithinPercent) {
                return "About the same as $previousLabel."
            }
        }
        val amount = formatDuration(abs(delta))
        return if (delta < 0) {
            "About $amount less than $previousLabel."
        } else {
            "About $amount more than $previousLabel."
        }
    }

    /**
     * Duration formatting: `< 1min` → `0m`-vs-`Under a minute` distinction preserved;
     * whole hours drop the zero minutes (`5h`, never `5h 0m`); never decimal or seconds.
     */
    fun formatDuration(durationMs: Long): String {
        val totalMinutes = (durationMs / MILLIS_PER_MINUTE).coerceAtLeast(0L)
        if (totalMinutes == 0L) return if (durationMs > 0L) "Under a minute" else "0m"
        val hours = totalMinutes / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR
        return when {
            hours == 0L -> "${minutes}m"
            minutes == 0L -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }
}
