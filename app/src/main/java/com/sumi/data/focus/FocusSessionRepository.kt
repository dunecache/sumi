package com.sumi.data.focus

import com.sumi.core.common.AppDispatchers
import com.sumi.core.database.dao.FocusSessionDao
import com.sumi.core.database.entity.FocusOutcome
import com.sumi.core.database.entity.FocusSession
import com.sumi.domain.common.Clock
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.focus.FocusHistory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Focus session store (Room `focus_sessions`). All timestamps UTC epoch ms.
 *
 * Open-session invariant: at most one open session exists at any time; outcomes close it.
 * Reboot finalization is deliberate: the boot receiver doesn't touch the DB; instead the
 * repository finalizes any open session whose start precedes the newest recorded boot
 * when the app next reads state (see [finalizeOpenAfterReboot]).
 */
@Singleton
class FocusSessionRepository @Inject constructor(
    private val sessionDao: FocusSessionDao,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
    private val dispatchers: AppDispatchers,
) {

    /** Live open session; emits null when nobody is focusing. Ends trigger recomposition. */
    fun observeOpen(): Flow<FocusSession?> = sessionDao.observeOpen()

    /** One-shot read of the open session; null when none. */
    suspend fun openSessionOrNull(): FocusSession? = withContext(dispatchers.io) { sessionDao.open() }

    /** Closed sessions, newest first — the Focus history feed. */
    fun observeHistory(): Flow<List<FocusSession>> = sessionDao.observeHistory()

    /**
     * Starts a session. Any open session is closed first (ENDED_EARLY) in the same
     * transaction as the insert (see [FocusSessionDao.insertSealingOpen]), so the new
     * one never shares state with a stale one — even under a race. Returns the new
     * session id.
     */
    suspend fun startSession(intendedMinutes: Int): Long = withContext(dispatchers.io) {
        val now = clock.nowUtcMs()
        sessionDao.insertSealingOpen(
            FocusSession(
                startedAtUtcMs = now,
                intendedMinutes = intendedMinutes,
                exitCount = 0,
                outcome = FocusOutcome.OPEN,
            ),
        )
    }

    /** Records one gentle exit on the open session; no-op when none is open. */
    suspend fun recordExit() = withContext(dispatchers.io) {
        sessionDao.incrementOpenExitCount()
    }

    /** Closes the open session with [outcome]; no-op when none is open. */
    suspend fun closeOpenSession(endedAtUtcMs: Long, outcome: String, exitCount: Int? = null) =
        withContext(dispatchers.io) {
            sessionDao.open()?.let { session ->
                sessionDao.finish(
                    session.id,
                    endedAtUtcMs,
                    exitCount ?: session.exitCount,
                    outcome,
                )
            }
        }

    /** Ends the session now, outcome = early end. */
    suspend fun endEarly() = closeOpenSession(clock.nowUtcMs(), FocusOutcome.ENDED_EARLY)

    /** Completed by the countdown (service auto-ends or user lets it ring through). */
    suspend fun endCompleted() = closeOpenSession(clock.nowUtcMs(), FocusOutcome.COMPLETED)

    /**
     * Finalizes an open session whose start precedes the newest boot instant. Called at
     * app start (bootstrap); the boot receiver records instants, this folds them in.
     * If no recorded boot follows the session start, the process died of other causes
     * (OEM kill, archive) — the row is sealed as `interrupted` with **no end timestamp**,
     * because the actual end instant is unknowable; better than inventing one.
     */
    suspend fun finalizeOpenAfterReboot(bootInstantsUtcMs: List<Long>): Boolean = withContext(dispatchers.io) {
        val open = sessionDao.open() ?: return@withContext false
        val lastBoot = bootInstantsUtcMs.maxOrNull()
        if (lastBoot != null && lastBoot > open.startedAtUtcMs) {
            sessionDao.finish(open.id, lastBoot, open.exitCount, FocusOutcome.REBOOT)
        } else {
            sessionDao.sealOpenAs(open.id, FocusOutcome.INTERRUPTED)
        }
        true
    }

    /** Sums elapsed focus time across sessions that ended inside the trailing [days]. */
    suspend fun weeklyFocusMs(nowUtcMs: Long, days: Int = 7): Long = withContext(dispatchers.io) {
        val zone = timeZone.currentZone()
        val start = FocusHistory.windowStartUtcMs(nowUtcMs, zone, days)
        val rows = sessionDao.since(start).map {
            FocusHistory.FocusWindowRow(it.startedAtUtcMs, it.endedAtUtcMs ?: it.startedAtUtcMs)
        }
        FocusHistory.weeklyElapsedMs(rows, start)
    }
}
