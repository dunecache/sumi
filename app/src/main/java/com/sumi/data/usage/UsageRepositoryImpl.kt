package com.sumi.data.usage

import com.sumi.core.common.AppDispatchers
import com.sumi.core.database.SumiDatabase
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.core.database.dao.CollectionStateDao
import com.sumi.core.database.dao.DailyAppUsageDao
import com.sumi.core.database.dao.DailyUsageDao
import com.sumi.core.database.dao.HourlyUsageDao
import com.sumi.core.database.dao.UsageSessionDao
import com.sumi.core.database.entity.Application
import com.sumi.core.database.entity.CollectionState
import com.sumi.core.database.entity.DailyAppUsage as EntityDailyAppUsage
import com.sumi.core.database.entity.DailyUsage as EntityDailyUsage
import com.sumi.core.database.entity.HourlyUsage as EntityHourlyUsage
import com.sumi.core.database.entity.UsageSession as EntitySession
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.usage.Aggregation
import com.sumi.domain.usage.DailyAppUsage
import com.sumi.domain.usage.DailyUsage
import com.sumi.domain.usage.HourlyUsage
import com.sumi.domain.usage.Normalization
import com.sumi.domain.usage.SessionReconstruction
import com.sumi.domain.usage.UsageEventSource
import com.sumi.domain.usage.UsageRepository
import com.sumi.domain.usage.UsageSession
import com.sumi.domain.usage.UsageStatsSource
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed [UsageRepository]. Collection is idempotent: upserts keyed by
 * `(packageName, startTime)` so a second run over the same window yields identical rows.
 * Multi-row reconciliation runs in one Room transaction.
 */
