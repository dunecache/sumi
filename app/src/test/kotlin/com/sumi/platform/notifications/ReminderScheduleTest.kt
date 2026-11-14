package com.sumi.platform.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The once-a-day reminder must ring at the intended local time, even at the day boundary.
 * The scheduler wraps the WorkManager call; this covers the wall-clock math it delegates to.
 */
class ReminderScheduleTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): ZonedDateTime =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone)

    @Test
    fun `later today fires today`() {
        val now = at(2026, 11, 14, 8, 30)
        val delay = initialDelayToNext(9 * 60, now)
        assertEquals(Duration.ofMinutes(30).toMillis(), delay)
    }

    @Test
    fun `exactly now rolls to the next day instead of firing immediately late`() {
        val now = at(2026, 11, 14, 9, 0)
        val delay = initialDelayToNext(9 * 60, now)
        // "at" the time, not after it — the next occurrence wins; tomorrow's 09:00.
        assertEquals(Duration.ofDays(1).toMillis(), delay)
    }

    @Test
    fun `minute just passed rolls to tomorrow`() {
        val now = at(2026, 11, 14, 9, 1)
        val delay = initialDelayToNext(9 * 60, now)
        assertEquals(Duration.ofMinutes(23 * 60 + 59).toMillis(), delay)
    }

    @Test
    fun `after midnight rolls to the same morning next day`() {
        val now = at(2026, 11, 14, 23, 30)
        val delay = initialDelayToNext(9 * 60, now)
        assertEquals(Duration.ofMinutes(9 * 60 + 30).toMillis(), delay)
    }

    @Test
    fun `out of range minutes are coerced, never crash`() {
        val now = at(2026, 11, 14, 8, 0)
        val tooLate = initialDelayToNext(9_999, now)
        assertEquals(Duration.ofMinutes(15 * 60 + 59).toMillis(), tooLate) // clamps to 23:59
        assertTrue(initialDelayToNext(-5, now) >= 0)
    }
}
