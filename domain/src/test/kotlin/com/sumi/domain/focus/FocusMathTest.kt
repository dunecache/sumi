package com.sumi.domain.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class FocusMathTest {

    private val start = 1_000_000L
    private val intended = 25

    @Test
    fun `remaining decreases to zero and clamps there`() {
        assertEquals(25 * 60_000L, FocusMath.remainingMs(start, intended, start))
        assertEquals(20 * 60_000L, FocusMath.remainingMs(start, intended, start + 5 * 60_000L))
        assertEquals(0L, FocusMath.remainingMs(start, intended, start + 30 * 60_000L))
    }

    @Test
    fun `complete fires exactly when remaining hits zero`() {
        assertFalse(FocusMath.isComplete(start, intended, start + 24 * 60_000L + 59_000L))
        assertTrue(FocusMath.isComplete(start, intended, start + 25 * 60_000L))
    }

    @Test
    fun `progress fraction clamps to one and ignores reversed order`() {
        assertEquals(0f, FocusMath.progressFraction(start, intended, start))
        assertEquals(0.5f, FocusMath.progressFraction(start, intended, start + 12 * 60_000L + 30_000L), 0.01f)
        assertEquals(1f, FocusMath.progressFraction(start, intended, start + 26 * 60_000L))
    }

    @Test
    fun `first leave counts one exit and stay-away does not inflate`() {
        var state = FocusMath.ExitState()
        state = FocusMath.onForeground(state, isSumiPackage = true)  // on Sumi
        assertEquals(0, state.exits)
        state = FocusMath.onForeground(state, isSumiPackage = false) // leaves for Instagram
        assertEquals(1, state.exits)
        state = FocusMath.onForeground(state, isSumiPackage = false) // hops to Twitter
        assertEquals(1, state.exits)
        state = FocusMath.onForeground(state, isSumiPackage = false) // deep-link from hop
        assertEquals(1, state.exits)
    }

    @Test
    fun `returning to Sumi resets the away marker so the next leave is a new exit`() {
        var state = FocusMath.ExitState()
        state = FocusMath.onForeground(state, isSumiPackage = false)
        state = FocusMath.onForeground(state, isSumiPackage = true)
        state = FocusMath.onForeground(state, isSumiPackage = false)
        assertEquals(2, state.exits)
    }

    @Test
    fun `away limit is two minutes`() {
        assertEquals(120_000L, FocusMath.AUTO_END_AWAY_MS)
    }

    @Test
    fun `summary text never judges and names under-run honestly`() {
        assertEquals("25 of 25 minutes. No gentle exits.", FocusMath.summaryText(25, 25 * 60_000L, 0))
        assertEquals("14 of 25 minutes, ended early. 2 gentle exits.", FocusMath.summaryText(25, 14 * 60_000L, 2))
        assertEquals("14 of 25 minutes, ended early. 1 gentle exit.", FocusMath.summaryText(25, 14 * 60_000L, 1))
    }
}
