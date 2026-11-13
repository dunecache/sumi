package com.sumi.domain.usage

import kotlinx.coroutines.flow.Flow

/**
 * Usage repository port (ARCHITECTURE.md §2). Implemented by `data`, consumed by `feature`.
 * Domain stays pure Kotlin; Android/Compose diffing lives in the caller.
 */
interface UsageRepository {

    /** Observed daily total for [dateKey]; emits null until data exists. */
    fun observeDailyTotal(dateKey: String): Flow<DailyUsage?>

    /** Observed per-app totals for [dateKey], sorted by duration desc. */
    fun observeDailyAppUsage(dateKey: String): Flow<List<DailyAppUsage>>

    /** Observed per-app totals across [dateKeys] (e.g. a trailing week). */
    fun observeDailyAppUsageRange(dateKeys: List<String>): Flow<List<DailyAppUsage>>

    /** Observed daily totals for one package, newest day first. */
    fun observeAppDailyUsage(packageName: String): Flow<List<DailyAppUsage>>

    /** Observed hourly buckets for [dateKey]. */
    fun observeHourlyUsage(dateKey: String): Flow<List<HourlyUsage>>

    /** Sessions overlapping [dateKey] (stored once; aggregation splits at midnight). */
    fun observeSessionsForDate(dateKey: String): Flow<List<UsageSession>>

    /** Observed sessions for one app on one day. */
    fun observeSessionsForPackageAndDate(packageName: String, dateKey: String): Flow<List<UsageSession>>

    /** Reconcile events from [fromUtcMs]..[toUtcMs] into Room (idempotent upserts). */
    suspend fun collectRecent(fromUtcMs: Long, toUtcMs: Long)

    /** One-time import of `UsageStats` aggregates; safe to call again (upserts). */
    suspend fun initialSyncFromUsageStats(fromUtcMs: Long, toUtcMs: Long)

    /** Recompute aggregates from stored sessions for all session-backed days. */
    suspend fun rebuildAggregates()
}

/** Source of raw platform events. Implemented in `platform`. */
interface UsageEventSource {
    suspend fun loadRawEvents(fromUtcMs: Long, toUtcMs: Long): List<RawUsageEvent>
}

/** Source of historical aggregates (`UsageStats`). Implemented in `platform`. */
interface UsageStatsSource {
    /** Daily per-app aggregates in [fromUtcMs]..[toUtcMs], keyed in [zone]. */
    suspend fun loadDailyAggregates(fromUtcMs: Long, toUtcMs: Long, zone: java.time.ZoneId): List<DailyAppAggregate>
}
