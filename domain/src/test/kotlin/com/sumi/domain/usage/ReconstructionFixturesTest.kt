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
    fun `long session with no events between is kept intact`() {
        // Apps emit no events while staying in the foreground; a gap must never cut a session.
        val sessions = run(listOf(fg("com.a", 0), bg("com.a", 45)))
        assertThat(sessions).hasSize(1)
        assertThat(sessions[0].endTimeUtcMs).isEqualTo(t(45))
        assertThat(sessions[0].durationMs(t(200))).isEqualTo(45 * 60_000L)
        assertThat(sessions[0].isEstimated).isFalse()
    }

    @Test
    fun `long session survives unrelated events from another package`() {
        val sessions = run(listOf(fg("com.b", 20), fg("com.a", 0), bg("com.b", 25), bg("com.a", 45)))
        val a = sessions.first { it.packageName == "com.a" }
        assertThat(a.endTimeUtcMs).isEqualTo(t(45))
        assertThat(a.isEstimated).isFalse()
    }

    @Test
    fun `device shutdown closes all sessions at the shutdown instant`() {
        val events = listOf(fg("com.a", 0), fg("com.b", 5), RawUsageEvent("", UsageEventType.DEVICE_SHUTDOWN, t(10)))
        val sessions = run(events)
        assertThat(sessions).hasSize(2)
        assertThat(sessions.all { it.endTimeUtcMs == t(10) }).isTrue()
        assertThat(sessions.none { it.isEstimated }).isTrue() // shutdown is definitive, not a guess
    }

    @Test
    fun `device startup closes open sessions at the last event before it`() {
        val events = listOf(fg("com.a", 0), fg("com.b", 5), RawUsageEvent("", UsageEventType.DEVICE_STARTUP, t(10)))
        val sessions = run(events)
        assertThat(sessions.first { it.packageName == "com.a" }.endTimeUtcMs).isEqualTo(t(5))
        assertThat(sessions.first { it.packageName == "com.a" }.isEstimated).isTrue()
        assertThat(sessions.first { it.packageName == "com.b" }.endTimeUtcMs).isEqualTo(t(5))
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

    @Test
    fun `open session closes at next session start without evidence (R1 finalization)`() {
        // com.a left without BACKGROUND; com.b starts 30 min later, no screen-off known.
        val stale = UsageSession("com.a", t(0), endTimeUtcMs = null, isEstimated = false)
        val next = UsageSession("com.b", t(30), endTimeUtcMs = t(50), isEstimated = false)
        val finalized = SessionReconstruction.finalizeOpenSessions(
            listOf(stale, next),
            screenOffUtcMs = emptyList(),
            staleBeforeUtcMs = 0L,
            nowUtcMs = t(200),
        )
        assertThat(finalized[0].endTimeUtcMs).isEqualTo(t(30))
        assertThat(finalized[0].isEstimated).isTrue()
        assertThat(finalized[1]).isEqualTo(next)
    }

    @Test
    fun `open session closes at the earliest screen-off after its start`() {
        // Evidence beats the R1 fallback: a screen-off at t(10) closes com.a before com.b starts at t(30).
        val stale = UsageSession("com.a", t(0), endTimeUtcMs = null, isEstimated = false)
        val next = UsageSession("com.b", t(30), endTimeUtcMs = t(50), isEstimated = false)
        val finalized = SessionReconstruction.finalizeOpenSessions(
            listOf(stale, next),
            screenOffUtcMs = listOf(t(10)),
            staleBeforeUtcMs = 0L,
            nowUtcMs = t(200),
        )
        assertThat(finalized[0].endTimeUtcMs).isEqualTo(t(10))
        assertThat(finalized[0].isEstimated).isTrue()
    }

    @Test
    fun `screen-off later than next start does not override it`() {
        val stale = UsageSession("com.a", t(0), endTimeUtcMs = null, isEstimated = false)
        val next = UsageSession("com.b", t(30), endTimeUtcMs = t(50), isEstimated = false)
        val finalized = SessionReconstruction.finalizeOpenSessions(
            listOf(stale, next),
            screenOffUtcMs = listOf(t(45)),
            staleBeforeUtcMs = 0L,
            nowUtcMs = t(200),
        )
        assertThat(finalized[0].endTimeUtcMs).isEqualTo(t(30))
    }

    @Test
    fun `stale open session older than retention closes at a bounded plausible end`() {
        // No events can exist for a session that started before the retention floor:
        // close at min(now, start + MAX_PLAUSIBLE_MS), flagged estimated.
        val old = UsageSession("com.a", t(0), endTimeUtcMs = null, isEstimated = false)
        val finalized = SessionReconstruction.finalizeOpenSessions(
            listOf(old),
            screenOffUtcMs = emptyList(),
            staleBeforeUtcMs = t(5),
            nowUtcMs = t(60 * 20), // 20 h later: beyond MAX_PLAUSIBLE_MS
        )
        assertThat(finalized[0].endTimeUtcMs).isEqualTo(t(0) + SessionReconstruction.MAX_PLAUSIBLE_MS)
        assertThat(finalized[0].isEstimated).isTrue()
    }

    @Test
    fun `latest open session stays open (R11)`() {
        val older = UsageSession("com.a", t(0), endTimeUtcMs = t(20), isEstimated = false)
        val current = UsageSession("com.b", t(30), endTimeUtcMs = null, isEstimated = false)
        val finalized = SessionReconstruction.finalizeOpenSessions(
            listOf(current, older),
            screenOffUtcMs = emptyList(),
            staleBeforeUtcMs = 0L,
            nowUtcMs = t(200),
        )
        assertThat(finalized.first { it.packageName == "com.b" }.endTimeUtcMs).isNull()
    }

    @Test
    fun `multi-activity hand-off yields one session`() {
        // act1 foreground, act2 foreground, act1 backgrounds (act2 still on screen),
        // act2 backgrounds — one continuous session, not truncated at act1's background.
        val base = t09
        val events = listOf(
            RawUsageEvent("com.a", UsageEventType.FOREGROUND, base), // act1
            RawUsageEvent("com.a", UsageEventType.FOREGROUND, base + 100), // act2
            RawUsageEvent("com.a", UsageEventType.BACKGROUND, base + 150), // act1 leaves
            RawUsageEvent("com.a", UsageEventType.BACKGROUND, base + 5_000), // act2 leaves
        )
        val sessions = run(events)
        assertThat(sessions).hasSize(1)
        assertThat(sessions[0].endTimeUtcMs).isEqualTo(base + 5_000)
        assertThat(sessions[0].isEstimated).isFalse()
    }

    @Test
    fun `activity hand-off with reversed pause-resume order is coalesced`() {
        // Some builds emit PAUSED(old) before RESUMED(new): close + immediate reopen.
        val base = t09
        val events = listOf(
            RawUsageEvent("com.a", UsageEventType.FOREGROUND, base),
            RawUsageEvent("com.a", UsageEventType.BACKGROUND, base + 100),
            RawUsageEvent("com.a", UsageEventType.FOREGROUND, base + 150),
            RawUsageEvent("com.a", UsageEventType.BACKGROUND, base + 5_000),
        )
        val sessions = run(events)
        assertThat(sessions).hasSize(1)
        assertThat(sessions[0].endTimeUtcMs).isEqualTo(base + 5_000)
    }

    @Test
    fun `same-package fragments more than two seconds apart are kept apart`() {
        val base = t09
        val events = listOf(
            RawUsageEvent("com.a", UsageEventType.FOREGROUND, base),
            RawUsageEvent("com.a", UsageEventType.BACKGROUND, base + 100),
            RawUsageEvent("com.a", UsageEventType.FOREGROUND, base + 5_000),
            RawUsageEvent("com.a", UsageEventType.BACKGROUND, base + 6_000),
        )
        assertThat(run(events)).hasSize(2)
    }

    @Test
    fun `coalescing keeps an estimated fragment flag`() {
        val base = t09
        val sessions = SessionReconstruction.reconstruct(
            Normalization.normalize(
                listOf(
                    RawUsageEvent("com.a", UsageEventType.FOREGROUND, base),
                    RawUsageEvent("com.a", UsageEventType.FOREGROUND, base + 1_000),
                ),
            ),
            nowUtcMs = base + 60_000L,
            rebootUtcMs = listOf(base + 500),
        )
        // The reboot closes [base, base] estimated; the reopened fragment coalesces onto it.
        assertThat(sessions).hasSize(1)
        assertThat(sessions[0].startTimeUtcMs).isEqualTo(base)
        assertThat(sessions[0].endTimeUtcMs).isNull()
        assertThat(sessions[0].isEstimated).isTrue()
    }

    @Test
    fun `merge prefers a real close over an estimated close over an open row`() {
        val open = UsageSession("com.a", t(0), endTimeUtcMs = null, isEstimated = false)
        val estimated = UsageSession("com.a", t(0), endTimeUtcMs = t(30), isEstimated = true)
        val real = UsageSession("com.a", t(0), endTimeUtcMs = t(45), isEstimated = false)

        assertThat(SessionReconstruction.mergeSessions(listOf(open, estimated))).isEqualTo(listOf(estimated))
        assertThat(SessionReconstruction.mergeSessions(listOf(estimated, open))).isEqualTo(listOf(estimated))
        assertThat(SessionReconstruction.mergeSessions(listOf(estimated, real))).isEqualTo(listOf(real))
        assertThat(SessionReconstruction.mergeSessions(listOf(open, real))).isEqualTo(listOf(real))
    }

    @Test
    fun `re-collected run replaces an estimated close with the real background`() {
        // Run 2 reconstructs the real BACKGROUND; the stored estimated close from run 1 must lose.
        val stored = UsageSession("com.a", t(0), endTimeUtcMs = t(30), isEstimated = true)
        val reconstructed = UsageSession("com.a", t(0), endTimeUtcMs = t(45), isEstimated = false)
        val merged = SessionReconstruction.mergeSessions(listOf(stored, reconstructed))
        val finalized = SessionReconstruction.finalizeOpenSessions(
            merged,
            screenOffUtcMs = emptyList(),
            staleBeforeUtcMs = 0L,
            nowUtcMs = t(200),
        )
        assertThat(finalized[0].endTimeUtcMs).isEqualTo(t(45))
        assertThat(finalized[0].isEstimated).isFalse()
    }

    @Test
    fun `same-instant starts are not closed by each other`() {
        val a = UsageSession("com.a", t(10), endTimeUtcMs = null, isEstimated = false)
        val b = UsageSession("com.b", t(10), endTimeUtcMs = t(40), isEstimated = false)
        val finalized = SessionReconstruction.finalizeOpenSessions(
            listOf(a, b),
            screenOffUtcMs = emptyList(),
            staleBeforeUtcMs = 0L,
            nowUtcMs = t(200),
        )
        assertThat(finalized.first { it.packageName == "com.a" }.endTimeUtcMs).isNull()
    }
}
