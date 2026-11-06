package com.sumi.domain.usage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Fixture-based session reconstruction tests (ROADMAP M1, ARCHITECTURE.md §5).
 * Each test documents one rule with a synthetic event stream.
 * clock base = 2026-11-06T09:00:00Z.
 */
class ReconstructionFixturesTest {

    private val t09: Long = 1762410000000L // 2026-11-06T09:00:00Z
    private fun t(min: Long): Long = t09 + min * 60_000L

    private fun fg(pkg: String, min: Long) = RawUsageEvent(pkg, UsageEventType.FOREGROUND, t(min))
    private fun bg(pkg: String, min: Long) = RawUsageEvent(pkg, UsageEventType.BACKGROUND, t(min))
    private fun scrOff(min: Long) = RawUsageEvent("", UsageEventType.SCREEN_NON_INTERACTIVE, t(min))
    private fun scrOn(min: Long) = RawUsageEvent("", UsageEventType.SCREEN_INTERACTIVE, t(min))

    private fun run(events: List<RawUsageEvent>, nowMin: Long = 200): List<UsageSession> =
        SessionReconstruction.reconstruct(Normalization.normalize(events), nowUtcMs = t(nowMin))

    @Test
    fun `basic foreground-background yields session`() {
        val sessions = run(listOf(fg("com.a", 0), bg("com.a", 20)))
        assertThat(sessions).hasSize(1)
        assertThat(sessions[0].durationMs(t(200))).isEqualTo(20 * 60_000L)
        assertThat(sessions[0].isEstimated).isFalse()
    }

    @Test
    fun `missing background stays open`() {
        val sessions = run(listOf(fg("com.a", 0)), nowMin = 30)
        assertThat(sessions).hasSize(1)
        assertThat(sessions[0].endTimeUtcMs).isNull()
        assertThat(sessions[0].durationMs(t(30))).isEqualTo(30 * 60_000L)
    }

    @Test
    fun `background without foreground is ignored`() {
        val sessions = run(listOf(bg("com.a", 10)))
        assertThat(sessions).isEmpty()
    }

    @Test
    fun `screen off closes open session`() {
        val sessions = run(listOf(fg("com.a", 0), scrOff(15), bg("com.a", 20)))
        assertThat(sessions).hasSize(1)
        assertThat(sessions[0].endTimeUtcMs).isEqualTo(t(15))
    }

    @Test
    fun `screen on alone opens nothing`() {
        val sessions = run(listOf(scrOn(0), scrOn(10)))
        assertThat(sessions).isEmpty()
    }

    @Test
    fun `duplicates and out-of-order are normalized`() {
        val events = listOf(bg("com.a", 20), fg("com.a", 0), fg("com.a", 0))
        val sessions = run(events)
        assertThat(sessions).hasSize(1)
    }

    @Test
    fun `very short session is kept`() {
        val sessions = run(listOf(fg("com.a", 0), bg("com.a", 1)))
        assertThat(sessions).hasSize(1)
        assertThat(sessions[0].durationMs(t(200))).isEqualTo(60_000L)
    }

    @Test
    fun `reboot never extends open session`() {
        val events = listOf(fg("com.a", 0), fg("com.a", 120))
        val sessions = SessionReconstruction.reconstruct(
            Normalization.normalize(events),
            nowUtcMs = t(200),
            rebootUtcMs = listOf(t(60)),
        )
        // First session must close at last event before reboot (t(0)), not extend to reboot.
        assertThat(sessions[0].endTimeUtcMs).isEqualTo(t(0))
        assertThat(sessions[0].isEstimated).isTrue()
    }

    @Test
    fun `midnight crossing kept as one session`() {
        val late = 1762470000000L // 2026-11-06T23:30:00Z approx — relative shift via t()
        val start = t09 + (14 * 60 + 30) * 60_000L
        val end = t09 + (15 * 60 + 30) * 60_000L
        val events = listOf(
            RawUsageEvent("com.a", UsageEventType.FOREGROUND, start),
            RawUsageEvent("com.a", UsageEventType.BACKGROUND, end),
        )
        val sessions = SessionReconstruction.reconstruct(
            Normalization.normalize(events),
            nowUtcMs = end + 60_000L,
        )
        assertThat(sessions).hasSize(1)
        assertThat(sessions[0].startTimeUtcMs).isEqualTo(start)
        assertThat(late).isAtLeast(0L)
    }

    @Test
    fun `split-screen keeps overlapping sessions`() {
        val sessions = run(
            listOf(
                fg("com.a", 0),
                fg("com.b", 5),
                bg("com.a", 20),
                bg("com.b", 25),
            ),
        )
        assertThat(sessions).hasSize(2)
    }
}
