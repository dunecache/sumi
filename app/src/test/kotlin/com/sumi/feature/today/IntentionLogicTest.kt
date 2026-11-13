package com.sumi.feature.today

import com.sumi.core.datastore.DailyGoalConfig
import com.sumi.domain.goal.DailyGoalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentionLogicTest {

    private val hourMs = 3_600_000L

    @Test
    fun `no configured goal renders the disabled state`() {
        val state = intentionState(DailyGoalConfig(goalMinutes = null, enabled = true), usedMs = hourMs)
        assertTrue(state is DailyIntentionState.Disabled)
    }

    @Test
    fun `paused goal renders the disabled state even with a stored value`() {
        val state = intentionState(DailyGoalConfig(goalMinutes = 240, enabled = false), usedMs = hourMs)
        assertTrue(state is DailyIntentionState.Disabled)
    }

    @Test
    fun `usage below the goal reports remaining time`() {
        val state = intentionState(DailyGoalConfig(240, enabled = true), usedMs = 2 * hourMs)
        val progress = (state as DailyIntentionState.Progress).progress
        assertEquals(DailyGoalStatus.ON_TRACK, progress.status)
        assertEquals(2 * hourMs, progress.remainingMs)
        assertTrue(progress.remainingMs >= 0L)
    }

    @Test
    fun `usage exactly at the goal is reached`() {
        val state = intentionState(DailyGoalConfig(240, enabled = true), usedMs = 4 * hourMs)
        val progress = (state as DailyIntentionState.Progress).progress
        assertEquals(DailyGoalStatus.REACHED, progress.status)
        assertEquals(0L, progress.overMs)
    }

    @Test
    fun `usage past the goal clamps remaining time at zero`() {
        val state = intentionState(DailyGoalConfig(240, enabled = true), usedMs = 5 * hourMs)
        val progress = (state as DailyIntentionState.Progress).progress
        assertEquals(DailyGoalStatus.EXCEEDED, progress.status)
        assertEquals(0L, progress.remainingMs)
        assertEquals(hourMs, progress.overMs)
    }

    @Test
    fun `status line never prints negative remaining time`() {
        val exceeded = (intentionState(DailyGoalConfig(60, enabled = true), usedMs = 3 * hourMs)
            as DailyIntentionState.Progress).progress
        val line = intentionStatusLine(exceeded)
        assertTrue("line must not contain a negative duration", !line.contains("-"))
    }

    @Test
    fun `status line for the on-track state names the remaining time`() {
        val progress = (intentionState(DailyGoalConfig(300, enabled = true), usedMs = hourMs)
            as DailyIntentionState.Progress).progress
        assertEquals("4h remaining", intentionStatusLine(progress))
    }
}
