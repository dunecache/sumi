package com.sumi.domain.usage

/**
 * Session reconstruction (ARCHITECTURE.md §5). Pure function over normalized events.
 *
 * Model: multi-window aware — each package has its own open session (R8).
 * `DailyUsage.totalDurationMs` later unions intervals so totals never exceed wall clock.
 *
 * Rules implemented:
 * - R1 foreground without background: at event level a foreground of a new app does NOT
 *   close others (multi-window, R8), so the previous session stays open until an explicit
 *   BACKGROUND, SCREEN_NON_INTERACTIVE, gap, or reboot closes it. At reconciliation time
 *   the repository applies [closeOpenAtNextSessionStart] (R1 finalization): a stale open
 *   session is closed at the next session's start and flagged estimated, so totals are
 *   bounded even when BACKGROUND events are dropped. Implicit close exceeding [MAX_PLAUSIBLE_MS]
 *   is flagged estimated (R14).
 * - R2 background without foreground: ignored.
 * - R3 screen non-interactive: closes all open at timestamp.
 * - R4 screen interactive: no-op.
 * - R5 reboot: caller passes reboot boundaries via [rebootUtcMs]; sessions never extend
 *   across a reboot; open sessions close at last event before the reboot and are estimated.
 * - R9/R10 handled by [Normalization].
 * - R11 open current: returned with `endTimeUtcMs = null`.
 * - R12 very short: kept.
 * - R14 implausibly long: flagged estimated if longer than [MAX_PLAUSIBLE_MS].
 *
 * Midnight (R6) and timezone (R7) are aggregation concerns, not reconstruction.
 */
object SessionReconstruction {

    /** 12h — longer without screen evidence is flagged estimated (R14). */
    const val MAX_PLAUSIBLE_MS = 12L * 60L * 60L * 1000L

    /** Gap with no events after which an open session is treated as abandoned (R5 support). */
    const val MAX_GAP_MS = 30L * 60L * 1000L

    private data class Open(val packageName: String, val startUtcMs: Long)

    /**
     * R1 at session level (used by the repository's reconciliation pass): an open session
     * that is followed by another session's start was left open by a missing BACKGROUND.
     * Close it at that next start and flag it estimated. The latest-starting session stays
     * open (R11: still current). Multi-window overlap is preserved because sessions are
     * compared by start time only — overlapping starts are not closed by each other.
     */
    fun closeOpenAtNextSessionStart(sessions: List<UsageSession>): List<UsageSession> {
        val sorted = sessions.sortedWith(compareBy<UsageSession> { it.startTimeUtcMs }.thenBy { it.packageName })
        return sorted.mapIndexed { index, session ->
            if (session.endTimeUtcMs != null) {
                session
            } else {
                val next = sorted.getOrNull(index + 1)
                if (next == null) {
                    session
                } else {
                    session.copy(endTimeUtcMs = next.startTimeUtcMs, isEstimated = true)
                }
            }
        }
    }

    /**
     * @param events normalized, sorted events for one window.
     * @param nowUtcMs current time for open-session duration estimate.
     * @param rebootUtcMs sorted reboot instants (UTC ms); sessions never span a reboot.
     */
    fun reconstruct(
        events: List<RawUsageEvent>,
        nowUtcMs: Long,
        rebootUtcMs: List<Long> = emptyList(),
    ): List<UsageSession> {
        val reboots = rebootUtcMs.sorted()
        val closed = mutableListOf<UsageSession>()
        val open = mutableMapOf<String, Open>()
        var lastEventUtcMs: Long? = null

        fun close(pkg: String, endMs: Long, estimated: Boolean) {
            val current = open.remove(pkg) ?: return
            val duration = (endMs - current.startUtcMs).coerceAtLeast(0L)
            val flagged = estimated || duration > MAX_PLAUSIBLE_MS
            closed += UsageSession(
                packageName = pkg,
                startTimeUtcMs = current.startUtcMs,
                endTimeUtcMs = endMs,
                isEstimated = flagged,
            )
        }

        fun closeAll(endMs: Long, estimated: Boolean) {
            open.keys.toList().forEach { pkg -> close(pkg, endMs, estimated) }
        }

        fun crossedReboot(prevMs: Long, nextMs: Long): Long? {
            val hits = reboots.filter { it > prevMs && it < nextMs }
            return hits.lastOrNull()
        }

        for (event in events) {
            val prev = lastEventUtcMs
            if (prev != null && event.timestampUtcMs > prev) {
                val reboot = crossedReboot(prev, event.timestampUtcMs)
                if (reboot != null) {
                    // Never extend across reboot: close at last known event before gap.
                    closeAll(prev, estimated = true)
                } else if (event.timestampUtcMs - prev > MAX_GAP_MS && open.isNotEmpty()) {
                    // Long silent gap: close at last known event, flag estimated.
                    // Gaps can be battery delay, not necessarily reboot — flag without discarding.
                    closeAll(prev, estimated = true)
                }
            }
            when (event.type) {
                UsageEventType.FOREGROUND -> {
                    // R1 duplicate foreground for same pkg: keep original start.
                    if (open.containsKey(event.packageName)) {
                        // no-op
                    } else {
                        open[event.packageName] = Open(event.packageName, event.timestampUtcMs)
                    }
                }
                UsageEventType.BACKGROUND -> {
                    // R2 ignore if no matching foreground.
                    if (open.containsKey(event.packageName)) {
                        close(event.packageName, event.timestampUtcMs, estimated = false)
                    }
                }
                UsageEventType.SCREEN_NON_INTERACTIVE -> {
                    // R3
                    closeAll(event.timestampUtcMs, estimated = false)
                }
                UsageEventType.SCREEN_INTERACTIVE -> {
                    // R4 no-op
                }
            }
            lastEventUtcMs = event.timestampUtcMs
        }

        // R11 open sessions remain open.
        open.values.forEach { current ->
            val duration = (nowUtcMs - current.startUtcMs).coerceAtLeast(0L)
            closed += UsageSession(
                packageName = current.packageName,
                startTimeUtcMs = current.startUtcMs,
                endTimeUtcMs = null,
                isEstimated = duration > MAX_PLAUSIBLE_MS,
            )
        }

        return closed.sortedWith(compareBy<UsageSession> { it.startTimeUtcMs }.thenBy { it.packageName })
    }
}
