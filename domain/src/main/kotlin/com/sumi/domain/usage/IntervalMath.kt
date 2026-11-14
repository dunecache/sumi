package com.sumi.domain.usage

/**
 * Interval math for windows that are shorter than a day (time-of-day comparisons).
 * Daily aggregates can't answer "yesterday up to 10:00", so comparisons clip sessions in
 * UTC and merge overlapping intervals the same way daily totals do (R8: union, never a
 * raw sum, so split-screen apps never inflate the total).
 */
object IntervalMath {

    /**
     * Union of the session intervals intersected with the half-open window
     * `[fromUtcMs, toUtcMs)`. Open sessions ([UsageSession.endTimeUtcMs] == null) are
     * measured up to [nowUtcMs]. Returns milliseconds; never negative.
     */
    fun unionMsInWindow(
        sessions: List<UsageSession>,
        fromUtcMs: Long,
        toUtcMs: Long,
        nowUtcMs: Long,
    ): Long {
        if (toUtcMs <= fromUtcMs) return 0L
        val clipped = sessions.mapNotNull { session ->
            val start = maxOf(session.startTimeUtcMs, fromUtcMs)
            val end = minOf(session.endTimeUtcMs ?: nowUtcMs, toUtcMs)
            if (end > start) start to end else null
        }.sortedBy { it.first }

        var total = 0L
        var mergedStart = 0L
        var mergedEnd = 0L
        var open = false
        for ((start, end) in clipped) {
            if (!open) {
                mergedStart = start
                mergedEnd = end
                open = true
            } else if (start <= mergedEnd) {
                mergedEnd = maxOf(mergedEnd, end)
            } else {
                total += mergedEnd - mergedStart
                mergedStart = start
                mergedEnd = end
            }
        }
        if (open) total += mergedEnd - mergedStart
        return total.coerceAtLeast(0L)
    }
}
