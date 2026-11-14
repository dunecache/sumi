package com.sumi.domain.insights

import com.sumi.domain.common.DateKey
import java.time.Instant
import java.time.ZoneId

/**
 * Canonical analytical ranges for Insights (ARCHITECTURE.md §16).
 *
 * Range rules shared by every caller of this enum:
 * - Windows end today, inclusive, and split days at local midnight via [DateKey].
 * - [TODAY] is *partial* (elapsed time only, up to [nowUtcMs]); every other range is a
 *   sequence of complete calendar days ([YESTERDAY] is a single complete day).
 * - [comparisonKeys] is the previous *equivalent* window:
 *   [TODAY] compares against yesterday clipped to the same local time of day (session
 *   granularity, handled by the caller), [YESTERDAY] against the day before yesterday,
 *   [WEEK]/[MONTH] against the N days immediately before the window.
 * - Comparison is omitted when the previous window holds no recorded activity at all —
 *   missing days inside an otherwise recorded window count as zero.
 */
enum class InsightsRange(val windowDays: Int) {
    TODAY(1),
    YESTERDAY(1),
    WEEK(7),
    MONTH(30);

    /** Date keys for the window, oldest first; today included for [TODAY]/[WEEK]/[MONTH]. */
    fun dateKeys(zone: ZoneId, nowUtcMs: Long): List<String> {
        val today = DateKey.of(nowUtcMs, zone)
        return when (this) {
            TODAY -> listOf(today)
            YESTERDAY -> listOf(DateKey.shift(today, -1L))
            WEEK -> (6 downTo 0).map { DateKey.shift(today, -it.toLong()) }
            MONTH -> (29 downTo 0).map { DateKey.shift(today, -it.toLong()) }
        }
    }

    /** Keys of the previous equivalent window, oldest first. */
    fun comparisonKeys(zone: ZoneId, nowUtcMs: Long): List<String> {
        val today = DateKey.of(nowUtcMs, zone)
        return when (this) {
            TODAY -> listOf(DateKey.shift(today, -1L))
            YESTERDAY -> listOf(DateKey.shift(today, -2L))
            WEEK -> (13 downTo 7).map { DateKey.shift(today, -it.toLong()) }
            MONTH -> (59 downTo 30).map { DateKey.shift(today, -it.toLong()) }
        }
    }

    /** Human-readable window for captions: "Today", "Yesterday", "the last 7 days". */
    val windowLabel: String
        get() = when (this) {
            TODAY -> "Today"
            YESTERDAY -> "Yesterday"
            WEEK -> "the last 7 days"
            MONTH -> "the last 30 days"
        }

    /** Compact switcher label (M3 segmented control). */
    val shortLabel: String
        get() = when (this) {
            TODAY -> "Today"
            YESTERDAY -> "Yesterday"
            WEEK -> "7 days"
            MONTH -> "30 days"
        }

    /** Neutral previous-period label for the comparison line. */
    val comparisonLabel: String
        get() = when (this) {
            TODAY -> "yesterday"
            YESTERDAY -> "the day before yesterday"
            WEEK -> "the previous 7 days"
            MONTH -> "the previous 30 days"
        }

    /** True when the window is made of complete past days ([TODAY] is partial). */
    val isCompleteWindow: Boolean
        get() = this != TODAY

    /**
     * For [TODAY]: the `[from, to)` instant pair covering yesterday from its start up to the
     * same local time of day as `now`. For complete windows this equals the full previous day
     * (or 7×/30× full days) and is only used as an interval bound by callers that clip —
     * comparisons over complete windows rely on daily aggregates instead.
     */
    fun comparisonWindow(zone: ZoneId, nowUtcMs: Long): Pair<Long, Long> {
        val now = Instant.ofEpochMilli(nowUtcMs).atZone(zone)
        val fromKey = comparisonKeys(zone, nowUtcMs).first()
        val from = DateKey.startOfDayUtcMs(fromKey, zone)
        if (this != TODAY) {
            return from to DateKey.endOfDayUtcMsExclusive(comparisonKeys(zone, nowUtcMs).last(), zone)
        }
        val localDate = now.toLocalDate().minusDays(1)
        val sameTimeYesterday = localDate.atTime(now.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
        return from to sameTimeYesterday
    }

    companion object {
        /** Default for a fresh screen: the trailing week. */
        val DEFAULT: InsightsRange = WEEK

        /** Restores a stored name (e.g. SavedStateHandle); unknown/absent values fall back to [DEFAULT]. */
        fun fromStorage(name: String?): InsightsRange =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
