package com.sumi.domain.usage

/**
 * Session reconstruction (ARCHITECTURE.md §5). Pure function over normalized events.
 *
 * Model: multi-window aware — each package has its own open session (R8).
 * `DailyUsage.totalDurationMs` later unions intervals so totals never exceed wall clock.
 *
 * Rules implemented:
 * - R1 foreground without background: at event level a package stays open while at least
 *   one of its activities is resumed — a second foreground of the same package bumps a
 *   resume counter, and a background only closes the package when the counter reaches
 *   zero (R8 multi-activity, e.g. `MainActivity` → `SettingsActivity`). At
 *   reconciliation time the repository applies [finalizeOpenSessions] (R1 finalization):
 *   a stale open session is closed at the earliest screen-off after its start, or at the
 *   next session's start as a fallback, flagged estimated; totals are bounded even when
 *   BACKGROUND events are dropped. Implicit close exceeding [MAX_PLAUSIBLE_MS]
 *   is flagged estimated (R14). Adjacent fragments of the same package separated by at
 *   most [COALESCE_GAP_MS] are merged at the end (same-instance ordering artifacts and
 *   sub-2 s activity hand-offs produce one session; the first start is kept, so the
 *   `(packageName, startTime)` storage key is stable).
 * - R2 background without foreground: ignored.
 * - R3 screen non-interactive: closes all open at timestamp.
 * - R4 screen interactive: no-op.
 * - R5 reboot: caller passes reboot boundaries via [rebootUtcMs]; sessions never extend
 *   across a reboot; open sessions close at last event before the reboot and are estimated.
 *   A `DEVICE_SHUTDOWN` event (API 29+) closes all sessions at the shutdown timestamp
 *   (not estimated — the runtime stopped); a `DEVICE_STARTUP` event closes them at the
 *   last event before it, like a reboot boundary (estimated).
 * - R9/R10 handled by [Normalization].
 * - R11 open current: returned with `endTimeUtcMs = null`.
 * - R12 very short: kept.
 * - R14 implausibly long: flagged estimated if longer than [MAX_PLAUSIBLE_MS].
 *
 * A long silence is NOT treated as a close here: apps emit no events while merely
 * staying in the foreground, so a gap rule would cut real sessions at an arbitrary
 * threshold. Long silences are handled where evidence exists — screen-off (R3),
 * reboot (R5), finalization at reconcile time ([finalizeOpenSessions]) or the
 * retention-floor stale-open close.
 *
 * Midnight (R6) and timezone (R7) are aggregation concerns, not reconstruction.
 */
object SessionReconstruction {

    /** 12h — longer without screen evidence is flagged estimated (R14). */
    const val MAX_PLAUSIBLE_MS = 12L * 60L * 60L * 1000L

    /** Adjacent same-package fragments separated by at most this gap are one session. */
    const val COALESCE_GAP_MS = 2_000L

    private data class Open(val packageName: String, val startUtcMs: Long)

    /**
     * Merge duplicate rows for the same (packageName, startTimeUtcMs) key. A truncated
     * window can re-reconstruct an open row for a session finalized by a previous run,
     * so preference goes: a real close (end set, not estimated) over an estimated close,
     * over an open row. The winner carries the better-known end time; the rest are
     * discarded by the upsert.
     */
    fun mergeSessions(sessions: List<UsageSession>): List<UsageSession> =
        sessions.groupBy { it.packageName to it.startTimeUtcMs }
            .map { (_, rows) ->
                rows.firstOrNull { it.endTimeUtcMs != null && !it.isEstimated }
                    ?: rows.firstOrNull { it.endTimeUtcMs != null }
                    ?: rows.first()
            }
            .sortedWith(compareBy({ it.startTimeUtcMs }, { it.packageName }))

