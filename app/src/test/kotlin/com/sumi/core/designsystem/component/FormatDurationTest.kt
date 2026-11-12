package com.sumi.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

/** Duration formatting rules per the navigation/Today reference §4. */
class FormatDurationTest {
    @Test
    fun `zero renders 0m`() = assertEquals("0m", formatDuration(0L))

    @Test
    fun `negative clamps to 0m`() = assertEquals("0m", formatDuration(-5_000L))

    @Test
    fun `sub-minute renders under a minute`() = assertEquals("Under a minute", formatDuration(30_000L))

    @Test
    fun `minutes render bare`() = assertEquals("33m", formatDuration(33L * 60_000L))

    @Test
    fun `exact hour drops zero minutes`() = assertEquals("5h", formatDuration(5L * 3_600_000L))

    @Test
    fun `hour and minutes`() = assertEquals("4h 51m", formatDuration((4L * 60 + 51) * 60_000L))

    @Test
    fun `never 0h`() = assertEquals("33m", formatDuration(2_030_000L))
}
