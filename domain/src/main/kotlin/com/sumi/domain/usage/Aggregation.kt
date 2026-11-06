package com.sumi.domain.usage

import com.sumi.domain.common.DateKey
import java.time.Instant
import java.time.ZoneId

/**
 * Aggregation from sessions (ARCHITECTURE.md §4.2 stage 4, DATA_MODEL.md §5).
 * Pure and reproducible: daily/hourly values are re-derivable from sessions.
 * Sessions crossing midnight are stored once; duration is split across date keys (R6).
 * Day/hour boundaries use the [ZoneId] supplied at aggregation time (R7, §6).
 */
object Aggregation {

    /**
     * Per-app per-day totals: sum of session overlap with each day in [zone].
     * Open sessions ([UsageSession.endTimeUtcMs] null) are measured up to [nowUtcMs].
     */
    fun dailyAppUsage(
        sessions: List<UsageSession>,
        zone: ZoneId,
        nowUtcMs: Long,
    ): List<DailyAppUsage> {
        val totals = mutableMapOf<Pair<String, String>, Long>()
        for (session in sessions) {
            val end = session.endTimeUtcMs ?: nowUtcMs
            if (end <= session.startTimeUtcMs) continue
            splitByDay(session.startTimeUtcMs, end, zone).forEach { (dateKey, ms) ->
                val key = dateKey to session.packageName
                totals[key] = (totals[key] ?: 0L) + ms
            }
        }
        return totals.map { ((dateKey, pkg), ms) -> DailyAppUsage(dateKey, pkg, ms) }
            .filter { it.durationMs > 0 }
            .sortedWith(compareBy<DailyAppUsage> { it.dateKey }.thenBy { it.packageName })
    }

    /** Daily totals as union of intervals per day (R8 multi-window safe). */
    fun dailyTotals(
        sessions: List<UsageSession>,
        zone: ZoneId,
        nowUtcMs: Long,
        lastCollectedAtUtcMs: Long = nowUtcMs,
    ): List<DailyUsage> {
        val byDay = mutableMapOf<String, MutableList<LongRange>>()
        for (session in sessions) {
            val end = session.endTimeUtcMs ?: nowUtcMs
            if (end <= session.startTimeUtcMs) continue
            splitRangesByDay(session.startTimeUtcMs, end, zone).forEach { (dateKey, range) ->
                byDay.getOrPut(dateKey) { mutableListOf() } += range
            }
        }
        return byDay.map { (dateKey, ranges) ->
            DailyUsage(
                dateKey = dateKey,
                totalDurationMs = unionMs(ranges),
                lastCollectedAtUtcMs = lastCollectedAtUtcMs,
            )
        }.sortedBy { it.dateKey }
    }

    /** Per-app per-hour buckets. DST days naturally yield 23/25 hours via zone rules. */
    fun hourlyUsage(
        sessions: List<UsageSession>,
        zone: ZoneId,
        nowUtcMs: Long,
    ): List<HourlyUsage> {
        val totals = mutableMapOf<Triple<String, Int, String>, Long>()
        for (session in sessions) {
            val end = session.endTimeUtcMs ?: nowUtcMs
            if (end <= session.startTimeUtcMs) continue
            var cursor = session.startTimeUtcMs
            while (cursor < end) {
                val instant = Instant.ofEpochMilli(cursor).atZone(zone)
                val dateKey = DateKey.of(cursor, zone)
                val hour = instant.hour
                val nextHourMs = instant.let {
                    it.toLocalDate().atTime(it.hour + 1, 0).atZone(zone).toInstant().toEpochMilli()
                }
                // For the 25-hour DST fall-back, atTime(h+1) is unambiguous; for the 23-hour
                // spring-forward gap, the builder skips the missing hour — cursor still advances.
                val segEnd = minOf(end, nextHourMs).coerceAtLeast(cursor + 1)
                val segMs = (segEnd - cursor).coerceAtLeast(0L)
                if (segMs > 0) {
                    val key = Triple(dateKey, hour, session.packageName)
                    totals[key] = (totals[key] ?: 0L) + segMs
                }
                cursor = segEnd
            }
        }
        return totals.map { ((dateKey, hour, pkg), ms) -> HourlyUsage(dateKey, hour, pkg, ms) }
            .filter { it.durationMs > 0 }
            .sortedWith(compareBy<HourlyUsage> { it.dateKey }.thenBy { it.hour }.thenBy { it.packageName })
    }

    private fun splitByDay(startMs: Long, endMs: Long, zone: ZoneId): List<Pair<String, Long>> =
        splitRangesByDay(startMs, endMs, zone).map { (dateKey, range) -> dateKey to (range.last - range.first) }

    private fun splitRangesByDay(startMs: Long, endMs: Long, zone: ZoneId): List<Pair<String, LongRange>> {
        val out = mutableListOf<Pair<String, LongRange>>()
        var cursor = startMs
        while (cursor < endMs) {
            val dateKey = DateKey.of(cursor, zone)
            val nextDayMs = DateKey.endOfDayUtcMsExclusive(dateKey, zone)
            val segEnd = minOf(endMs, nextDayMs).coerceAtLeast(cursor + 1)
            out += dateKey to (cursor..segEnd)
            cursor = segEnd
        }
        return out
    }

    private fun unionMs(ranges: List<LongRange>): Long {
        if (ranges.isEmpty()) return 0L
        val sorted = ranges.sortedBy { it.first }
        var total = 0L
        var curStart = sorted[0].first
        var curEnd = sorted[0].last
        for (range in sorted.drop(1)) {
            if (range.first <= curEnd) {
                curEnd = maxOf(curEnd, range.last)
            } else {
                total += (curEnd - curStart).coerceAtLeast(0L)
                curStart = range.first
                curEnd = range.last
            }
        }
        total += (curEnd - curStart).coerceAtLeast(0L)
        return total
    }
}
