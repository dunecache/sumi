package com.sumi.domain.usage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IntervalMathTest {

    private fun session(startMinuteOffset: Long, durationMin: Long): UsageSession =
        UsageSession(
            packageName = "com.example.app",
            startTimeUtcMs = 1_000_000L + startMinuteOffset * 60_000L,
            endTimeUtcMs = 1_000_000L + (startMinuteOffset + durationMin) * 60_000L,
            isEstimated = false,
        )

    @Test
    fun `sums non-overlapping sessions`() {
        val s = listOf(session(0, 10), session(30, 15))
        assertThat(IntervalMath.unionMsInWindow(s, 0L, 100_000_000L, nowUtcMs = 100_000_000L))
            .isEqualTo(25L * 60_000L)
    }

    @Test
    fun `overlapping split-screen sessions are not double-counted`() {
        val s = listOf(session(0, 20), session(5, 15))
        // Union of [0,20) and [5,20) is [0,20) → 20 minutes.
        assertThat(IntervalMath.unionMsInWindow(s, 0L, 100_000_000L, nowUtcMs = 100_000_000L))
            .isEqualTo(20L * 60_000L)
    }

    @Test
    fun `clips sessions at the window boundaries`() {
        val base = 1_000_000L
        val s = listOf(session(0, 60))
        // Window = 10..30 minutes into the session → exactly the middle 20 minutes.
        assertThat(IntervalMath.unionMsInWindow(s, base + 10L * 60_000L, base + 30L * 60_000L, nowUtcMs = base))
            .isEqualTo(20L * 60_000L)
    }

    @Test
    fun `open sessions are measured up to now but clipped at the window`() {
        val open = UsageSession(
            packageName = "com.example.app",
            startTimeUtcMs = 0L,
            endTimeUtcMs = null,
            isEstimated = false,
        )
        assertThat(IntervalMath.unionMsInWindow(listOf(open), 0L, 500_000L, nowUtcMs = 2_000_000L))
            .isEqualTo(500_000L)
    }

    @Test
    fun `adjacent sessions merge without drifting`() {
        val s = listOf(session(0, 10), session(10, 10), session(20, 10))
        assertThat(IntervalMath.unionMsInWindow(s, 0L, 100_000_000L, nowUtcMs = 100_000_000L))
            .isEqualTo(30L * 60_000L)
    }

    @Test
    fun `empty window and empty input yield zero`() {
        assertThat(IntervalMath.unionMsInWindow(emptyList(), 0L, 1L, 1L)).isEqualTo(0L)
        assertThat(IntervalMath.unionMsInWindow(listOf(session(0, 10)), 1_000L, 1_000L, 1L)).isEqualTo(0L)
    }
}
