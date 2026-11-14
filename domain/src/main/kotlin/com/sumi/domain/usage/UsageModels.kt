package com.sumi.domain.usage

/**
 * Pure-Kotlin usage models. No Android types leak in here so the whole engine is JVM-testable
 * (see `ARCHITECTURE.md` §2, `AGENTS.md` §2.6).
 */

enum class UsageEventType {
    FOREGROUND,
    BACKGROUND,
    SCREEN_INTERACTIVE,
    SCREEN_NON_INTERACTIVE,
    /** API 29+: the runtime shut down — activities/services are gone (mapped from `DEVICE_SHUTDOWN`). */
    DEVICE_SHUTDOWN,
    /** API 29+: the runtime started — events between shutdown and startup are lost (mapped from `DEVICE_STARTUP`). */
    DEVICE_STARTUP,
}

/** Normalized internal event. Platform adapters map Android `UsageEvents` constants to this. */
data class RawUsageEvent(
    /** Package name; empty for screen events (no app foreground). */
    val packageName: String,
    val type: UsageEventType,
    /** UTC epoch ms. */
    val timestampUtcMs: Long,
)

/** Reconstructed foreground interval for one packaged app. */
data class UsageSession(
    val packageName: String,
    /** UTC epoch ms. */
    val startTimeUtcMs: Long,
    /** UTC epoch ms; `null` = open current session (finalized on next reconcile). */
    val endTimeUtcMs: Long?,
    /** True when reconstructed via a fallback rule (implicit close, reboot, gap, implausible length). */
    val isEstimated: Boolean,
) {
    /** Duration in ms; open sessions are measured up to [nowUtcMs] for display. */
    fun durationMs(nowUtcMs: Long): Long {
        val end = endTimeUtcMs ?: nowUtcMs
        return (end - startTimeUtcMs).coerceAtLeast(0L)
    }

    /** Zero-duration placeholder kept so storage can store an open session row. */
    fun storedDurationMs(nowUtcMs: Long): Long = durationMs(nowUtcMs)
}

/** Per-app per-day total. */
data class DailyAppUsage(
    val dateKey: String,
    val packageName: String,
    val durationMs: Long,
    val source: DailyAppUsageSource = DailyAppUsageSource.SESSIONS,
)

enum class DailyAppUsageSource {
    SESSIONS,
    USAGE_STATS_AGGREGATE,
}

data class DailyUsage(
    val dateKey: String,
    /** Union of all session intervals overlapping the day (multi-window safe, R8). */
    val totalDurationMs: Long,
    val lastCollectedAtUtcMs: Long,
)

data class HourlyUsage(
    val dateKey: String,
    val hour: Int,
    val packageName: String,
    val durationMs: Long,
)

/** Lightweight holder for historical aggregates imported from `UsageStats`. */
data class DailyAppAggregate(
    val dateKey: String,
    val packageName: String,
    val durationMs: Long,
)
