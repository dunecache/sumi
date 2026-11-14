package com.sumi.domain.focus

/**
 * Focus session rules (ARCHITECTURE.md §16). Pure, unit-tested.
 *
 * Philosophy: a session that ends early ended early — a fact, not a failure. Nothing here
 * judges the user. No streaks, no scores, no warnings beyond the calm summary.
 */
object FocusMath {

    /** Auto-end threshold: away from Sumi this long → the session ends (§16.6). Two minutes. */
    const val AUTO_END_AWAY_MS = 2L * 60L * 1000L

    /** Countdown tick cadence while a session is active. Cheap; not a live tracker. */
    const val POLL_INTERVAL_MS = 1_000L

    /** Drift-check cadence inside the active session state; coarse and battery friendly. */
    const val DRIFT_INTERVAL_MS = 60_000L

    fun remainingMs(startedAtUtcMs: Long, intendedMinutes: Int, nowUtcMs: Long): Long {
        val endUtcMs = startedAtUtcMs + intendedMinutes.toLong() * 60_000L
        return (endUtcMs - nowUtcMs).coerceAtLeast(0L)
    }

    fun isComplete(startedAtUtcMs: Long, intendedMinutes: Int, nowUtcMs: Long): Boolean =
        remainingMs(startedAtUtcMs, intendedMinutes, nowUtcMs) == 0L

    /** Progress through the intended duration, clamped to [0, 1]. */
    fun progressFraction(startedAtUtcMs: Long, intendedMinutes: Int, nowUtcMs: Long): Float {
        val total = intendedMinutes.toLong() * 60_000L
        if (total <= 0L) return 1f
        return ((nowUtcMs - startedAtUtcMs).toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Drift state machine for one session. Feed it foreground events one at a time; the
     * returned value carries the new state. Pure — the caller keeps the state between
     * polls.
     *
     * A "gentle exit" = a transition from Sumi (or a fresh start) to a different app.
     * Staying away does not count again (even if the user hops between apps). Returning
     * to Sumi resets the state, so the next leave counts as a new exit.
     */
    data class ExitState(
        val away: Boolean = false,
        val exits: Int = 0,
    )

    fun onForeground(state: ExitState, isSumiPackage: Boolean): ExitState =
        when {
            isSumiPackage -> state.copy(away = false) // back in Sumi: reset, no new exit
            !state.away -> state.copy(away = true, exits = state.exits + 1) // first leave: one exit
            else -> state // still away; no count inflation
        }

    /** Neutral summary line for the post-session reveal (§16.7). */
    fun summaryText(intendedMinutes: Int, actualMs: Long, exits: Int): String {
        val played = (actualMs / 60_000L).coerceAtLeast(0L)
        val base = if (played >= intendedMinutes) {
            "$played of $intendedMinutes minutes"
        } else {
            "$played of $intendedMinutes minutes, ended early"
        }
        return if (exits == 0) {
            "$base. No gentle exits."
        } else {
            "$base. $exits gentle ${if (exits == 1) "exit" else "exits"}."
        }
    }
}
