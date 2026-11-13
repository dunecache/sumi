package com.sumi.domain.goal

/**
 * Daily intention goal — an awareness instrument, structurally incapable of restricting
 * anything (ARCHITECTURE.md §14): no blocking, no notifications, no enforcement. It reads
 * the same daily aggregation as the headline screen-time statistic; it never computes
 * from a different source.
 */
enum class DailyGoalStatus { ON_TRACK, REACHED, EXCEEDED }

/** Derived goal progress for one day.
 *
 * @param usedMs usage so far (same aggregate as the headline total).
 * @param goalMs configured target in ms; caller passes a positive value.
 * @param remainingMs time still under the goal; clamped at 0 — **never negative**.
 * @param overMs how far usage is past the goal; 0 unless status is [DailyGoalStatus.EXCEEDED].
 * @param fraction progress through the goal; may exceed 1 when over (UI clamps for drawing).
 */
data class DailyGoalProgress(
    val usedMs: Long,
    val goalMs: Long,
    val remainingMs: Long,
    val overMs: Long,
    val fraction: Float,
    val status: DailyGoalStatus,
)

object DailyGoal {

    /**
     * Evaluates goal progress. Exactly reaching the goal is [DailyGoalStatus.REACHED]
     * (remaining 0, over 0); only strictly exceeding is [DailyGoalStatus.EXCEEDED].
     */
    fun evaluate(usedMs: Long, goalMs: Long): DailyGoalProgress {
        require(goalMs > 0L) { "goalMs must be positive" }
        val safeUsed = usedMs.coerceAtLeast(0L)
        val remaining = (goalMs - safeUsed).coerceAtLeast(0L)
        val over = (safeUsed - goalMs).coerceAtLeast(0L)
        val fraction = safeUsed.toFloat() / goalMs.toFloat()
        val status = when {
            over > 0L -> DailyGoalStatus.EXCEEDED
            safeUsed == goalMs -> DailyGoalStatus.REACHED
            else -> DailyGoalStatus.ON_TRACK
        }
        return DailyGoalProgress(safeUsed, goalMs, remaining, over, fraction, status)
    }
}
