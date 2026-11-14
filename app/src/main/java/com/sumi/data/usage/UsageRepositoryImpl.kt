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
import com.sumi.core.database.entity.CURRENT_PIPELINE_VERSION
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
import com.sumi.domain.usage.DailyAppAggregate
import com.sumi.domain.usage.DailyAppUsage
import com.sumi.domain.usage.DailyAppUsageSource
import com.sumi.domain.usage.DailyUsage
import com.sumi.domain.usage.HourlyUsage
import com.sumi.domain.usage.Normalization
import com.sumi.domain.usage.RebootBoundarySource
import com.sumi.domain.usage.SessionReconstruction
import com.sumi.domain.usage.UsageEventSource
import com.sumi.domain.usage.UsageEventType
import com.sumi.domain.usage.UsageExclusions
import com.sumi.domain.usage.UsageRepository
import com.sumi.domain.usage.UsageSession
import com.sumi.domain.usage.UsageStatsSource
import androidx.room.withTransaction
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Room-backed [UsageRepository]. All collection paths (resume, worker, onboarding)
 * serialize through one [Mutex] and all multi-row reconciliation runs in a single Room
 * transaction, so concurrent triggers observe identical state.
 *
 * Collection is idempotent: sessions upsert keyed by `(packageName, startTime)` and the
 * touched days are re-derived from the full stored session set overlapping those days —
 * never from the window slice — so a second run over the same window yields identical
 * rows. Days backed by sessions are owned by the sessions (their aggregate-only import
 * rows are replaced); import-only days keep their `USAGE_STATS_AGGREGATE` rows.
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

    /** Serializes every collection path — the worker and a foreground resume can never interleave. */
    private val mutex = Mutex()

    /**
     * The excluded set of the last prune in this process. Pruning is one-way and the
     * set (Sumi + launcher + IME + the excluded HOME handlers) changes rarely, so the
     * three DISTINCT scans are skipped once a prune for this exact set ran — they
     * dominated repeated resumes. A process restart re-prunes once.
     */
    @Volatile
    private var lastPrunedExcluded: Set<String>? = null

    /**
     * Day totals, merged at read time with the imported per-app history.
     *
     * The 90-day `UsageStatsManager` import writes only `USAGE_STATS_AGGREGATE`
     * rows; the session-backed collection writes `SESSIONS` rows from the same source
     * and they replace the import rows for collected days. Stored rows always win;
     * days without a stored row derive from the per-app sum — so every total-driven
     * surface (overview, bars, comparisons, coverage) agrees with the category and
     * most-used surfaces instead of reading an empty table for imported days.
     * Derived rows carry a now-valued [DailyUsage.lastCollectedAtUtcMs]; it is never
     * rendered anywhere, so an import-time stamp is not fabricated.
     */
    private fun observeMergedDailyTotals(dateKeys: List<String>): Flow<List<DailyUsage>> {
        if (dateKeys.isEmpty()) return flowOf(emptyList())
        return combine(
            dailyDao.observeByDateKeys(dateKeys),
            dailyAppDao.observeByDateKeys(dateKeys),
        ) { stored, perApp ->
            val storedByKey = stored.associateBy { it.dateKey }
            val perAppByKey = perApp.groupBy { it.dateKey }
            dateKeys.mapNotNull { key ->
                storedByKey[key]?.let {
                    DailyUsage(it.dateKey, it.totalDurationMs, it.lastCollectedAt)
                } ?: perAppByKey[key]?.takeIf { it.isNotEmpty() }?.let { rows ->
                    DailyUsage(key, rows.sumOf { it.durationMs }, clock.nowUtcMs())
                }
            }
        }
    }

    override fun observeDailyTotal(dateKey: String): Flow<DailyUsage?> =
        observeMergedDailyTotals(listOf(dateKey)).map { totals ->
            totals.firstOrNull { it.dateKey == dateKey }
        }

    override fun observeDailyAppUsage(dateKey: String): Flow<List<DailyAppUsage>> =
        observeDailyAppUsageEntities(dateKey).map { list ->
            list.map { DailyAppUsage(it.dateKey, it.packageName, it.durationMs, parseSource(it.source)) }
                .sortedByDescending { it.durationMs }
        }

    override fun observeDailyAppUsageRange(dateKeys: List<String>): Flow<List<DailyAppUsage>> {
        if (dateKeys.isEmpty()) return flowOf(emptyList())
        return dailyAppDao.observeByDateKeys(dateKeys).map { list ->
            list.map { DailyAppUsage(it.dateKey, it.packageName, it.durationMs, parseSource(it.source)) }
        }
    }

    override fun observeDailyTotalsRange(dateKeys: List<String>): Flow<List<DailyUsage>> =
        observeMergedDailyTotals(dateKeys)

    override fun observeAppDailyUsage(packageName: String): Flow<List<DailyAppUsage>> =
        dailyAppDao.observeByPackage(packageName).map { list ->
            list.map { DailyAppUsage(it.dateKey, it.packageName, it.durationMs, parseSource(it.source)) }
        }

    private fun observeDailyAppUsageEntities(dateKey: String): Flow<List<EntityDailyAppUsage>> =
        dailyAppDao.observeByDateKey(dateKey)

    override fun observeHourlyUsage(dateKey: String): Flow<List<HourlyUsage>> =
        hourlyDao.observeByDateKeys(listOf(dateKey)).map { rows ->
            rows.map { HourlyUsage(it.dateKey, it.hour, it.packageName, it.durationMs) }
        }

    override fun observeHourlyUsageRange(dateKeys: List<String>): Flow<List<HourlyUsage>> {
        if (dateKeys.isEmpty()) return flowOf(emptyList())
        return hourlyDao.observeByDateKeys(dateKeys).map { rows ->
            rows.map { HourlyUsage(it.dateKey, it.hour, it.packageName, it.durationMs) }
        }
    }

    override fun observeSessionsForDate(dateKey: String): Flow<List<UsageSession>> {
        val zone = timeZone.currentZone()
        return sessionDao.observeOverlapping(
            DateKey.startOfDayUtcMs(dateKey, zone),
            DateKey.endOfDayUtcMsExclusive(dateKey, zone),
        ).map { list -> list.map { it.toDomain() } }
    }

    /**
     * Sessions overlapping any of [dateKeys]. Intended for contiguous key runs (all
     * callers use those); a non-contiguous list yields sessions for the whole
     * min..max span. Stored once; aggregation splits at midnight. The caller clips
     * to a day when rendering a partial-day view.
     */
    override fun observeSessionsForDateKeys(dateKeys: List<String>): Flow<List<UsageSession>> {
        if (dateKeys.isEmpty()) return flowOf(emptyList())
        val zone = timeZone.currentZone()
        val sorted = dateKeys.sorted()
        return sessionDao.observeOverlapping(
            DateKey.startOfDayUtcMs(sorted.first(), zone),
            DateKey.endOfDayUtcMsExclusive(sorted.last(), zone),
        ).map { list -> list.map { it.toDomain() } }
    }

    override fun observeSessionsForPackageAndDate(packageName: String, dateKey: String): Flow<List<UsageSession>> {
        val zone = timeZone.currentZone()
        return sessionDao.observeOverlappingForPackage(
            packageName,
            DateKey.startOfDayUtcMs(dateKey, zone),
            DateKey.endOfDayUtcMsExclusive(dateKey, zone),
        ).map { list -> list.map { it.toDomain() } }
    }

    /**
     * Incremental collection with overlap window: query from `min(from, cursor - overlap)`
     * (bounded below by the platform's event-retention floor) to now, reconcile in one
     * transaction, then advance the cursor.
     */
    override suspend fun collectRecent(fromUtcMs: Long, toUtcMs: Long) =
        withContext(dispatchers.io) {
            mutex.withLock { collectRecentLocked(fromUtcMs, toUtcMs) }
        }

    private suspend fun collectRecentLocked(fromUtcMs: Long, toUtcMs: Long) {
        val zone = timeZone.currentZone()
        val now = toUtcMs
        // The platform keeps usage events for roughly a week; nothing we collect can be older.
        val retentionFloor = now - EVENT_RETENTION_MS

        // §15: device furniture (Sumi itself, the launcher, the IME) is never tracked.
        // The set is resolved per run because the launcher and IME can change.
        val excluded = excludedPackages.resolve()
        pruneExcludedUsage(excluded)

        // Self-heal the library for packages that exist only in the imported history
        // (aggregate rows written before this fix, or from the 90-day import).
        // Steady state: one cheap query returning no rows.
        val missingLibraryRows = dailyAppDao.getPackagesMissingApplicationRows()

        // One-time repair for rows written by the pre-2026-11 pipeline rules. Wipes
        // what the events can reproduce and resets the cursor so the collection
        // below rebuilds the whole retained range.
        repairPipelineIfNeeded(now)

        val prevState = collectionDao.get()
        val cursor = prevState?.lastEventTimeCollected?.takeIf { it > 0L }
        // Still-open rows may predate the window; the events that close them (if any
        // exist within the retention floor) must be loaded, so the window widens to
        // the earliest open row, bounded by the floor.
        val earliestOpen = sessionDao.getOpen().minOfOrNull { it.startTime }?.coerceAtLeast(retentionFloor)
        val windowStart = when {
            // No cursor: first collection after install (or after the repair reset).
            cursor == null -> earliestOpen ?: retentionFloor
            else -> minOf(
                maxOf(retentionFloor, minOf(fromUtcMs, cursor - OVERLAP_MS)),
                earliestOpen ?: Long.MAX_VALUE,
            )
        }.coerceAtLeast(retentionFloor)

        // §6: on zone change, re-key stored sessions and rebuild session-backed aggregates.
        val zoneChanged = prevState != null && prevState.lastTimeZoneId.isNotEmpty() &&
            prevState.lastTimeZoneId != zone.id

        val raw = eventSource.loadRawEvents(windowStart, now)
        val normalized = Normalization.normalize(UsageExclusions.filterEvents(raw, excluded))
        val screenOffs = normalized.filter { it.type == UsageEventType.SCREEN_NON_INTERACTIVE }
            .map { it.timestampUtcMs }
        val reconstructed = SessionReconstruction.reconstruct(
            normalized, nowUtcMs = now, rebootUtcMs = rebootSource.rebootInstantsUtcMs())

        // Merge stored rows with the reconstruction: a real close wins over an
        // estimated close, which wins over an open row — a truncated window can
        // re-reconstruct an open row for a session finalized in a previous run.
        val existing = if (zoneChanged) {
            sessionDao.getAll().map { it.toDomain() }
        } else {
            sessionDao.getOverlapping(windowStart, now).map { it.toDomain() }
        }
        val merged = SessionReconstruction.mergeSessions(existing + reconstructed)
        val finalized = SessionReconstruction.finalizeOpenSessions(
            merged,
            screenOffUtcMs = screenOffs,
            staleBeforeUtcMs = retentionFloor,
            nowUtcMs = now,
        )

        // Idempotent upsert keyed by (packageName, startTime); re-stamp date keys.
        val finalizedEntities = finalized.map { session ->
            EntitySession(
                packageName = session.packageName,
                startTime = session.startTimeUtcMs,
                endTime = session.endTimeUtcMs,
                durationMs = session.durationMs(now),
                dateKey = DateKey.of(session.startTimeUtcMs, zone),
                isEstimated = session.isEstimated,
            )
        }

        // Affected days come from the window's time span, never from output rows —
        // a day corrected to zero is still rebuilt.
        val touchedDays = if (zoneChanged) {
            spannedDaysOf(finalized, zone, now)
        } else {
            DateKey.daysBetween(windowStart, now, zone)
        }

        // Resolve labels (PackageManager IPC) before opening the write transaction.
        val appPlans = planApplicationRows(missingLibraryRows + finalizedEntities.map { it.packageName })

        db.withTransaction {
            sessionDao.upsertAll(finalizedEntities)
            if (zoneChanged) {
                // Re-keyed/rebuilt below; sweep session-derived rows so nothing
                // stale survives outside the re-derived day set.
                dailyAppDao.deleteWhereSource(DailyAppUsageSource.SESSIONS.name)
                dailyDao.deleteWhereSource(DailyAppUsageSource.SESSIONS.name)
                hourlyDao.deleteAll()
            }
            replaceAggregates(touchedDays, zone, now)
            applyApplicationRows(appPlans)
            val fresh = collectionDao.get()
            collectionDao.upsert(
                (fresh ?: CollectionState()).copy(
                    lastEventTimeCollected = maxOf(fresh?.lastEventTimeCollected ?: 0L, now),
                    earliestSessionTime = sessionDao.earliestStart(),
                    lastTimeZoneId = zone.id,
                    initialSyncCompleted = fresh?.initialSyncCompleted == true,
                    pipelineVersion = fresh?.pipelineVersion ?: CURRENT_PIPELINE_VERSION,
                ),
            )
        }
    }

    /**
     * Creates Application rows for packages seen in any collection path and upgrades
     * placeholder rows when their real label becomes resolvable. User fields
     * (categoryId, isHidden, isCategoryUserSet) are never overwritten; a fresh row
     * gets a confident default from the bundled mapping or stays uncategorized.
     * Fresh rows stamp `firstSeenAt` (the quiet "New" marker) and `isSystemApp`
     * from [metadata] when discovery provides it; the usage path leaves them unknown
     * (false / now). Reads and label resolution happen here; writes happen in
     * [applyApplicationRows] inside the caller's transaction.
     */
    private suspend fun planApplicationRows(
        packages: List<String>,
        metadata: Map<String, InstalledApp> = emptyMap(),
    ): List<Application> {
        val distinct = packages.distinct()
        if (distinct.isEmpty()) return emptyList()
        val existingByPackage = applicationRowsByPackage(distinct).associateBy { it.packageName }
        val now = clock.nowUtcMs()
        return distinct.mapNotNull { pkg ->
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
    }

    private suspend fun applicationRowsByPackage(packages: List<String>): List<Application> =
        packages.distinct().chunked(IN_LIST_CHUNK).flatMap { applicationDao.getByPackages(it) }

    private suspend fun applyApplicationRows(rows: List<Application>) {
        if (rows.isNotEmpty()) applicationDao.upsertAll(rows)
    }

    /**
     * Removes stored usage of excluded packages (ARCHITECTURE.md §15), including the
     * always-excluded system packages (`UsageExclusions.ALWAYS_EXCLUDED`).
     *
     * Days whose sessions were all excluded drop their session-derived daily rows; a
     * day that keeps other apps is recomputed from the remaining overlap (sessions are
     * split at day boundaries, so spill-over rows are handled correctly). Import-only
     * days keep their `USAGE_STATS_AGGREGATE` total — it is an upper bound, but it is
     * the only history those days have. Idempotent: a second run finds no rows and
     * does nothing.
     */
    private suspend fun pruneExcludedUsage(deviceExcluded: Set<String>) {
        val excluded = deviceExcluded + UsageExclusions.ALWAYS_EXCLUDED
        if (excluded.isEmpty()) return
        if (excluded == lastPrunedExcluded) return // ran already in this process
        val packages = excluded.toList()
        val zone = timeZone.currentZone()
        val now = clock.nowUtcMs()
        // Session-backed days, computed from the rows' time spans — not from dateKey,
        // which is the start day only.
        val sessionDays = sessionDao.getByPackages(packages)
            .flatMap { session ->
                DateKey.daysBetween(
                    session.startTimeUtcMs,
                    (session.endTimeUtcMs ?: now).coerceAtLeast(session.startTimeUtcMs + 1),
                    zone,
                )
            }
        val affectedDays = (
            sessionDays +
                dailyAppDao.getDateKeysByPackages(packages) +
                hourlyDao.getDateKeysByPackages(packages)
            ).toSortedSet()
        val hasApplicationRows = applicationRowsByPackage(packages).isNotEmpty()
        // The DISTINCT scans are done; remember the result for the rest of this process
        // even when there was nothing to delete (the no-op path is the common one).
        lastPrunedExcluded = excluded
        if (affectedDays.isEmpty() && !hasApplicationRows) return
        db.withTransaction {
            sessionDao.deleteByPackages(packages)
            dailyAppDao.deleteByPackages(packages)
            hourlyDao.deleteByPackages(packages)
            // Library rows for device furniture would show up as zero-usage apps.
            if (hasApplicationRows) applicationDao.deleteByPackages(packages)
            for (day in affectedDays) {
                val remaining = UsageExclusions.filterSessions(
                    sessionDao.getOverlapping(
                        DateKey.startOfDayUtcMs(day, zone),
                        DateKey.endOfDayUtcMsExclusive(day, zone),
                    ).map { it.toDomain() },
                    excluded,
                )
                if (remaining.isEmpty()) {
                    // Import-only day: keep the aggregate upper bound, drop stale session rows.
                    dailyDao.deleteByDateKeysAndSource(listOf(day), DailyAppUsageSource.SESSIONS.name)
                } else {
                    dailyDao.deleteByDateKeys(listOf(day))
                    val totals = Aggregation.dailyTotals(remaining, zone, now)
                    dailyDao.upsertAll(
                        totals.map {
                            EntityDailyUsage(
                                it.dateKey,
                                it.totalDurationMs,
                                it.lastCollectedAtUtcMs,
                                DailyAppUsageSource.SESSIONS.name,
                            )
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
            mutex.withLock {
                val discovered = installedApps.launchableApps()
                if (discovered.isEmpty()) return@withLock
                val excluded = excludedPackages.resolve()
                val existing = applicationDao.getAll()
                val plan = LibraryMerge.plan(existing, discovered, excluded)
                // Fresh rows (labels and first-seen stamps) are planned outside the
                // transaction; excluded packages and existing rows are untouched.
                val rows = planApplicationRows(
                    plan.insertPackages,
                    discovered.associateBy { it.packageName },
                )
                db.withTransaction {
                    // Reinstall restore + system-flag drift correction. User fields kept.
                    if (plan.restoreRows.isNotEmpty()) applicationDao.upsertAll(plan.restoreRows)
                    // Left the catalog: hide from the library, keep everything else.
                    if (plan.markUninstalled.isNotEmpty()) {
                        plan.markUninstalled.chunked(IN_LIST_CHUNK)
                            .forEach { applicationDao.setInstalled(it, installed = false) }
                    }
                    applyApplicationRows(rows)
                }
            }
        }

    override suspend fun initialSyncFromUsageStats(fromUtcMs: Long, toUtcMs: Long) =
        withContext(dispatchers.io) {
            mutex.withLock { initialSyncLocked(fromUtcMs, toUtcMs) }
        }

    private suspend fun initialSyncLocked(fromUtcMs: Long, toUtcMs: Long) {
        val now = clock.nowUtcMs()
        val zone = timeZone.currentZone()
        // One-time repair first: freshly wiped retained range is re-imported below,
        // then re-collected by the caller.
        repairPipelineIfNeeded(now)
        val excluded = excludedPackages.resolve()
        val aggregates = UsageExclusions.filterAggregates(
            statsSource.loadDailyAggregates(fromUtcMs, toUtcMs, zone),
            excluded,
        )
        // The platform can emit duplicate or overlapping buckets on some OEMs; keep
        // one row per (day, package), durations summed (each bucket lies inside a
        // single calendar day, so the sum stays bounded by the day).
        val merged = aggregates
            .groupBy { it.dateKey to it.packageName }
            .map { (_, rows) ->
                DailyAppAggregate(rows.first().dateKey, rows.first().packageName, rows.sumOf { it.durationMs })
            }
        // Session-backed days replace their aggregate rows wholesale on collection.
        val importDays = DateKey.daysBetween(fromUtcMs, toUtcMs, zone)
        val sessionDays = if (importDays.isEmpty()) {
            emptySet()
        } else {
            spannedDaysOf(
                sessionDao.getOverlapping(
                    DateKey.startOfDayUtcMs(importDays.first(), zone),
                    DateKey.endOfDayUtcMsExclusive(importDays.last(), zone),
                ).map { it.toDomain() },
                zone,
                now,
            ).toSet()
        }
        val importable = merged.filter { it.dateKey !in sessionDays }
        val plans = planApplicationRows(merged.map { it.packageName })
        db.withTransaction {
            if (importable.isNotEmpty()) {
                val days = importable.map { it.dateKey }.distinct()
                dailyAppDao.deleteByDateKeysAndSource(days, DailyAppUsageSource.USAGE_STATS_AGGREGATE.name)
                dailyAppDao.upsertAll(
                    importable.map {
                        EntityDailyAppUsage(it.dateKey, it.packageName, it.durationMs, DailyAppUsageSource.USAGE_STATS_AGGREGATE.name)
                    },
                )
                // Approximate day totals from the per-app sums — an upper bound (the
                // per-day union of intervals is unknowable from aggregates). Days that
                // get collected are replaced by session-derived rows on the next run.
                val byDay = importable.groupBy { it.dateKey }
                dailyDao.deleteByDateKeysAndSource(byDay.keys.toList(), DailyAppUsageSource.USAGE_STATS_AGGREGATE.name)
                dailyDao.upsertAll(
                    byDay.map { (day, rows) ->
                        EntityDailyUsage(day, rows.sumOf { it.durationMs }, now, DailyAppUsageSource.USAGE_STATS_AGGREGATE.name)
                    },
                )
            }
            // The 90-day import must also create Application rows — otherwise apps with
            // history but no recent events (last events only survive ~7 days) are missing
            // from the Apps library and from Apps search.
            applyApplicationRows(plans)
            collectionDao.upsert(
                (collectionDao.get() ?: CollectionState()).copy(
                    initialSyncCompleted = true,
                    lastTimeZoneId = zone.id,
                    // Cursor intentionally not advanced here — collectRecent owns it.
                ),
            )
        }
    }

    /**
     * Recomputes the session-derived state: drops stale `SESSIONS` rows and hourly
     * buckets everywhere, then re-derives every day spanned by a stored session.
     * Days with only imported aggregates are left untouched — their events are gone,
     * so the import is the only history they have.
     */
    override suspend fun rebuildAggregates() =
        withContext(dispatchers.io) {
            mutex.withLock {
                val zone = timeZone.currentZone()
                val now = clock.nowUtcMs()
                val sessions = sessionDao.getAll().map { it.toDomain() }
                db.withTransaction {
                    dailyAppDao.deleteWhereSource(DailyAppUsageSource.SESSIONS.name)
                    dailyDao.deleteWhereSource(DailyAppUsageSource.SESSIONS.name)
                    hourlyDao.deleteAll()
                    replaceAggregates(spannedDaysOf(sessions, zone, now), zone, now)
                }
            }
        }

    /**
     * One-time repair for databases written by the pre-2026-11 pipeline: sessions were
     * cut at a spurious 30-minute gap, aggregates were derived from the collection
     * window instead of the stored history, and the imported history was wiped by
     * rebuilds. Rows the events can still reproduce (the platform retains roughly a
     * week of events) are deleted and the cursor is reset, so the next collection
     * rebuilds them with the corrected rules. `USAGE_STATS_AGGREGATE` rows are kept:
     * import-only days have no sessions to rebuild from. Rows older than the retention
     * floor keep whatever they held — their events are gone (documented in
     * ARCHITECTURE.md §9). Idempotent: a healthy database exits immediately.
     */
    private suspend fun repairPipelineIfNeeded(nowUtcMs: Long) {
        val state = collectionDao.get()
        if ((state?.pipelineVersion ?: CURRENT_PIPELINE_VERSION) >= CURRENT_PIPELINE_VERSION) return
        val zone = timeZone.currentZone()
        val floor = nowUtcMs - EVENT_RETENTION_MS
        val days = DateKey.daysBetween(floor, nowUtcMs, zone)
        db.withTransaction {
            sessionDao.deleteOverlapping(floor, nowUtcMs)
            if (days.isNotEmpty()) {
                dailyAppDao.deleteByDateKeysAndSource(days, DailyAppUsageSource.SESSIONS.name)
                dailyDao.deleteByDateKeysAndSource(days, DailyAppUsageSource.SESSIONS.name)
                hourlyDao.deleteByDateKeys(days)
            }
            collectionDao.upsert(
                (collectionDao.get() ?: CollectionState()).copy(
                    lastEventTimeCollected = 0L,
                    earliestSessionTime = null,
                    pipelineVersion = CURRENT_PIPELINE_VERSION,
                ),
            )
        }
    }

    /**
     * Re-derives the session-backed state for [dayKeys] from the stored sessions.
     *
     * Days WITH sessions are owned by the sessions: all rows for those days
     * (session- and aggregate-sourced) are deleted and re-derived — an aggregate row
     * can never out-vote a session. Days WITHOUT sessions keep their
     * `USAGE_STATS_AGGREGATE` rows (import-only history); stale session-derived rows
     * and hourly buckets there are dropped. Callers run this inside a transaction.
     */
    private suspend fun replaceAggregates(dayKeys: List<String>, zone: ZoneId, nowUtcMs: Long) {
        if (dayKeys.isEmpty()) return
        val sorted = dayKeys.sorted().distinct()
        val wanted = sorted.toSet()
        val from = DateKey.startOfDayUtcMs(sorted.first(), zone)
        val toExclusive = DateKey.endOfDayUtcMsExclusive(sorted.last(), zone)
        val sessions = sessionDao.getOverlapping(from, toExclusive).map { it.toDomain() }
        val spanned = spannedDaysOf(sessions, zone, nowUtcMs).toSet()

        // Coverage boundary: the first day Sumi ever saw events may be partially
        // covered. When an imported upper-bound row exists for that day, it wins —
        // sessions only exist from the first event on, and cannot reconcile the
        // pre-event remainder of the day. Days after the boundary are fully covered.
        val boundaryStart = minOf(
            collectionDao.get()?.earliestSessionTime ?: Long.MAX_VALUE,
            sessions.minOfOrNull { it.startTimeUtcMs } ?: Long.MAX_VALUE,
        )
        val boundaryKey = boundaryStart.takeIf { it != Long.MAX_VALUE }?.let { DateKey.of(it, zone) }
        val keepBoundaryAggregate = boundaryKey != null && boundaryKey in wanted &&
            dailyAppDao.getByDateKey(boundaryKey).any {
                it.source == DailyAppUsageSource.USAGE_STATS_AGGREGATE.name
            }

        val owned = sorted.filter { it in spanned && it != boundaryKey.takeIf { keepBoundaryAggregate } }
        val orphans = sorted.filter { it !in owned }
        if (owned.isNotEmpty()) {
            dailyAppDao.deleteByDateKeys(owned)
            dailyDao.deleteByDateKeys(owned)
            hourlyDao.deleteByDateKeys(owned)
        }
        if (orphans.isNotEmpty()) {
            dailyAppDao.deleteByDateKeysAndSource(orphans, DailyAppUsageSource.SESSIONS.name)
            dailyDao.deleteByDateKeysAndSource(orphans, DailyAppUsageSource.SESSIONS.name)
            hourlyDao.deleteByDateKeys(orphans)
        }
        val excludeDay = boundaryKey.takeIf { keepBoundaryAggregate }
        val dailyApp = Aggregation.dailyAppUsage(sessions, zone, nowUtcMs)
            .filter { it.dateKey in wanted && it.dateKey != excludeDay }
        val dailyTotals = Aggregation.dailyTotals(sessions, zone, nowUtcMs, lastCollectedAtUtcMs = nowUtcMs)
            .filter { it.dateKey in wanted && it.dateKey != excludeDay }
        val hourly = Aggregation.hourlyUsage(sessions, zone, nowUtcMs)
            .filter { it.dateKey in wanted && it.dateKey != excludeDay }
        dailyAppDao.upsertAll(
            dailyApp.map { EntityDailyAppUsage(it.dateKey, it.packageName, it.durationMs, it.source.name) },
        )
        dailyDao.upsertAll(
            dailyTotals.map {
                EntityDailyUsage(it.dateKey, it.totalDurationMs, it.lastCollectedAtUtcMs, DailyAppUsageSource.SESSIONS.name)
            },
        )
        hourlyDao.upsertAll(
            hourly.map { EntityHourlyUsage(it.dateKey, it.hour, it.packageName, it.durationMs) },
        )
    }

    private fun spannedDaysOf(sessions: List<UsageSession>, zone: ZoneId, nowUtcMs: Long): List<String> =
        sessions
            .flatMap { session ->
                DateKey.daysBetween(
                    session.startTimeUtcMs,
                    (session.endTimeUtcMs ?: nowUtcMs).coerceAtLeast(session.startTimeUtcMs + 1),
                    zone,
                )
            }
            .toSortedSet()
            .toList()

    private fun parseSource(name: String) =
        try {
            DailyAppUsageSource.valueOf(name)
        } catch (_: IllegalArgumentException) {
            DailyAppUsageSource.SESSIONS
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

        /** The platform keeps usage events for roughly a week; nothing collected can be older. */
        const val EVENT_RETENTION_MS = 7L * 24L * 60L * 60L * 1000L

        /** Chunk size for SQLite variable limits on older devices. */
        const val IN_LIST_CHUNK = 500
    }
}
