package com.sumi.domain.focus

import java.time.ZoneId
import java.time.Instant

/**
 * Weekly "time in focus" roll-up for the Focus screen (§16.8). Uses the same week
 * boundaries as Insights (trailing 7 local calendar days, [ZoneId] honored).
 *
 * A session contributes its *actual elapsed* time if it ended within the window;
 * sessions started before the window but ended inside it count fully (their time was
 * lived on those days). This matches how daily aggregation attributes midnight
 * crossings to both days.
 */
object FocusHistory {

    data class FocusWindowRow(val startedAtUtcMs: Long, val endedAtUtcMs: Long)

    /** Sum of the actual session lengths whose end time falls in the trailing [days]. */
    fun weeklyElapsedMs(
        sessions: List<FocusWindowRow>,
        windowStartUtcMs: Long,
    ): Long = sessions.asSequence()
        .filter { it.endedAtUtcMs >= windowStartUtcMs }
        .sumOf { (it.endedAtUtcMs - it.startedAtUtcMs).coerceAtLeast(0L) }

    /** Start-of-day UTC of the local date [days - 1] days before [nowUtcMs] in [zone]. */
    fun windowStartUtcMs(nowUtcMs: Long, zone: ZoneId, days: Int = 7): Long =
        java.time.Instant.ofEpochMilli(nowUtcMs)
            .atZone(zone)
            .toLocalDate()
            .minusDays((days - 1).toLong())
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
}
