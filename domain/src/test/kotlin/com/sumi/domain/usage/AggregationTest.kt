package com.sumi.domain.usage

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import org.junit.Test

class AggregationTest {

    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun `dailyAppUsage splits midnight crossing`() {
        // 2026-11-05T22:30Z -> 23:30Z Berlin same day; 23:30Z -> 00:30 next day.
        val start = java.time.Instant.parse("2026-11-05T22:45:00Z").toEpochMilli()
        val end = java.time.Instant.parse("2026-11-06T00:15:00Z").toEpochMilli()
        val sessions = listOf(UsageSession("com.a", start, end, isEstimated = false))
        val daily = Aggregation.dailyAppUsage(sessions, zone, nowUtcMs = end + 1000L)
        // Berlin: 23:45->23:59 (15m) on 11-05, 00:00->01:15 (75m) on 11-06.
        val byDay = daily.associateBy { it.dateKey }
        assertThat(byDay).containsKey("2026-11-05")
        assertThat(byDay).containsKey("2026-11-06")
        assertThat(byDay.getValue("2026-11-05").durationMs).isEqualTo(15 * 60_000L)
        assertThat(byDay.getValue("2026-11-06").durationMs).isEqualTo(75 * 60_000L)
    }

    @Test
    fun `dailyTotals union avoids double count in split-screen`() {
        val start = java.time.Instant.parse("2026-11-06T08:00:00Z").toEpochMilli()
        val sessions = listOf(
            UsageSession("com.a", start, start + 60 * 60_000L, isEstimated = false),
            UsageSession("com.b", start + 30 * 60_000L, start + 90 * 60_000L, isEstimated = false),
        )
        val totals = Aggregation.dailyTotals(sessions, zone, nowUtcMs = start + 200 * 60_000L)
        // Union is 90 minutes, not 120.
        assertThat(totals[0].totalDurationMs).isEqualTo(90 * 60_000L)
    }

    @Test
    fun `hourly buckets follow zone`() {
        val start = java.time.Instant.parse("2026-11-06T08:15:00Z").toEpochMilli()
        val end = start + 90 * 60_000L
        val sessions = listOf(UsageSession("com.a", start, end, isEstimated = false))
        val hourly = Aggregation.hourlyUsage(sessions, zone, nowUtcMs = end + 1000L)
        assertThat(hourly.map { it.hour }.toSet().size).isAtLeast(1)
        val sum = hourly.sumOf { it.durationMs }
        assertThat(sum).isEqualTo(90 * 60_000L)
    }

    @Test
    fun `rebuild equals stored aggregates`() {
        val start = java.time.Instant.parse("2026-11-06T08:00:00Z").toEpochMilli()
        val sessions = listOf(
            UsageSession("com.a", start, start + 20 * 60_000L, isEstimated = false),
            UsageSession("com.a", start + 30 * 60_000L, start + 40 * 60_000L, isEstimated = false),
        )
        val now = start + 100 * 60_000L
        val first = Aggregation.dailyAppUsage(sessions, zone, now)
        val second = Aggregation.dailyAppUsage(sessions, zone, now)
        assertThat(second).isEqualTo(first)
    }
}
