package com.sumi.domain.insights

import com.sumi.domain.usage.HourlyUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TypicalHoursTest {

    private fun rows(vararg entries: Pair<String, Pair<Int, Long>>): List<HourlyUsage> =
        entries.map { (dateKey, it) ->
            val (hour, ms) = it
            HourlyUsage(dateKey, hour, "com.app", ms)
        }

    @Test
    fun `summary sums per hour and counts distinct days`() {
        val summary = HourlySummary.from(
            rows(
                "2026-11-01" to (9 to 10L),
                "2026-11-01" to (9 to 5L),
                "2026-11-02" to (9 to 7L),
                "2026-11-02" to (21 to 60L),
            ),
        )
        assertEquals(22L, summary.perHourMs[9])
        assertEquals(60L, summary.perHourMs[21])
        assertEquals(0L, summary.perHourMs[0])
        assertEquals(2, summary.daysWithUsage)
    }

    @Test
    fun `summary ignores zero-duration buckets when counting days`() {
        val summary = HourlySummary.from(rows("2026-11-01" to (9 to 0L)))
        assertEquals(0, summary.daysWithUsage)
    }

    @Test
    fun `a three-hour window around the peak hours wins`() {
        val summary = HourlySummary(
            perHourMs = List(24) { hour ->
                when (hour) {
                    20, 21, 22 -> 100L
                    8 -> 40L
                    else -> 0L
                }
            },
            daysWithUsage = 5,
        )
        val window = TypicalHours.typicalWindow(summary)
        assertEquals(ActiveWindow(20, 23, 5), window)
    }

    @Test
    fun `ties resolve to the earlier window, deterministically`() {
        val summary = HourlySummary(
            perHourMs = List(24) { hour -> if (hour in 6..8 || hour in 14..16) 30L else 0L },
            daysWithUsage = 4,
        )
        val window = TypicalHours.typicalWindow(summary)
        assertEquals(ActiveWindow(6, 9, 4), window)
    }

    @Test
    fun `fewer than three days of history claims nothing`() {
        val summary = HourlySummary(
            perHourMs = List(24) { hour -> if (hour == 21) 10_000L else 0L },
            daysWithUsage = 2,
        )
        assertNull(TypicalHours.typicalWindow(summary))
    }

    @Test
    fun `no usage at all claims nothing`() {
        val summary = HourlySummary.from(emptyList())
        assertNull(TypicalHours.typicalWindow(summary))
    }

    @Test
    fun `late-night apps peak in the last in-day window, never a wrapped one`() {
        val summary = HourlySummary(
            perHourMs = List(24) { hour -> if (hour in 22..23) 50L else 0L },
            daysWithUsage = 6,
        )
        // Windows never wrap past midnight: [20,23) and [21,24) both carry the load;
        // ties resolve to the earlier window deterministically.
        val window = TypicalHours.typicalWindow(summary)
        assertEquals(ActiveWindow(20, 23, 6), window)
    }
}