    /**
     * R1 at session level (used by the repository's reconciliation pass), with evidence:
     * an open session is closed at the earliest screen-off instant after its start
     * (real evidence, still flagged estimated — the app did not report leaving the
     * foreground itself). Without screen evidence it closes at the next session's
     * start (any package), flagged estimated. Sessions still open whose start precedes
     * [staleBeforeUtcMs] (the platform's event-retention floor) can never be matched to
     * their BACKGROUND event: they close at `min(nowUtcMs, start + MAX_PLAUSIBLE_MS)`,
     * flagged estimated. The newest still-unmatched session stays open (R11).
     */
    fun finalizeOpenSessions(
        sessions: List<UsageSession>,
        screenOffUtcMs: List<Long>,
        staleBeforeUtcMs: Long,
        nowUtcMs: Long,
    ): List<UsageSession> {
        val sorted = sessions.sortedWith(compareBy({ it.startTimeUtcMs }, { it.packageName }))
        val offs = screenOffUtcMs.sorted()
        return sorted.mapIndexed { index, session ->
            if (session.endTimeUtcMs != null) {
                session
            } else if (session.startTimeUtcMs < staleBeforeUtcMs) {
                val end = minOf(nowUtcMs, session.startTimeUtcMs + MAX_PLAUSIBLE_MS)
                session.copy(endTimeUtcMs = end, isEstimated = true)
            } else {
                val screenOff = offs.firstOrNull { it > session.startTimeUtcMs }
                val next = sorted.getOrNull(index + 1)?.startTimeUtcMs
                val end = listOfNotNull(screenOff, next).minOrNull()
                if (end == null || end <= session.startTimeUtcMs) {
                    // Overlapping starts are not closed by each other; no evidence yet.
                    session
                } else {
                    session.copy(endTimeUtcMs = end, isEstimated = true)
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
        // Resumed-activity counter per package (not tracked per activity, only the count
        // matters for "is anything of this package still on screen?").
        val resumeCount = mutableMapOf<String, Int>()
        var lastEventUtcMs: Long? = null

        fun close(pkg: String, endMs: Long, estimated: Boolean) {
            val current = open.remove(pkg) ?: return
            resumeCount.remove(pkg)
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
                // A reboot between two events closes everything at the last event
                // before the gap. Long silent gaps are NOT a close rule: foreground
                // apps emit no events, and finalization at reconcile time bounds them.
                val reboot = crossedReboot(prev, event.timestampUtcMs)
                if (reboot != null) {
                    closeAll(prev, estimated = true)
                }
            }
            when (event.type) {
                UsageEventType.FOREGROUND -> {
                    // A package stays open while any of its activities is resumed;
                    // duplicate foregrounds bump the counter (act1 → act2 hand-off).
                    val count = resumeCount[event.packageName]
                    if (open.containsKey(event.packageName)) {
                        resumeCount[event.packageName] = (count ?: 1) + 1
                    } else {
                        open[event.packageName] = Open(event.packageName, event.timestampUtcMs)
                        resumeCount[event.packageName] = 1
                    }
                }
                UsageEventType.BACKGROUND -> {
                    // R2 ignore if no matching foreground. Otherwise decrement the
                    // counter; the package closes only when its last activity leaves.
                    val count = resumeCount[event.packageName]
                    if (count != null) {
                        if (count > 1) {
                            resumeCount[event.packageName] = count - 1
                        } else {
                            close(event.packageName, event.timestampUtcMs, estimated = false)
                        }
                    }
                }
                UsageEventType.SCREEN_NON_INTERACTIVE -> {
                    // R3
                    closeAll(event.timestampUtcMs, estimated = false)
                }
                UsageEventType.SCREEN_INTERACTIVE -> {
                    // R4 no-op
                }
                UsageEventType.DEVICE_SHUTDOWN -> {
                    // R5 with a definitive instant: the runtime stopped, every activity died.
                    closeAll(event.timestampUtcMs, estimated = false)
                }
                UsageEventType.DEVICE_STARTUP -> {
                    // R5 boundary: events between shutdown and startup are lost; close
                    // at the last event before the startup, flagged estimated.
                    if (prev != null) closeAll(prev, estimated = true)
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

        return coalesceAdjacent(closed, nowUtcMs)
    }

    /**
     * Merge adjacent fragments of the same package separated by at most [COALESCE_GAP_MS]
     * (activity hand-offs emitted as close+open for one fragment of wall-clock time, and
     * same-millisecond order inversions). The merged session keeps the first start (so
     * the storage key is stable) and the last end; `isEstimated` sticks if any fragment
     * was estimated or the merged span exceeds [MAX_PLAUSIBLE_MS]. Fragments that stay
     * open win the merge (their `endTimeUtcMs` stays null).
     */
    private fun coalesceAdjacent(sessions: List<UsageSession>, nowUtcMs: Long): List<UsageSession> {
        if (sessions.size < 2) {
            return sessions.sortedWith(compareBy({ it.startTimeUtcMs }, { it.packageName }))
        }
        val sorted = sessions.sortedWith(compareBy({ it.startTimeUtcMs }, { it.packageName }))
        val out = ArrayList<UsageSession>(sorted.size)
        for (session in sorted) {
            val last = out.lastOrNull()
            if (
                last != null &&
                last.packageName == session.packageName &&
                last.endTimeUtcMs != null &&
                session.startTimeUtcMs - last.endTimeUtcMs <= COALESCE_GAP_MS
            ) {
                val mergedEnd = session.endTimeUtcMs
                out[out.lastIndex] = UsageSession(
                    packageName = last.packageName,
                    startTimeUtcMs = last.startTimeUtcMs,
                    endTimeUtcMs = mergedEnd,
                    isEstimated = last.isEstimated ||
                        session.isEstimated ||
                        ((mergedEnd ?: nowUtcMs) - last.startTimeUtcMs) > MAX_PLAUSIBLE_MS,
                )
            } else {
                out += session
            }
        }
        return out
    }
}
