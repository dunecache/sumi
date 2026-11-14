package com.sumi.domain.insights

import com.google.common.truth.Truth.assertThat
import com.sumi.domain.usage.DailyUsage
import com.sumi.domain.usage.HourlyUsage
import com.sumi.domain.usage.UsageSession
import java.time.ZoneId
import org.junit.Test

class InsightsTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val start = java.time.Instant.parse("2026-11-06T08:00:00Z").toEpochMilli()

    @Test
    fun `rules are deterministic and separate fact from interpretation`() {
        val sessions = listOf(
            UsageSession("com.a", start, start + 48 * 60_000L, isEstimated = false),
            UsageSession("com.b", start + 60 * 60_000L, start + 75 * 60_000L, isEstimated = false),
        )
        val daily = (0 until 7).map { day ->
            DailyUsage("2026-11-0$day", 3_600_000L * (day + 1), start)
        }
        val hourly = listOf(HourlyUsage("2026-11-06", 21, "com.a", 900_000L))
        val first = InsightsEngine.summarize(
            sessions, daily, hourly, zone, start + 200 * 60_000L,
            windowTotalMs = daily.sumOf { it.totalDurationMs },
            windowDays = daily.size,
        )
        val second = InsightsEngine.summarize(
            sessions, daily, hourly, zone, start + 200 * 60_000L,
            windowTotalMs = daily.sumOf { it.totalDurationMs },
            windowDays = daily.size,
        )
        assertThat(second).isEqualTo(first)
        assertThat(first.insights.any { it.kind == InsightKind.FACT }).isTrue()
        assertThat(first.insights.any { it.kind == InsightKind.INTERPRETATION }).isTrue()
        assertThat(first.longestSessionMs).isEqualTo(48 * 60_000L)
    }

    @Test
    fun `pickups omitted with thin history`() {
        val sessions = listOf(UsageSession("com.a", start, start + 10 * 60_000L, isEstimated = false))
        val daily = listOf(DailyUsage("2026-11-06", 600_000L, start))
        val summary = InsightsEngine.summarize(
            sessions, daily, emptyList(), zone, start + 20 * 60_000L,
            windowTotalMs = 600_000L,
            windowDays = 1,
        )
        assertThat(summary.pickupsEstimate).isNull()
    }

    @Test
    fun `average states its window and includes zero days`() {
        val sessions = listOf(UsageSession("com.a", start, start + 3_600_000L, isEstimated = false))
        val daily = listOf(DailyUsage("2026-11-06", 7_200_000L, start))
        // Window: 7 days with 0 usage on 6 of them — the observer cannot pretend the quiet days don't exist.
        val summary = InsightsEngine.summarize(
            sessions, daily, emptyList(), zone, start + 20 * 60_000L,
            windowTotalMs = 7_200_000L,
            windowDays = 7,
        )
        val avg = summary.insights.first { "average" in it.text }
        // 7_200_000 ms over 7 days = 17m per day.
        assertThat(avg.text).isEqualTo("Your average day was 17m, across the last 7 days.")
    }

    @Test
    fun `average rounds to whole minutes`() {
        val sessions = listOf(UsageSession("com.a", start, start + 1_000L, isEstimated = false))
        val summary = InsightsEngine.summarize(
            sessions, emptyList(), emptyList(), zone, start + 20 * 60_000L,
            windowTotalMs = 25_200_000L, // exactly 7 hours
            windowDays = 7,
        )
        val avg = summary.insights.first { "average" in it.text }
        assertThat(avg.text).isEqualTo("Your average day was 1h 0m, across the last 7 days.")
    }

    @Test
    fun `no psychological claims`() {
        val sessions = listOf(UsageSession("com.a", start, start + 5_000L * 60_000L, isEstimated = true))
        val daily = listOf(DailyUsage("2026-11-06", 1L, start))
        val summary = InsightsEngine.summarize(
            sessions, daily, emptyList(), zone, start + 20 * 60_000L,
            windowTotalMs = 1L,
            windowDays = 1,
        )
        val forbidden = listOf("addict", "stress", "failed")
        summary.insights.forEach { insight ->
            forbidden.forEach { word ->
                assertThat(insight.text.lowercase()).doesNotContain(word)
            }
        }
    }
}
