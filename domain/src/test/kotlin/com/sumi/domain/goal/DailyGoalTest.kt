package com.sumi.domain.goal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DailyGoalTest {

    private val hour = 3_600_000L

    @Test
    fun `usage below goal is on track with positive remaining time`() {
        val progress = DailyGoal.evaluate(usedMs = hour, goalMs = 5 * hour)
        assertThat(progress.status).isEqualTo(DailyGoalStatus.ON_TRACK)
        assertThat(progress.remainingMs).isEqualTo(4 * hour)
        assertThat(progress.overMs).isEqualTo(0L)
        assertThat(progress.fraction).isWithin(1e-6f).of(0.2f)
    }

    @Test
    fun `usage exactly at the goal is reached - not exceeded`() {
        val progress = DailyGoal.evaluate(usedMs = 5 * hour, goalMs = 5 * hour)
        assertThat(progress.status).isEqualTo(DailyGoalStatus.REACHED)
        assertThat(progress.remainingMs).isEqualTo(0L)
        assertThat(progress.overMs).isEqualTo(0L)
        assertThat(progress.fraction).isWithin(1e-6f).of(1f)
    }

    @Test
    fun `usage past the goal is exceeded - remaining time is never negative`() {
        val progress = DailyGoal.evaluate(usedMs = 6 * hour, goalMs = 5 * hour)
        assertThat(progress.status).isEqualTo(DailyGoalStatus.EXCEEDED)
        assertThat(progress.remainingMs).isEqualTo(0L) // clamped, never negative
        assertThat(progress.overMs).isEqualTo(hour)
        assertThat(progress.fraction).isWithin(1e-6f).of(1.2f)
    }

    @Test
    fun `zero usage is on track with zero fraction`() {
        val progress = DailyGoal.evaluate(usedMs = 0L, goalMs = 4 * hour)
        assertThat(progress.status).isEqualTo(DailyGoalStatus.ON_TRACK)
        assertThat(progress.fraction).isEqualTo(0f)
        assertThat(progress.remainingMs).isEqualTo(4 * hour)
    }

    @Test
    fun `fraction can far exceed one - drawing is the UI's job to clamp`() {
        val progress = DailyGoal.evaluate(usedMs = 20 * hour, goalMs = 2 * hour)
        assertThat(progress.fraction).isWithin(1e-6f).of(10f)
        assertThat(progress.overMs).isEqualTo(18 * hour)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive goals are rejected`() {
        DailyGoal.evaluate(usedMs = hour, goalMs = 0L)
    }
}
