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
import com.sumi.platform.applications.AppLabelResolver
import com.sumi.platform.applications.InstalledApp
import com.sumi.platform.applications.InstalledAppsSource
import com.sumi.platform.usage.ExcludedPackagesResolver
import com.sumi.domain.common.Clock
import com.sumi.domain.categories.DefaultPackageCategories
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.usage.Aggregation
import com.sumi.domain.usage.DailyAppUsage
import com.sumi.domain.usage.DailyUsage
import com.sumi.domain.usage.HourlyUsage
import com.sumi.domain.usage.Normalization
import com.sumi.domain.usage.RebootBoundarySource
import com.sumi.domain.usage.SessionReconstruction
import com.sumi.domain.usage.UsageEventSource
import com.sumi.domain.usage.UsageExclusions
import com.sumi.domain.usage.UsageRepository
import com.sumi.domain.usage.UsageSession
import com.sumi.domain.usage.UsageStatsSource
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
    private val rebootSource: RebootBoundarySource,
    private val clock: Clock,
    private val timeZone: TimeZoneProvider,
    private val dispatchers: AppDispatchers,
    private val labelResolver: AppLabelResolver,
    private val excludedPackages: ExcludedPackagesResolver,
    private val installedApps: InstalledAppsSource,
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

    override fun observeDailyAppUsageRange(dateKeys: List<String>): Flow<List<DailyAppUsage>> {
        if (dateKeys.isEmpty()) return flowOf(emptyList())
        return db.dailyAppUsageDao().observeByDateKeys(dateKeys).map { list ->
            list.map { DailyAppUsage(it.dateKey, it.packageName, it.durationMs, parseSource(it.source)) }
        }
    }

    override fun observeDailyTotalsRange(dateKeys: List<String>): Flow<List<DailyUsage>> {
        if (dateKeys.isEmpty()) return flowOf(emptyList())
        return dailyDao.observeByDateKeys(dateKeys).map { list ->
            list.map { DailyUsage(it.dateKey, it.totalDurationMs, it.lastCollectedAt) }
        }
    }

    override fun observeAppDailyUsage(packageName: String): Flow<List<DailyAppUsage>> =
        db.dailyAppUsageDao().observeByPackage(packageName).map { list ->
            list.map { DailyAppUsage(it.dateKey, it.packageName, it.durationMs, parseSource(it.source)) }
        }

    private fun observeDailyAppUsageEntities(dateKey: String): Flow<List<EntityDailyAppUsage>> =
        db.dailyAppUsageDao().observeByDateKey(dateKey)

    override fun observeHourlyUsage(dateKey: String): Flow<List<HourlyUsage>> = observeHourly(dateKey)

    /** Flow for hourly buckets backed by a one-shot query transformed to a value. */
    fun observeHourly(dateKey: String): Flow<List<HourlyUsage>> = kotlinx.coroutines.flow.flow {
        val rows = hourlyDao.getByDateKey(dateKey)
        emit(rows.map { HourlyUsage(it.dateKey, it.hour, it.packageName, it.durationMs) })
    }

    override fun observeHourlyUsageRange(dateKeys: List<String>): Flow<List<HourlyUsage>> {
        if (dateKeys.isEmpty()) return flowOf(emptyList())
        return hourlyDao.observeByDateKeys(dateKeys).map { rows ->
            rows.map { HourlyUsage(it.dateKey, it.hour, it.packageName, it.durationMs) }
        }
    }

    override fun observeSessionsForDate(dateKey: String): Flow<List<UsageSession>> =
        sessionDao.observeByDateKey(dateKey).map { list -> list.map { it.toDomain() } }

    override fun observeSessionsForDateKeys(dateKeys: List<String>): Flow<List<UsageSession>> {
        if (dateKeys.isEmpty()) return flowOf(emptyList())
        return sessionDao.observeByDateKeys(dateKeys).map { list -> list.map { it.toDomain() } }
    }

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
            val zone = timeZone.currentZone()
            val prevState = collectionDao.get()
            // §6: on zone change, re-key stored sessions and rebuild all aggregates.
            val zoneChanged = prevState != null && prevState.lastTimeZoneId.isNotEmpty() &&
                prevState.lastTimeZoneId != zone.id

            // §15: device furniture (Sumi itself, the launcher, the IME) is never tracked.
            // The set is resolved per run because the launcher and IME can change.
            val excluded = excludedPackages.resolve()
            pruneExcludedUsage(excluded)

            // Self-heal the library for packages that exist only in the imported history
            // (aggregate rows written before this fix, or from the 90-day import).
            // Steady state: one cheap query returning no rows.
            val missingLibraryRows = dailyAppDao.getPackagesMissingApplicationRows()
            if (missingLibraryRows.isNotEmpty()) ensureApplicationRows(missingLibraryRows)

            val raw = eventSource.loadRawEvents(
                maxOf(fromUtcMs, (prevState?.lastEventTimeCollected ?: fromUtcMs) - OVERLAP_MS),
                toUtcMs,
            )
            val normalized = Normalization.normalize(UsageExclusions.filterEvents(raw, excluded))
            val sessions = SessionReconstruction.reconstruct(
                normalized, nowUtcMs = toUtcMs, rebootUtcMs = rebootSource.rebootInstantsUtcMs())

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
            // Window-scoped normally; full history when the zone changed so re-key
            // covers every stored row (H6/§6).
            val existing = if (zoneChanged) {
                sessionDao.getAll()
            } else {
                sessionDao.getAllInRange(
                    maxOf(fromUtcMs, (prevState?.lastEventTimeCollected ?: fromUtcMs) - OVERLAP_MS),
                    toUtcMs,
                )
            }
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

            // R1 finalization: stale open rows (missing BACKGROUND) close at the next
            // session's start instead of growing with wall-clock forever (H3).
            val finalized = SessionReconstruction.closeOpenAtNextSessionStart(merged.map { it.toDomain() })
            val finalizedEntities = finalized.map { session ->
                EntitySession(
                    packageName = session.packageName,
                    startTime = session.startTimeUtcMs,
                    endTime = session.endTimeUtcMs,
                    durationMs = session.durationMs(toUtcMs),
                    dateKey = DateKey.of(session.startTimeUtcMs, zone),
                    isEstimated = session.isEstimated,
                )
            }

            val dailyApp = Aggregation.dailyAppUsage(finalized, zone, toUtcMs)
            val dailyTotals = Aggregation.dailyTotals(finalized, zone, toUtcMs)
            val hourly = Aggregation.hourlyUsage(finalized, zone, toUtcMs)

            // Hygiene (H5): delete aggregates for every touched day before re-inserting,
            // so corrected/pruned rows never linger (DATA_MODEL §5.6 reproducibility).
            val affectedDateKeys = (dailyApp.map { it.dateKey } + dailyTotals.map { it.dateKey } +
                hourly.map { it.dateKey }).toSortedSet()

            db.withTransaction {
                sessionDao.upsertAll(finalizedEntities)
                if (zoneChanged) {
                    dailyAppDao.deleteAll()
                    dailyDao.deleteAll()
                    hourlyDao.deleteAll()
                } else if (affectedDateKeys.isNotEmpty()) {
                    dailyAppDao.deleteByDateKeys(affectedDateKeys.toList())
                    dailyDao.deleteByDateKeys(affectedDateKeys.toList())
                    hourlyDao.deleteByDateKeys(affectedDateKeys.toList())
                }
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
                collectionDao.upsert(
                    (prevState ?: CollectionState()).copy(
                        lastEventTimeCollected = maxOf(prevState?.lastEventTimeCollected ?: 0L, toUtcMs),
                        earliestSessionTime = finalizedEntities.minOfOrNull { it.startTime },
                        lastTimeZoneId = zone.id,
                        initialSyncCompleted = prevState?.initialSyncCompleted == true,
                    ),
                )
                // Ensure Application rows exist for stats display; keep user fields intact.
                ensureApplicationRows(finalizedEntities.map { it.packageName })
                Unit
            }
            Unit
        }

    /**
     * Creates Application rows for packages seen in any collection path and upgrades
     * placeholder rows when their real label becomes resolvable. Keeps user fields
     * intact (categoryId, isHidden, isCategoryUserSet are never overwritten; a fresh
     * row gets a confident default from the bundled mapping or stays uncategorized).
     * Fresh rows stamp `firstSeenAt` (the quiet "New" marker) and `isSystemApp` from
     * [metadata] when discovery provides it; the usage path leaves them unknown (0/false).
     * Callers must run this inside their own transaction when atomicity matters.
     */
    private suspend fun ensureApplicationRows(
        packages: List<String>,
        metadata: Map<String, InstalledApp> = emptyMap(),
    ) {
        val distinct = packages.distinct()
        if (distinct.isEmpty()) return
        val existingByPackage = distinct.mapNotNull { pkg ->
            applicationDao.getByPackage(pkg)?.let { pkg to it }
        }.toMap()
        val now = clock.nowUtcMs()
        val rows = distinct.mapNotNull { pkg ->
            val existing = existingByPackage[pkg]
            val label = labelResolver.labelFor(pkg)?.takeIf { it != pkg }
            when {
                // Fresh rows: seed a confident default when one exists; unknown
                // packages stay uncategorized (never silently classified).
                existing == null -> Application(
                    pkg,
                    label ?: pkg,
                    categoryId = DefaultPackageCategories.forPackage(pkg),
                    isSystemApp = metadata[pkg]?.isSystemApp ?: false,
                    firstSeenAt = now,
                )
                // displayName == packageName marks the unresolved placeholder.
                label != null && existing.displayName == existing.packageName ->
                    existing.copy(displayName = label)
                else -> null
            }
        }
        if (rows.isNotEmpty()) applicationDao.upsertAll(rows)
    }

    /**
     * Removes stored usage of excluded packages (ARCHITECTURE.md §15).
     *
     * Rows collected before the exclusion policy existed would otherwise keep Sumi's own
     * time — and the launcher/IME time — in the totals forever. The affected days' daily
     * totals are recomputed from the remaining sessions, so the headline stays the union
     * of the sessions that are actually kept (DATA_MODEL §5 rule 8).
     *
     * Only packages in the excluded set are touched — never a user app. Idempotent: a
     * second run finds no rows for the excluded packages and does nothing.
     */
    private suspend fun pruneExcludedUsage(excluded: Set<String>) {
        if (excluded.isEmpty()) return
        val packages = excluded.toList()
        val affectedDays = (
            sessionDao.getDateKeysByPackages(packages) +
                dailyAppDao.getDateKeysByPackages(packages) +
                hourlyDao.getDateKeysByPackages(packages)
            ).toSortedSet()
        val hasApplicationRows = applicationDao.getByPackages(packages).isNotEmpty()
        if (affectedDays.isEmpty() && !hasApplicationRows) return
        val zone = timeZone.currentZone()
        val now = clock.nowUtcMs()
        db.withTransaction {
            sessionDao.deleteByPackages(packages)
            dailyAppDao.deleteByPackages(packages)
            hourlyDao.deleteByPackages(packages)
            // Library rows for device furniture would show up as zero-usage apps.
            if (hasApplicationRows) applicationDao.deleteByPackages(packages)
            affectedDays.forEach { day ->
                val remaining = UsageExclusions.filterSessions(
                    sessionDao.getByDateKey(day).map { it.toDomain() },
                    excluded,
                )
                val totals = Aggregation.dailyTotals(remaining, zone, now)
                dailyDao.deleteByDateKeys(listOf(day))
                if (totals.isNotEmpty()) {
                    dailyDao.upsertAll(
                        totals.map {
                            EntityDailyUsage(it.dateKey, it.totalDurationMs, it.lastCollectedAtUtcMs)
                        },
                    )
                }
            }
        }
    }

    /**
     * Auto-detect the installed apps (launcher query) and reconcile the `applications`
     * table with the live catalog:
     *  - missing launchable packages get fresh rows (usage-seen or not);
     *  - rows whose package vanished from the catalog are marked `isInstalled = 0`
     *    — never deleted, so the assignment and the history survive a reinstall;
     *  - reappearing packages flip back to `isInstalled = 1` (system flag refreshed).
     *
     * Excluded device furniture (Sumi itself, the launcher, the IME) is never added.
     * Existing rows are never overwritten beyond those flag flips — categoryId,
     * isCategoryUserSet and displayName of user-touched rows are safe.
     * Called at app start (bootstrap) and again on onboarding completion.
     *
     * An *empty* discovery result is treated as "the package manager told us nothing"
     * (failure, or no launcher apps resolvable), not as "the user uninstalled
     * everything" — no rows are marked uninstalled in that case.
     */
    override suspend fun backfillInstalledApps() =
        withContext(dispatchers.io) {
            val discovered = installedApps.launchableApps()
            if (discovered.isEmpty()) return@withContext
            val excluded = excludedPackages.resolve()
            val existing = applicationDao.getAll()
            val plan = LibraryMerge.plan(existing, discovered, excluded)
            db.withTransaction {
                // Reinstall restore + system-flag drift correction. User fields kept.
                if (plan.restoreRows.isNotEmpty()) applicationDao.upsertAll(plan.restoreRows)
                // Left the catalog: hide from the library, keep everything else.
                if (plan.markUninstalled.isNotEmpty()) {
                    applicationDao.setInstalled(plan.markUninstalled, installed = false)
                }
            }
            // Fresh rows (labels and first-seen stamps). Excluded packages and existing
            // rows are untouched — ensureApplicationRows creates only what is missing.
            ensureApplicationRows(plan.insertPackages, discovered.associateBy { it.packageName })
        }

    override suspend fun initialSyncFromUsageStats(fromUtcMs: Long, toUtcMs: Long) =
        withContext(dispatchers.io) {
            val now = clock.nowUtcMs()
            val zone = timeZone.currentZone()
            val excluded = excludedPackages.resolve()
            val aggregates = UsageExclusions.filterAggregates(
                statsSource.loadDailyAggregates(fromUtcMs, toUtcMs, zone),
                excluded,
            )
            val sessionDays = sessionDao.getAllDateKeys().toSet()
            // Session-backed days replace aggregate-only rows.
            val filtered = aggregates.filter { it.dateKey !in sessionDays }
            db.withTransaction {
                if (filtered.isNotEmpty()) {
                    dailyAppDao.upsertAll(
                        filtered.map {
                            EntityDailyAppUsage(it.dateKey, it.packageName, it.durationMs, "USAGE_STATS_AGGREGATE")
                        },
                    )
                }
                // The 90-day import must also create Application rows — otherwise apps with
                // history but no recent events (last events only survive ~7 days) are missing
                // from the Apps library and from Apps search.
                ensureApplicationRows(aggregates.map { it.packageName })
                collectionDao.upsert(
                    (collectionDao.get() ?: CollectionState()).copy(
                        initialSyncCompleted = true,
                        lastTimeZoneId = zone.id,
                        lastEventTimeCollected = maxOf(collectionDao.get()?.lastEventTimeCollected ?: 0L, now),
                    ),
                )
            }
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
                // Full rebuild: delete first so orphaned rows never linger (§5.6 reproducibility).
                dailyAppDao.deleteAll()
                dailyDao.deleteAll()
                hourlyDao.deleteAll()
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