@Singleton
class UsageRepositoryImpl @Inject constructor(
    private val db: SumiDatabase,
    private val sessionDao: UsageSessionDao,
    private val dailyDao: DailyUsageDao,
    private val dailyAppDao: DailyAppUsageDao,
    private val hourlyDao: HourlyUsageDao,
    private val applicationDao: ApplicationDao,
    private val collectionDao: CollectionStateDao,
    private val eventSource: UsageEventSource,
    private val statsSource: UsageStatsSource,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
    private val dispatchers: AppDispatchers,
) : UsageRepository {

    override fun observeDailyTotal(dateKey: String): Flow<DailyUsage?> =
        dailyDao.observeAll().map { all ->
            all.firstOrNull { it.dateKey == dateKey }?.let {
                DailyUsage(it.dateKey, it.totalDurationMs, it.lastCollectedAt)
            }
        }

    override fun observeDailyAppUsage(dateKey: String): Flow<List<DailyAppUsage>> =
        observeDailyAppUsageEntities(dateKey).map { list ->
            list.map { DailyAppUsage(it.dateKey, it.packageName, it.durationMs, parseSource(it.source)) }
                .sortedByDescending { it.durationMs }
        }

    private fun observeDailyAppUsageEntities(dateKey: String): Flow<List<EntityDailyAppUsage>> =
        db.dailyAppUsageDao().observeByDateKey(dateKey)

    override fun observeHourlyUsage(dateKey: String): Flow<List<HourlyUsage>> = observeHourly(dateKey)

    /** Flow for hourly buckets backed by a one-shot query transformed to a value. */
    fun observeHourly(dateKey: String): Flow<List<HourlyUsage>> = kotlinx.coroutines.flow.flow {
        val rows = hourlyDao.getByDateKey(dateKey)
        emit(rows.map { HourlyUsage(it.dateKey, it.hour, it.packageName, it.durationMs) })
    }

    override fun observeSessionsForDate(dateKey: String): Flow<List<UsageSession>> =
        sessionDao.observeByDateKey(dateKey).map { list -> list.map { it.toDomain() } }

    override fun observeSessionsForPackageAndDate(packageName: String, dateKey: String): Flow<List<UsageSession>> =
        sessionDao.observeByDateKey(dateKey).map { list ->
            list.filter { it.packageName == packageName }.map { it.toDomain() }
        }

    /**
     * Incremental collection with overlap window: query from `cursor - overlap` to now,
     * reconcile in one transaction, then advance the cursor.
     */
    override suspend fun collectRecent(fromUtcMs: Long, toUtcMs: Long) =
        withContext(dispatchers.io) {
            val now = clock.nowUtcMs()
            val zone = timeZone.currentZone()
            val overlapMs = OVERLAP_MS
            val effectiveFrom = maxOf(fromUtcMs, (collectionDao.get()?.lastEventTimeCollected ?: fromUtcMs) - overlapMs)

            val raw = eventSource.loadRawEvents(effectiveFrom, toUtcMs)
            val normalized = Normalization.normalize(raw)
            val sessions = SessionReconstruction.reconstruct(normalized, nowUtcMs = toUtcMs)

            // Idempotent upsert keyed by (packageName, startTime).
            val entities = sessions.map { session ->
                EntitySession(
                    packageName = session.packageName,
                    startTime = session.startTimeUtcMs,
                    endTime = session.endTimeUtcMs,
                    durationMs = session.durationMs(toUtcMs),
                    dateKey = DateKey.of(session.startTimeUtcMs, zone),
                    isEstimated = session.isEstimated,
                )
            }
            val existing = sessionDao.getAllInRange(effectiveFrom, toUtcMs)
            val existingKeys = existing.mapTo(mutableSetOf()) { it.packageName to it.startTime }
            val merged = (existing + entities)
                .groupBy { it.packageName to it.startTime }
                .mapValues { (_, rows) ->
                    // Prefer closed rows: a truncated window can re-reconstruct an open row
                    // for a session already finalized in a previous run (R11: never resurrect).
                    rows.firstOrNull { it.endTime != null } ?: rows.first()
                }
                .values
                .toList()
                .sortedBy { it.startTime }

            val domainSessions = merged.map { it.toDomain() }
            val dailyApp = Aggregation.dailyAppUsage(domainSessions, zone, toUtcMs)
            val dailyTotals = Aggregation.dailyTotals(domainSessions, zone, toUtcMs)
            val hourly = Aggregation.hourlyUsage(domainSessions, zone, toUtcMs)

            db.withTransaction {
                sessionDao.upsertAll(merged)
                dailyAppDao.upsertAll(
                    dailyApp.map {
                        EntityDailyAppUsage(it.dateKey, it.packageName, it.durationMs, it.source.name)
                    },
                )
                dailyDao.upsertAll(
                    dailyTotals.map {
                        EntityDailyUsage(it.dateKey, it.totalDurationMs, it.lastCollectedAtUtcMs)
                    },
                )
                hourlyDao.upsertAll(
                    hourly.map {
                        EntityHourlyUsage(it.dateKey, it.hour, it.packageName, it.durationMs)
                    },
                )
                val prev = collectionDao.get()
                collectionDao.upsert(
                    (prev ?: CollectionState()).copy(
                        lastEventTimeCollected = maxOf(prev?.lastEventTimeCollected ?: 0L, toUtcMs),
                        earliestSessionTime = merged.minOfOrNull { it.startTime },
                        lastTimeZoneId = zone.id,
                        initialSyncCompleted = prev?.initialSyncCompleted == true,
                    ),
                )
                // Ensure Application rows exist for stats display; keep user fields intact.
                val known = applicationDao.getAll().mapTo(mutableSetOf()) { it.packageName }
                val missing = merged.map { it.packageName }.filter { it !in known }.distinct()
                if (missing.isNotEmpty()) {
                    applicationDao.upsertAll(missing.map { Application(it, it) })
                }
                Unit
            }
            Unit
        }

    override suspend fun initialSyncFromUsageStats(fromUtcMs: Long, toUtcMs: Long) =
        withContext(dispatchers.io) {
            val now = clock.nowUtcMs()
            val zone = timeZone.currentZone()
            val aggregates = statsSource.loadDailyAggregates(fromUtcMs, toUtcMs, zone)
            val sessionDays = sessionDao.getAllDateKeys().toSet()
            // Session-backed days replace aggregate-only rows.
            val filtered = aggregates.filter { it.dateKey !in sessionDays }
            if (filtered.isNotEmpty()) {
                dailyAppDao.upsertAll(
                    filtered.map {
                        EntityDailyAppUsage(it.dateKey, it.packageName, it.durationMs, "USAGE_STATS_AGGREGATE")
                    },
                )
            }
            collectionDao.upsert(
                (collectionDao.get() ?: CollectionState()).copy(
                    initialSyncCompleted = true,
                    lastTimeZoneId = zone.id,
                    lastEventTimeCollected = maxOf(collectionDao.get()?.lastEventTimeCollected ?: 0L, now),
                ),
            )
        }

    override suspend fun rebuildAggregates() =
        withContext(dispatchers.io) {
            val zone = timeZone.currentZone()
            val now = toUtcMsDefault()
            val all = sessionDao.getAll()
            val domain = all.map { it.toDomain() }
            val dailyApp = Aggregation.dailyAppUsage(domain, zone, now)
            val dailyTotals = Aggregation.dailyTotals(domain, zone, now)
            val hourly = Aggregation.hourlyUsage(domain, zone, now)
            db.withTransaction {
                dailyAppDao.upsertAll(
                    dailyApp.map {
                        EntityDailyAppUsage(it.dateKey, it.packageName, it.durationMs, it.source.name)
                    },
                )
                dailyDao.upsertAll(
                    dailyTotals.map {
                        EntityDailyUsage(it.dateKey, it.totalDurationMs, it.lastCollectedAtUtcMs)
                    },
                )
                hourlyDao.upsertAll(
                    hourly.map {
                        EntityHourlyUsage(it.dateKey, it.hour, it.packageName, it.durationMs)
                    },
                )
            }
        }

    private fun toUtcMsDefault(): Long = clock.nowUtcMs()

    private fun parseSource(name: String) =
        try {
            com.sumi.domain.usage.DailyAppUsageSource.valueOf(name)
        } catch (_: IllegalArgumentException) {
            com.sumi.domain.usage.DailyAppUsageSource.SESSIONS
        }

    private fun EntitySession.toDomain() = UsageSession(
        packageName = packageName,
        startTimeUtcMs = startTime,
        endTimeUtcMs = endTime,
        isEstimated = isEstimated,
    )

    private companion object {
        /** Reconciliation overlap must cover a still-open session from the previous cursor (6h). */
        const val OVERLAP_MS = 6L * 60L * 60L * 1000L
    }
}
