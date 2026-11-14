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
     * Per-app per-day totals: union of session intervals per (day, package).
     * Open sessions ([UsageSession.endTimeUtcMs] null) are measured up to [nowUtcMs].
     * Overlapping rows for the same package (e.g. fragments re-reconstructed across
     * windows) never inflate the app's total or the ranked lists derived from it.
     */
    fun dailyAppUsage(
        sessions: List<UsageSession>,
        zone: ZoneId,
        nowUtcMs: Long,
    ): List<DailyAppUsage> {
        val ranges = mutableMapOf<Pair<String, String>, MutableList<LongRange>>()
        for (session in sessions) {
            val end = session.endTimeUtcMs ?: nowUtcMs
            if (end <= session.startTimeUtcMs) continue
            splitRangesByDay(session.startTimeUtcMs, end, zone).forEach { (dateKey, range) ->
                ranges.getOrPut(dateKey to session.packageName) { mutableListOf() } += range
            }
        }
        return ranges.map { (key, dayRanges) ->
            DailyAppUsage(key.first, key.second, unionMs(dayRanges))
        }
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

    /**
     * Per-app per-hour buckets. DST semantics: on a fall-back day the local hour 01:00
     * occurs twice; both passes fold into one bucket, which can therefore hold up to
     * 120 minutes. On a spring-forward day, hour 02:00 has no bucket. 24 keys, DST-safe
     * iteration via zone rules; consumers scale buckets by value (never assume 60 min).
     */
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
                val nextHourMs = instant.truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                    .plusHours(1).toInstant().toEpochMilli()
                // truncatedTo + plusHours is safe at 23:00 and across DST boundaries:
                // fall-back yields a 25-hour day, spring-forward a 23-hour day. On a
                // fall-back day the duplicated local hour 01:00 folds into one bucket
                // (it can hold up to 120 minutes); on spring-forward hour 02:00 gets no
                // bucket. Keyed (dateKey, hour 0–23, package), same as before.
                val segEnd = minOf(end, nextHourMs).coerceAtLeast(cursor + 1)
                val segMs = (segEnd - cursor).coerceAtLeast(0L)
                if (segMs > 0) {
                    val key = Triple(dateKey, hour, session.packageName)
                    totals[key] = (totals[key] ?: 0L) + segMs
                }
                cursor = segEnd
            }
        }
        return totals.map { entry ->
            val (dateKey, hour, pkg) = entry.key
            HourlyUsage(dateKey, hour, pkg, entry.value)
        }
            .filter { it.durationMs > 0 }
            .sortedWith(compareBy<HourlyUsage> { it.dateKey }.thenBy { it.hour }.thenBy { it.packageName })
    }

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
