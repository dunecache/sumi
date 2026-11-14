package com.sumi.domain.insights

import com.sumi.domain.usage.HourlyUsage

/**
 * "Typically active between 21:00 and 00:00" — derived from the same hourly buckets
 * that power Today's 24-hour timeline (session-backed days only; the 90-day aggregate
 * import carries no hourly resolution, which keeps this honest rather than precise).
 *
 * Nothing is claimed below [MIN_DAYS] days with usage; callers render an honest
 * "not enough history" line instead of a window.
 */

/** One contiguous active window on the wall clock, hour-of-day boundaries. */
data class ActiveWindow(
    /** First hour of the window, 0–23 (inclusive). */
    val startHour: Int,
    /** End of the window, 1–24 (exclusive); 24 renders as `00:00`. */
    val endHourExclusive: Int,
    /** How many distinct days contributed at least one nonzero bucket. */
    val daysWithUsage: Int,
)

/** Aggregated hourly profile for one application across a range of days. */
data class HourlySummary(
    /** 24 values, index = hour of day, sums across all rows. */
    val perHourMs: List<Long>,
    /** Distinct date keys carrying at least one nonzero bucket. */
    val daysWithUsage: Int,
) {
    companion object {
        /** Sums per-app hourly rows into a 24-value wall-clock profile. */
        fun from(rows: List<HourlyUsage>): HourlySummary {
            val perHour = MutableList(HOURS_PER_DAY) { 0L }
            val days = HashSet<String>()
            for (row in rows) {
                if (row.hour in 0 until HOURS_PER_DAY && row.durationMs > 0L) {
                    perHour[row.hour] = perHour[row.hour] + row.durationMs
                    days += row.dateKey
                }
            }
            return HourlySummary(perHour.toList(), days.size)
        }

        private const val HOURS_PER_DAY = 24
    }
}

object TypicalHours {

    /** Width of the active window in hours. */
    const val WINDOW_HOURS = 3

    /** Minimum distinct days with usage before a window is reported. */
    const val MIN_DAYS = 3

    /**
     * Best [WINDOW_HOURS]-hour contiguous window by total duration.
     *
     * Returns null when there are fewer than [MIN_DAYS] days with usage or no usage at
     * all — the caller shows an honest "not enough history" line. Ties resolve to the
     * earlier window (deterministic).
     */
    fun typicalWindow(summary: HourlySummary): ActiveWindow? {
        if (summary.perHourMs.size != 24) return null
        if (summary.daysWithUsage < MIN_DAYS) return null
        if (summary.perHourMs.sum() <= 0L) return null
        var bestSum = -1L
        var bestStart = 0
        for (start in 0..24 - WINDOW_HOURS) {
            var sum = 0L
            for (offset in 0 until WINDOW_HOURS) sum += summary.perHourMs[start + offset]
            if (sum > bestSum) {
                bestSum = sum
                bestStart = start
            }
        }
        return ActiveWindow(bestStart, bestStart + WINDOW_HOURS, summary.daysWithUsage)
    }
}
