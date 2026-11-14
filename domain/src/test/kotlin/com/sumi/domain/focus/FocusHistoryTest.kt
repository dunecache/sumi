package com.sumi.domain.focus

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class FocusHistoryTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun zoneMillis(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        java.time.LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `window start is the local midnight of 6 days back`() {
        val nowUtc = zoneMillis(2026, 11, 14, 14, 0)
        val start = FocusHistory.windowStartUtcMs(nowUtc, zone)
        assertEquals(zoneMillis(2026, 11, 8, 0, 0), start)
    }

    @Test
    fun `weekly sum counts only sessions which ended inside the window`() {
        val nowUtc = zoneMillis(2026, 11, 14, 14, 0)
        val windowStart = FocusHistory.windowStartUtcMs(nowUtc, zone)
        val rows = listOf(
            // Ended at the very start of the window: included.
            FocusHistory.FocusWindowRow(zoneMillis(2026, 11, 7, 22, 0), zoneMillis(2026, 11, 8, 0, 0)),
            // Inside the window: included.
            FocusHistory.FocusWindowRow(zoneMillis(2026, 11, 10, 9, 0), zoneMillis(2026, 11, 10, 9, 25)),
            // Ended yesterday: included.
            FocusHistory.FocusWindowRow(zoneMillis(2026, 11, 13, 20, 0), zoneMillis(2026, 11, 13, 21, 0)),
            // Ended before the window: excluded.
            FocusHistory.FocusWindowRow(zoneMillis(2026, 11, 2, 9, 0), zoneMillis(2026, 11, 2, 9, 45)),
        )
        // Ended exactly at the window edge (included): 2h. Next: 25m. Then 1h.
        val expected = (120L + 25L + 60L) * 60_000L
        assertEquals(expected, FocusHistory.weeklyElapsedMs(rows, windowStart))
    }
}
