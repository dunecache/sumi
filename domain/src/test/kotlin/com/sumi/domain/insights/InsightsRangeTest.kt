package com.sumi.domain.insights

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class InsightsRangeTest {

    private val zone = ZoneId.of("Europe/Berlin")

    // Saturday 2026-11-14, 10:30 local (Europe/Berlin = CET, UTC+1).
    private val now = Instant.parse("2026-11-14T09:30:00Z").toEpochMilli()

    @Test
    fun `today is a single partial day ending today`() {
        assertThat(InsightsRange.TODAY.dateKeys(zone, now)).containsExactly("2026-11-14")
    }

    @Test
    fun `yesterday is a single complete past day`() {
        assertThat(InsightsRange.YESTERDAY.dateKeys(zone, now)).containsExactly("2026-11-13")
    }

    @Test
    fun `week is the trailing seven days ending today, oldest first`() {
        assertThat(InsightsRange.WEEK.dateKeys(zone, now)).containsExactly(
            "2026-11-08",
            "2026-11-09",
            "2026-11-10",
            "2026-11-11",
            "2026-11-12",
            "2026-11-13",
            "2026-11-14",
        ).inOrder()
    }

    @Test
    fun `month is the trailing thirty days ending today`() {
        val keys = InsightsRange.MONTH.dateKeys(zone, now)
        assertThat(keys).hasSize(30)
        assertThat(keys.first()).isEqualTo("2026-10-16")
        assertThat(keys.last()).isEqualTo("2026-11-14")
    }

    @Test
    fun `comparison windows are the previous equivalent equivalent periods`() {
        assertThat(InsightsRange.TODAY.comparisonKeys(zone, now)).containsExactly("2026-11-13")
        assertThat(InsightsRange.YESTERDAY.comparisonKeys(zone, now)).containsExactly("2026-11-12")
        assertThat(InsightsRange.WEEK.comparisonKeys(zone, now))
            .containsExactly(
                "2026-11-01",
                "2026-11-02",
                "2026-11-03",
                "2026-11-04",
                "2026-11-05",
                "2026-11-06",
                "2026-11-07",
            ).inOrder()
        assertThat(InsightsRange.MONTH.comparisonKeys(zone, now)).hasSize(30)
        assertThat(InsightsRange.MONTH.comparisonKeys(zone, now).last()).isEqualTo("2026-10-15")
    }

    @Test
    fun `today's comparison window clips at the same local wall-clock time`() {
        val (from, to) = InsightsRange.TODAY.comparisonWindow(zone, now)
        // Window starts at yesterday's local midnight…
        assertThat(from).isEqualTo(Instant.parse("2026-11-12T23:00:00Z").toEpochMilli())
        // …and ends at yesterday 10:30 local, i.e. yesterday up to the same wall-clock time.
        assertThat(to).isEqualTo(Instant.parse("2026-11-13T09:30:00Z").toEpochMilli())
        assertThat(to - from).isEqualTo(10L * 60L * 60L * 1000L + 30L * 60L * 1000L)
    }

    @Test
    fun `fromStorage restores known names and falls back to the week`() {
        assertThat(InsightsRange.fromStorage("MONTH")).isEqualTo(InsightsRange.MONTH)
        assertThat(InsightsRange.fromStorage("TODAY")).isEqualTo(InsightsRange.TODAY)
        assertThat(InsightsRange.fromStorage(null)).isEqualTo(InsightsRange.WEEK)
        assertThat(InsightsRange.fromStorage("nonsense")).isEqualTo(InsightsRange.WEEK)
    }
}
