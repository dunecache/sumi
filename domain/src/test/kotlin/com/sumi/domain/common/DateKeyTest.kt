package com.sumi.domain.common

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import org.junit.Test

class DateKeyTest {

    @Test
    fun `midnight crossing splits by zone at aggregation time`() {
        val zone = ZoneId.of("Europe/Berlin")
        // 2026-11-05T22:30:00Z is 23:30 in Berlin
        val before = java.time.Instant.parse("2026-11-05T22:30:00Z").toEpochMilli()
        // 2026-11-05T23:30:00Z is 00:30 next day in Berlin
        val after = java.time.Instant.parse("2026-11-05T23:30:00Z").toEpochMilli()

        assertThat(DateKey.of(before, zone)).isEqualTo("2026-11-05")
        assertThat(DateKey.of(after, zone)).isEqualTo("2026-11-06")
    }

    @Test
    fun `start and end of day are consistent`() {
        val zone = ZoneId.of("America/New_York")
        val start = DateKey.startOfDayUtcMs("2026-11-06", zone)
        val endExclusive = DateKey.endOfDayUtcMsExclusive("2026-11-06", zone)
        assertThat(endExclusive).isGreaterThan(start)
        assertThat(DateKey.of(start, zone)).isEqualTo("2026-11-06")
        assertThat(DateKey.of(endExclusive - 1, zone)).isEqualTo("2026-11-06")
        assertThat(DateKey.of(endExclusive, zone)).isEqualTo("2026-11-07")
    }

    @Test
    fun `shift preserves date math`() {
        assertThat(DateKey.shift("2026-11-06", 1)).isEqualTo("2026-11-07")
        assertThat(DateKey.shift("2026-11-06", -1)).isEqualTo("2026-11-05")
    }
}
