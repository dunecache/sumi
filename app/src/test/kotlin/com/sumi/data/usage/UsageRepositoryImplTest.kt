package com.sumi.data.usage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sumi.core.common.AppDispatchers
import com.sumi.core.database.SumiDatabase
import com.sumi.core.database.entity.CURRENT_PIPELINE_VERSION
import com.sumi.core.database.entity.UsageSession as SessionRow
import com.sumi.domain.common.Clock
import com.sumi.domain.common.DateKey
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.usage.DailyAppAggregate
import com.sumi.domain.usage.RawUsageEvent
import com.sumi.domain.usage.RebootBoundarySource
import com.sumi.domain.usage.SessionReconstruction
import com.sumi.domain.usage.UsageEventSource
import com.sumi.domain.usage.UsageEventType
import com.sumi.domain.usage.UsageStatsSource
import com.sumi.platform.applications.AppLabelResolver
import com.sumi.platform.applications.InstalledApp
import com.sumi.platform.applications.InstalledAppsSource
import com.sumi.platform.usage.ExcludedPackagesResolver
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Repository-level tests for the collection pipeline (window math, merge rules, repair,
 * aggregate replacement, exclusions) against an in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UsageRepositoryImplTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    // 2026-11-14 is a Saturday; fixed instants per test keep everything deterministic.
    private fun at(day: LocalDate, hour: Int, minute: Int = 0): Long =
        day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private class MutableClock(@Volatile var now: Long) : Clock {
        override fun nowUtcMs(): Long = now
    }

    private class FakeEventSource : UsageEventSource {
        var events: List<RawUsageEvent> = emptyList()
        val requestedWindows = mutableListOf<Pair<Long, Long>>()

        override suspend fun loadRawEvents(fromUtcMs: Long, toUtcMs: Long): List<RawUsageEvent> {
            requestedWindows += fromUtcMs to toUtcMs
            return events.filter { it.timestampUtcMs in fromUtcMs..toUtcMs }
        }
    }

    private class FakeStatsSource : UsageStatsSource {
        var rows: List<DailyAppAggregate> = emptyList()

        override suspend fun loadDailyAggregates(
            fromUtcMs: Long,
            toUtcMs: Long,
            zone: ZoneId,
        ): List<DailyAppAggregate> = rows
    }

    private object NoReboots : RebootBoundarySource {
        override fun rebootInstantsUtcMs(): List<Long> = emptyList()
    }

    private class FakeExclusions(private val device: Set<String> = emptySet()) : ExcludedPackagesResolver {
        override suspend fun resolve(): Set<String> = device
    }

    private class FakeLabelResolver : AppLabelResolver {
        override suspend fun labelFor(packageName: String): String? = null
    }

    private object NoInstalledApps : InstalledAppsSource {
        override suspend fun launchableApps(): List<InstalledApp> = emptyList()
    }

    private lateinit var db: SumiDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SumiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun repo(
        clock: MutableClock,
        events: FakeEventSource,
        stats: FakeStatsSource = FakeStatsSource(),
        exclusions: ExcludedPackagesResolver = FakeExclusions(),
    ) = UsageRepositoryImpl(
        db = db,
        sessionDao = db.usageSessionDao(),
        dailyDao = db.dailyUsageDao(),
        dailyAppDao = db.dailyAppUsageDao(),
        hourlyDao = db.hourlyUsageDao(),
        applicationDao = db.applicationDao(),
        collectionDao = db.collectionStateDao(),
        eventSource = events,
        statsSource = stats,
        rebootSource = NoReboots,
        clock = clock,
        timeZone = TimeZoneProvider { zone },
        dispatchers = AppDispatchers(Dispatchers.IO, Dispatchers.Unconfined, Dispatchers.Unconfined),
        labelResolver = FakeLabelResolver(),
        excludedPackages = exclusions,
        installedApps = NoInstalledApps,
    )

    @Test
    fun `windowed second collection keeps morning usage`() = runTest {
        val day = LocalDate.of(2026, 11, 14)
        val key = DateKey.of(at(day, 12, 0), zone)
        val events = FakeEventSource().apply {
            events = listOf(
                RawUsageEvent("com.a", UsageEventType.FOREGROUND, at(day, 6, 30)),
                RawUsageEvent("com.a", UsageEventType.BACKGROUND, at(day, 7, 15)),
                RawUsageEvent("com.b", UsageEventType.FOREGROUND, at(day, 8, 0)),
                RawUsageEvent("com.b", UsageEventType.BACKGROUND, at(day, 8, 30)),
            )
        }
        val clock = MutableClock(at(day, 10, 0))
        val repository = repo(clock, events)

        repository.collectRecent(at(day, 4, 0), at(day, 10, 0))

        // Foreground resume: from = now - 6h, which excludes the morning sessions.
        clock.now = at(day, 16, 0)
        repository.collectRecent(at(day, 10, 0), at(day, 16, 0))

        val totalsByKey = db.dailyUsageDao().observeByDateKeys(listOf(key)).first().associateBy { it.dateKey }
        assertEquals(75L * 60_000L, totalsByKey.getValue(key).totalDurationMs)
        val perApp = db.dailyAppUsageDao().observeByDateKey(key).first()
        assertEquals(45L * 60_000L, perApp.first { it.packageName == "com.a" }.durationMs)
        assertEquals(30L * 60_000L, perApp.first { it.packageName == "com.b" }.durationMs)
        val hourlySum = db.hourlyUsageDao().observeByDateKeys(listOf(key)).first()
            .filter { it.packageName == "com.a" }
            .sumOf { it.durationMs }
        assertEquals(45L * 60_000L, hourlySum)
    }

    @Test
    fun `foreground collection after a long absence reaches back past the previous cursor`() = runTest {
        val day1 = LocalDate.of(2026, 11, 13)
        val day2 = LocalDate.of(2026, 11, 14)
        val events = FakeEventSource().apply {
            events = listOf(
                RawUsageEvent("com.a", UsageEventType.FOREGROUND, at(day1, 8, 0)),
                RawUsageEvent("com.a", UsageEventType.BACKGROUND, at(day1, 9, 0)),
            )
        }
        val clock = MutableClock(at(day1, 9, 0))
        val repository = repo(clock, events)

        repository.collectRecent(at(day1, 3, 0), at(day1, 9, 0))
        assertEquals(at(day1, 9, 0), db.collectionStateDao().get()!!.lastEventTimeCollected)

        // Next open: an app session from 23:00 — outside the 6h foreground window.
        clock.now = at(day2, 6, 0)
        events.events = events.events + listOf(
            RawUsageEvent("com.b", UsageEventType.FOREGROUND, at(day1, 23, 0)),
            RawUsageEvent("com.b", UsageEventType.BACKGROUND, at(day2, 0, 30)),
        )
        repository.collectRecent(at(day2, 0, 0), at(day2, 6, 0))

        // Window started at cursor - overlap, not at the caller's from.
        assertEquals(at(day1, 3, 0), events.requestedWindows.last().first)
        val stored = db.usageSessionDao().getAll()
        assertTrue(stored.any { it.packageName == "com.b" && it.startTime == at(day1, 23, 0) && it.endTime == at(day2, 0, 30) })
    }

    @Test
    fun `estimated close from a previous run is replaced by the real background`() = runTest {
        val day = LocalDate.of(2026, 11, 14)
        val events = FakeEventSource().apply {
            // The platform window for run 1 does not yet contain com.a's BACKGROUND.
            events = listOf(
                RawUsageEvent("com.a", UsageEventType.FOREGROUND, at(day, 9, 0)),
                RawUsageEvent("com.b", UsageEventType.FOREGROUND, at(day, 9, 30)),
                RawUsageEvent("com.b", UsageEventType.BACKGROUND, at(day, 9, 45)),
            )
        }
        val clock = MutableClock(at(day, 10, 0))
        val repository = repo(clock, events)

        repository.collectRecent(at(day, 4, 0), at(day, 10, 0))

        // com.a was left open; the next session's start finalized it as estimated.
        val storedAfterFirst = db.usageSessionDao().getAll().first { it.packageName == "com.a" }
        assertEquals(at(day, 9, 30), storedAfterFirst.endTime)
        assertTrue(storedAfterFirst.isEstimated)

        // Run 2: the BACKGROUND is now visible; it must replace the estimated close.
        clock.now = at(day, 14, 0)
        events.events = events.events + RawUsageEvent("com.a", UsageEventType.BACKGROUND, at(day, 11, 0))
        repository.collectRecent(at(day, 8, 0), at(day, 14, 0))

        val storedAfterSecond = db.usageSessionDao().getAll().first { it.packageName == "com.a" }
        assertEquals(at(day, 11, 0), storedAfterSecond.endTime)
        assertFalse(storedAfterSecond.isEstimated)
    }

    @Test
    fun `rebuild keeps import-only aggregate days`() = runTest {
        val today = LocalDate.of(2026, 11, 14)
        val importDay = today.minusDays(10)
        val importKey = DateKey.of(at(importDay, 12, 0), zone)
        val stats = FakeStatsSource().apply {
            rows = listOf(DailyAppAggregate(importKey, "com.x", 90L * 60_000L))
        }
        val clock = MutableClock(at(today, 12, 0))
        val repository = repo(clock, FakeEventSource(), stats)

        repository.initialSyncFromUsageStats(at(today.minusDays(90), 0, 0), at(today, 12, 0))
        repository.rebuildAggregates()

        val perApp = db.dailyAppUsageDao().observeByDateKey(importKey).first()
        assertEquals(90L * 60_000L, perApp.single { it.packageName == "com.x" }.durationMs)
        assertEquals("USAGE_STATS_AGGREGATE", perApp.single { it.packageName == "com.x" }.source)
        val total = db.dailyUsageDao().observeByDateKeys(listOf(importKey)).first().single()
        assertEquals(90L * 60_000L, total.totalDurationMs)
    }

    @Test
    fun `coverage boundary day keeps its imported upper bound`() = runTest {
        val today = LocalDate.of(2026, 11, 14)
        val boundary = today.minusDays(2) // 2026-11-12 — the first day Sumi ever sees
        val boundaryKey = DateKey.of(at(boundary, 12, 0), zone)
        val olderKey = DateKey.of(at(today.minusDays(10), 12, 0), zone)
        val stats = FakeStatsSource().apply {
            rows = listOf(
                DailyAppAggregate(olderKey, "com.y", 60L * 60_000L),
                DailyAppAggregate(boundaryKey, "com.x", 3L * 60L * 60_000L),
            )
        }
        val events = FakeEventSource().apply {
            events = listOf(
                RawUsageEvent("com.a", UsageEventType.FOREGROUND, at(boundary, 9, 0)),
                RawUsageEvent("com.a", UsageEventType.BACKGROUND, at(boundary, 10, 0)),
            )
        }
        val clock = MutableClock(at(today, 12, 0))
        val repository = repo(clock, events, stats)

        repository.initialSyncFromUsageStats(at(today.minusDays(90), 0, 0), at(today, 12, 0))
        // Import writes the upper bound for the boundary day.
        assertEquals(
            3L * 60L * 60_000L,
            db.dailyUsageDao().observeByDateKeys(listOf(boundaryKey)).first().single().totalDurationMs,
        )

        repository.collectRecent(at(today, 6, 0), at(today, 12, 0))

        // The boundary day keeps its aggregate upper bound; sessions cannot reconcile
        // the pre-event remainder of the day.
        val total = db.dailyUsageDao().observeByDateKeys(listOf(boundaryKey)).first().single()
        assertEquals(3L * 60L * 60_000L, total.totalDurationMs)
        val perApp = db.dailyAppUsageDao().observeByDateKey(boundaryKey).first()
        assertEquals("USAGE_STATS_AGGREGATE", perApp.single { it.packageName == "com.x" }.source)
        assertTrue(perApp.none { it.packageName == "com.a" })
        // Days after the boundary are fully covered: the older import-only day is kept too.
        val older = db.dailyAppUsageDao().observeByDateKey(olderKey).first()
        assertEquals("USAGE_STATS_AGGREGATE", older.single().source)
    }

    @Test
    fun `pipeline repair rebuilds retained-range sessions and keeps import-only days`() = runTest {
        val today = LocalDate.of(2026, 11, 14)
        val importKeyRetained = DateKey.of(at(today.minusDays(2), 12, 0), zone)
        val importKeyOld = DateKey.of(at(today.minusDays(10), 12, 0), zone)
        val stats = FakeStatsSource().apply {
            rows = listOf(
                DailyAppAggregate(importKeyRetained, "com.x", 10L * 60_000L),
                DailyAppAggregate(importKeyOld, "com.y", 20L * 60_000L),
            )
        }
        val events = FakeEventSource().apply {
            events = listOf(
                RawUsageEvent("com.a", UsageEventType.FOREGROUND, at(today.minusDays(1), 12, 0)),
                RawUsageEvent("com.a", UsageEventType.BACKGROUND, at(today.minusDays(1), 13, 0)),
            )
        }
        val clock = MutableClock(at(today, 12, 0))
        val repository = repo(clock, events, stats)

        repository.initialSyncFromUsageStats(at(today.minusDays(90), 0, 0), at(today, 12, 0))
        repository.collectRecent(at(today, 6, 0), at(today, 12, 0))
        assertTrue(db.usageSessionDao().getAll().isNotEmpty())

        // Simulate a legacy database: pipeline version below current.
        db.collectionStateDao().upsert(db.collectionStateDao().get()!!.copy(pipelineVersion = 0))

        clock.now = at(today, 13, 0)
        repository.collectRecent(at(today, 7, 0), at(today, 13, 0))

        // Retained-range sessions were rebuilt from the full event history.
        val rebuilt = db.usageSessionDao().getAll()
        assertTrue(
            rebuilt.any {
                it.packageName == "com.a" && it.startTime == at(today.minusDays(1), 12, 0)
            },
        )
        assertEquals(at(today, 13, 0) - EVENT_RETENTION_MS, events.requestedWindows.last().first)
        assertEquals(CURRENT_PIPELINE_VERSION, db.collectionStateDao().get()!!.pipelineVersion)
        // Import-only days survive the repair, both inside and beyond the retention floor.
        assertEquals(
            2,
            db.dailyAppUsageDao().observeByDateKeys(listOf(importKeyRetained, importKeyOld)).first().size,
        )
        assertEquals(
            2,
            db.dailyUsageDao().observeByDateKeys(listOf(importKeyRetained, importKeyOld)).first().size,
        )
    }

    @Test
    fun `open row stored before the window closes when its background event is seen`() = runTest {
        val day1 = LocalDate.of(2026, 11, 13)
        val day2 = LocalDate.of(2026, 11, 14)
        db.usageSessionDao().upsertAll(
            listOf(
                SessionRow(
                    packageName = "com.a",
                    startTime = at(day1, 22, 0),
                    endTime = null,
                    durationMs = 60_000L,
                    dateKey = DateKey.of(at(day1, 22, 0), zone),
                    isEstimated = false,
                ),
            ),
        )
        val events = FakeEventSource().apply {
            events = listOf(
                RawUsageEvent("com.a", UsageEventType.FOREGROUND, at(day1, 22, 0)),
                RawUsageEvent("com.a", UsageEventType.BACKGROUND, at(day2, 1, 0)),
            )
        }
        val clock = MutableClock(at(day2, 6, 0))
        val repository = repo(clock, events)

        repository.collectRecent(at(day2, 0, 0), at(day2, 6, 0))

        val stored = db.usageSessionDao().getAll().single { it.packageName == "com.a" }
        assertEquals(at(day2, 1, 0), stored.endTime)
        assertFalse(stored.isEstimated)
    }

    @Test
    fun `open row older than event retention closes at a bounded plausible end`() = runTest {
        val today = LocalDate.of(2026, 11, 14)
        val old = at(today.minusDays(25), 9, 0)
        db.usageSessionDao().upsertAll(
            listOf(
                SessionRow(
                    packageName = "com.a",
                    startTime = old,
                    endTime = null,
                    durationMs = 60_000L,
                    dateKey = DateKey.of(old, zone),
                    isEstimated = false,
                ),
            ),
        )
        val clock = MutableClock(at(today, 12, 0))
        val repository = repo(clock, FakeEventSource())

        repository.collectRecent(at(today, 6, 0), at(today, 12, 0))

        val stored = db.usageSessionDao().getAll().single { it.packageName == "com.a" }
        assertEquals(old + SessionReconstruction.MAX_PLAUSIBLE_MS, stored.endTime)
        assertTrue(stored.isEstimated)
    }

    @Test
    fun `always-excluded packages never reach storage and legacy rows are pruned`() = runTest {
        val day = LocalDate.of(2026, 11, 14)
        db.usageSessionDao().upsertAll(
            listOf(
                SessionRow(
                    packageName = "android",
                    startTime = at(day, 7, 0),
                    endTime = at(day, 8, 0),
                    durationMs = 3_600_000L,
                    dateKey = DateKey.of(at(day, 7, 0), zone),
                    isEstimated = false,
                ),
            ),
        )
        val events = FakeEventSource().apply {
            events = listOf(
                RawUsageEvent("android", UsageEventType.FOREGROUND, at(day, 9, 0)),
                RawUsageEvent("com.android.systemui", UsageEventType.FOREGROUND, at(day, 9, 10)),
                RawUsageEvent("com.a", UsageEventType.FOREGROUND, at(day, 9, 30)),
                RawUsageEvent("com.a", UsageEventType.BACKGROUND, at(day, 10, 0)),
            )
        }
        val clock = MutableClock(at(day, 12, 0))
        val repository = repo(clock, events)

        repository.collectRecent(at(day, 6, 0), at(day, 12, 0))

        val rows = db.usageSessionDao().getAll()
        assertTrue(rows.none { it.packageName == "android" })
        assertTrue(rows.none { it.packageName == "com.android.systemui" })
        assertTrue(rows.any { it.packageName == "com.a" })
    }

    @Test
    fun `midnight-crossing session is returned for both days and split in totals`() = runTest {
        val d1 = LocalDate.of(2026, 11, 6)
        val d2 = d1.plusDays(1)
        val k1 = DateKey.of(at(d1, 12, 0), zone)
        val k2 = DateKey.of(at(d2, 2, 0), zone)
        val events = FakeEventSource().apply {
            events = listOf(
                RawUsageEvent("com.a", UsageEventType.FOREGROUND, at(d1, 23, 50)),
                RawUsageEvent("com.a", UsageEventType.BACKGROUND, at(d2, 0, 20)),
            )
        }
        val clock = MutableClock(at(d2, 1, 0))
        val repository = repo(clock, events)

        repository.collectRecent(at(d1, 12, 0), at(d2, 1, 0))

        assertEquals(1, repository.observeSessionsForDate(k1).first().size)
        assertEquals(1, repository.observeSessionsForDate(k2).first().size)
        val totals = db.dailyUsageDao().observeByDateKeys(listOf(k1, k2)).first().associateBy { it.dateKey }
        assertEquals(10L * 60_000L, totals.getValue(k1).totalDurationMs)
        assertEquals(20L * 60_000L, totals.getValue(k2).totalDurationMs)
    }

    @Test
    fun `repeat collection yields identical session state`() = runTest {
        val day = LocalDate.of(2026, 11, 14)
        val events = FakeEventSource().apply {
            events = listOf(
                RawUsageEvent("com.a", UsageEventType.FOREGROUND, at(day, 6, 30)),
                RawUsageEvent("com.a", UsageEventType.BACKGROUND, at(day, 7, 15)),
                RawUsageEvent("com.b", UsageEventType.FOREGROUND, at(day, 8, 0)),
                RawUsageEvent("com.b", UsageEventType.BACKGROUND, at(day, 8, 30)),
            )
        }
        val clock = MutableClock(at(day, 10, 0))
        val repository = repo(clock, events)

        repository.collectRecent(at(day, 4, 0), at(day, 10, 0))
        val first = db.usageSessionDao().getAll().map { listOf(it.packageName, it.startTime, it.endTime) }
        repository.collectRecent(at(day, 4, 0), at(day, 10, 0))
        val second = db.usageSessionDao().getAll().map { listOf(it.packageName, it.startTime, it.endTime) }

        assertEquals(first, second)
    }

    private companion object {
        /** Matches UsageRepositoryImpl's retention floor (7 days) for assertions. */
        const val EVENT_RETENTION_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
