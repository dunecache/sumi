package com.sumi.domain.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Calendar-day helpers. All instants are **UTC epoch milliseconds**; the local date is derived
 * with the [ZoneId] supplied by the caller (see [TimeZoneProvider]).
 *
 * Date keys use ISO format `YYYY-MM-DD` and are computed at aggregation time, never persisted as local times.
 */
object DateKey {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** Returns the `YYYY-MM-DD` date key for [epochMs] in [zone]. */
    fun of(epochMs: Long, zone: ZoneId): String = formatter.format(Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate())

    /** Returns the UTC epoch ms of the start of day [dateKey] in [zone]. */
    fun startOfDayUtcMs(dateKey: String, zone: ZoneId): Long {
        val date = java.time.LocalDate.parse(dateKey, formatter)
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Returns the UTC epoch ms of the start of the next day after [dateKey] in [zone]. */
    fun endOfDayUtcMsExclusive(dateKey: String, zone: ZoneId): Long {
        val date = java.time.LocalDate.parse(dateKey, formatter).plusDays(1)
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Shifts [dateKey] by [days] calendar days in [zone] (preserves its value as a date). */
    fun shift(dateKey: String, days: Long): String {
        val date = java.time.LocalDate.parse(dateKey, formatter).plusDays(days)
        return formatter.format(date)
    }

    /**
     * The `YYYY-MM-DD` keys covering the half-open range [fromUtcMs, toUtcMs) in [zone].
     * Empty when the range is empty. Used to derive "affected days" from the window's
     * time span, never from output rows — a correction that zeroes a day must still
     * trigger that day's replacement.
     */
    fun daysBetween(fromUtcMs: Long, toUtcMs: Long, zone: ZoneId): List<String> {
        if (toUtcMs <= fromUtcMs) return emptyList()
        var date = Instant.ofEpochMilli(fromUtcMs).atZone(zone).toLocalDate()
        val last = Instant.ofEpochMilli(toUtcMs - 1).atZone(zone).toLocalDate()
        val out = ArrayList<String>()
        while (!date.isAfter(last)) {
            out += formatter.format(date)
            date = date.plusDays(1)
        }
        return out
    }
}
