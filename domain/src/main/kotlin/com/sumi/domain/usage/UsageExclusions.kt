package com.sumi.domain.usage

/**
 * Excluded packages — device furniture, not the user's apps (ARCHITECTURE.md §15).
 *
 * Sumi measures time spent in *apps*. A few packages are always in the foreground by
 * the system and would otherwise be counted as usage:
 *  - **Sumi itself** — the user opening Sumi to look at their usage would inflate it.
 *  - **The default launcher** — time on the home screen is not app usage.
 *  - **The current input method** — the keyboard is not an app.
 *  - **System UI** — status bar, notification shade and recents overlay.
 *
 * The *device-specific* set (self, launcher, IME) is resolved by the platform layer;
 * this object owns the rules and the filtering so they stay unit-testable.
 *
 * Excluded packages never reach the database: their events are dropped before session
 * reconstruction, their aggregate rows are dropped on import, and rows stored before the
 * policy existed are pruned (and the affected daily totals recomputed from the
 * remaining sessions) by the repository.
 */
object UsageExclusions {

    /** The Android framework package. */
    const val ANDROID_FRAMEWORK = "android"

    /** System UI package (status bar, notification shade, recents overlay). */
    const val SYSTEM_UI = "com.android.systemui"

    /** Packages that are never user apps, on any device. */
    val ALWAYS_EXCLUDED: Set<String> = setOf(ANDROID_FRAMEWORK, SYSTEM_UI)

    /**
     * True when [packageName] must not be tracked.
     *
     * Blank package names belong to screen events (screen on/off) and are never
     * excluded — they carry session-closing semantics (R3).
     */
    fun isExcluded(packageName: String, deviceExcluded: Set<String>): Boolean =
        packageName.isNotBlank() && (packageName in ALWAYS_EXCLUDED || packageName in deviceExcluded)

    /**
     * Drops events of excluded packages. Screen events are always kept: they close
     * open sessions for every package (R3) and carry no package identity.
     */
    fun filterEvents(events: List<RawUsageEvent>, deviceExcluded: Set<String>): List<RawUsageEvent> =
        events.filterNot { isExcluded(it.packageName, deviceExcluded) }

    /** Drops per-app aggregate rows of excluded packages (import path). */
    fun filterAggregates(
        aggregates: List<DailyAppAggregate>,
        deviceExcluded: Set<String>,
    ): List<DailyAppAggregate> = aggregates.filterNot { isExcluded(it.packageName, deviceExcluded) }

    /**
     * Drops sessions of excluded packages (prune path).
     *
     * The remaining sessions are what the affected days' daily totals are recomputed
     * from, so the headline stays the union of the sessions that are actually kept.
     */
    fun filterSessions(
        sessions: List<UsageSession>,
        deviceExcluded: Set<String>,
    ): List<UsageSession> = sessions.filterNot { isExcluded(it.packageName, deviceExcluded) }
}
