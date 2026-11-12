package com.sumi.feature.today

import com.sumi.core.database.entity.Application
import com.sumi.domain.usage.DailyAppUsage
import org.junit.Assert.assertEquals
import org.junit.Test

/** Summary templates (§3.7) and most-used labeling/sorting (§3.5) per the navigation/Today reference. */
class TodayLogicTest {

    private fun app(pkg: String, name: String) = Application(packageName = pkg, displayName = name)
    private fun usage(pkg: String, ms: Long) =
        DailyAppUsage(dateKey = "2026-11-12", packageName = pkg, durationMs = ms)

    // --- observationFor (§3.7 templates) ---

    @Test
    fun `under a minute is a quiet day`() {
        assertEquals("A quiet day on the phone so far.", observationFor(0L, emptyList()))
        assertEquals("A quiet day on the phone so far.", observationFor(59_999L, emptyList()))
    }

    @Test
    fun `total only when no app data`() {
        assertEquals("2h 15m today.", observationFor(135L * 60_000L, emptyList()))
    }

    @Test
    fun `largest share names the top app`() {
        val mostUsed = listOf(MostUsedApp("com.a", "A", 3_600_000L))
        assertEquals("1h today — A took the largest share.", observationFor(3_600_000L, mostUsed))
    }

    @Test
    fun `never spends hours`() {
        // 33m total with a top app must not produce "0h" (build 2026-11-12 bug).
        val mostUsed = listOf(MostUsedApp("com.a", "launcher3", 8L * 60_000L))
        assertEquals("33m today — launcher3 took the largest share.", observationFor(33L * 60_000L, mostUsed))
    }

    // --- mostUsedApps (§3.5) ---

    @Test
    fun `sorted by duration descending with alphabetical tiebreak`() {
        val result = mostUsedApps(
            listOf(usage("com.b", 1_000L), usage("com.a", 1_000L), usage("com.c", 2_000L)),
            emptyList(),
        )
        assertEquals(listOf("c", "a", "b"), result.map { it.displayName })
    }

    @Test
    fun `capped at five rows`() {
        val input = (1..8).map { usage("com.app$it", (10 - it) * 60_000L) }
        assertEquals(5, mostUsedApps(input, emptyList()).size)
    }

    @Test
    fun `friendly label wins over package tail`() {
        val result = mostUsedApps(
            listOf(usage("com.example.app", 1_000L)),
            listOf(app("com.example.app", "Example")),
        )
        assertEquals("Example", result.single().displayName)
    }

    @Test
    fun `no friendly label falls back to package tail`() {
        val result = mostUsedApps(listOf(usage("com.launchers.launcher3", 1_000L)), emptyList())
        assertEquals("launcher3", result.single().displayName)
    }

    @Test
    fun `duplicate labels disambiguated with package tail`() {
        val result = mostUsedApps(
            listOf(usage("com.android.systemui", 2_000L), usage("com.other.android", 1_000L)),
            listOf(app("com.android.systemui", "Android"), app("com.other.android", "Android")),
        )
        assertEquals("Android (systemui)", result[0].displayName)
        assertEquals("Android (android)", result[1].displayName)
    }

    @Test
    fun `bare package name disambiguates as system`() {
        // "android" resolving to itself: tail equals name, head is empty → "system" (§3.5 example).
        val result = mostUsedApps(
            listOf(usage("android", 2_000L), usage("com.other.android", 1_000L)),
            listOf(app("android", "Android"), app("com.other.android", "Android")),
        )
        assertEquals("Android (system)", result[0].displayName)
        assertEquals("Android (other)", result[1].displayName)
    }

    @Test
    fun `duplicate fallback labels disambiguated by parent segment`() {
        val result = mostUsedApps(
            listOf(usage("com.a.launcher3", 2_000L), usage("com.b.launcher3", 1_000L)),
            emptyList(),
        )
        assertEquals("launcher3 (a)", result[0].displayName)
        assertEquals("launcher3 (b)", result[1].displayName)
    }

    @Test
    fun `empty usage renders empty list`() {
        assertEquals(emptyList<MostUsedApp>(), mostUsedApps(emptyList(), emptyList()))
    }
}
