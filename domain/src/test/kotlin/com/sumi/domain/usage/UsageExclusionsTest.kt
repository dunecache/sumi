package com.sumi.domain.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rules for excluded packages (ARCHITECTURE.md §15). The device-specific set (self,
 * launcher, IME) is resolved by the platform layer; these tests pin the pure rules.
 */
class UsageExclusionsTest {

    private val device = setOf("com.sumi", "com.android.launcher3", "com.google.android.inputmethod.latin")

    @Test
    fun `system ui and the framework are always excluded`() {
        assertTrue(UsageExclusions.isExcluded(UsageExclusions.SYSTEM_UI, emptySet()))
        assertTrue(UsageExclusions.isExcluded(UsageExclusions.ANDROID_FRAMEWORK, emptySet()))
    }

    @Test
    fun `device specific packages are excluded`() {
        assertTrue(UsageExclusions.isExcluded("com.sumi", device))
        assertTrue(UsageExclusions.isExcluded("com.android.launcher3", device))
        assertTrue(UsageExclusions.isExcluded("com.google.android.inputmethod.latin", device))
    }

    @Test
    fun `ordinary apps are never excluded`() {
        assertFalse(UsageExclusions.isExcluded("com.instagram.android", device))
        assertFalse(UsageExclusions.isExcluded("com.sumi.notifications", device))
    }

    @Test
    fun `blank packages belong to screen events and are kept`() {
        assertFalse(UsageExclusions.isExcluded("", device))
    }

    @Test
    fun `filtering events drops excluded packages and keeps screen events`() {
        val events = listOf(
            RawUsageEvent("com.instagram.android", UsageEventType.FOREGROUND, 1_000L),
            RawUsageEvent("com.sumi", UsageEventType.FOREGROUND, 2_000L),
            RawUsageEvent("com.sumi", UsageEventType.BACKGROUND, 3_000L),
            RawUsageEvent("", UsageEventType.SCREEN_NON_INTERACTIVE, 4_000L),
            RawUsageEvent("com.android.launcher3", UsageEventType.FOREGROUND, 5_000L),
        )
        val kept = UsageExclusions.filterEvents(events, device)
        assertEquals(
            listOf(
                "com.instagram.android",
                "",
            ),
            kept.map { it.packageName },
        )
        assertEquals(UsageEventType.SCREEN_NON_INTERACTIVE, kept.last().type)
    }

    @Test
    fun `filtering aggregates drops excluded packages`() {
        val aggregates = listOf(
            DailyAppAggregate("2026-11-13", "com.instagram.android", 60_000L),
            DailyAppAggregate("2026-11-13", "com.sumi", 30_000L),
            DailyAppAggregate("2026-11-13", "com.android.systemui", 10_000L),
        )
        val kept = UsageExclusions.filterAggregates(aggregates, device)
        assertEquals(listOf("com.instagram.android"), kept.map { it.packageName })
    }

    @Test
    fun `filtering with an empty device set still drops the always excluded packages`() {
        val events = listOf(
            RawUsageEvent(UsageExclusions.SYSTEM_UI, UsageEventType.FOREGROUND, 1_000L),
            RawUsageEvent("com.example.app", UsageEventType.FOREGROUND, 2_000L),
        )
        val kept = UsageExclusions.filterEvents(events, emptySet())
        assertEquals(listOf("com.example.app"), kept.map { it.packageName })
    }

    @Test
    fun `filtering sessions drops excluded packages and keeps user apps`() {
        val sessions = listOf(
            UsageSession("com.instagram.android", 1_000L, 2_000L, false),
            UsageSession("com.sumi", 2_000L, 3_000L, false),
            UsageSession("com.android.launcher3", 3_000L, 4_000L, false),
        )
        val kept = UsageExclusions.filterSessions(sessions, device)
        assertEquals(listOf("com.instagram.android"), kept.map { it.packageName })
    }
}
