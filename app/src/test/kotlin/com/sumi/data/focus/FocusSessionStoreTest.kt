package com.sumi.data.focus

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sumi.core.common.AppDispatchers
import com.sumi.core.database.SumiDatabase
import com.sumi.core.database.entity.FocusOutcome
import com.sumi.domain.common.Clock
import com.sumi.domain.common.TimeZoneProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Store-level tests for the Focus session lifecycle (§16.5–7), against an in-memory
 * Room database. Covers the open/close invariant, boot finalization, and the trailing
 * 7-day roll-up. No UI involved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusSessionStoreTest {

    private val zone = ZoneId.of("Europe/Berlin")

    // 2026-11-14 is the fixed "now"; noon local so the week window is well-defined.
    private val fixedNow = LocalDateTime.of(2026, 11, 14, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private lateinit var db: SumiDatabase
    private lateinit var repository: FocusSessionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SumiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FocusSessionRepository(
            sessionDao = db.focusSessionDao(),
            clock = Clock { fixedNow },
            timeZone = TimeZoneProvider { zone },
            dispatchers = AppDispatchers(Dispatchers.IO, Dispatchers.Unconfined, Dispatchers.Unconfined),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `starting a session opens exactly one row`() = runTest {
        val id = repository.startSession(25)
        val open = db.focusSessionDao().open()
        assertEquals(id, open?.id)
        assertEquals(25, open?.intendedMinutes)
        assertEquals(0, open?.exitCount)
        assertEquals(FocusOutcome.OPEN, open?.outcome)
        assertNull(open?.endedAtUtcMs)
    }

    @Test
    fun `starting over closes the stale open session as ended early`() = runTest {
        val first = repository.startSession(25)
        val nowField = db.focusSessionDao().open()!!
        assertEquals(first, nowField.id)

        repository.startSession(45)

        val openRows = db.focusSessionDao().open()
        assertNotEquals(first, openRows?.id)
        assertEquals(45, openRows?.intendedMinutes)
        val stale = db.focusSessionDao().observeHistory().first()
            .first { it.id == first }
        assertEquals(FocusOutcome.ENDED_EARLY, stale.outcome)
        assertEquals(fixedNow, stale.endedAtUtcMs)
    }

    @Test
    fun `recordExit bumps only the open session and survives a completion`() = runTest {
        repository.startSession(25)
        repository.recordExit()
        repository.recordExit()
        assertEquals(2, db.focusSessionDao().open()?.exitCount)

        repository.endCompleted()
        assertNull(db.focusSessionDao().open())
        val closed = db.focusSessionDao().observeHistory().first().single()
        assertEquals(FocusOutcome.COMPLETED, closed.outcome)
        assertEquals(2, closed.exitCount)
    }

    @Test
    fun `closing twice is a no-op and keeps wall-clock truth`() = runTest {
        repository.startSession(25)
        repository.endCompleted()
        repository.endEarly() // no open session anymore; must not throw or create rows

        val rows = db.focusSessionDao().observeHistory().first()
        assertEquals(1, rows.size)
        assertEquals(FocusOutcome.COMPLETED, rows.single().outcome)
    }

    @Test
    fun `boot finalization seals an open session at the boot instant`() = runTest {
        repository.startSession(25)
        val bootAfterStart = fixedNow + 10 * 60_000L

        val sealed = repository.finalizeOpenAfterReboot(listOf(bootAfterStart))
        assertTrue(sealed)

        val row = db.focusSessionDao().observeHistory().first().single()
        assertEquals(FocusOutcome.REBOOT, row.outcome)
        assertEquals(bootAfterStart, row.endedAtUtcMs)
    }

    @Test
    fun `no recorded reboot seals as interrupted without inventing a wall-clock end`() = runTest {
        repository.startSession(25)
        // No boot after the session started — process death of other causes (OEM kill).
        val sealed = repository.finalizeOpenAfterReboot(listOf(fixedNow - 60_000L))
        assertTrue(sealed)
        assertNull(db.focusSessionDao().open())
        val row = db.focusSessionDao().observeHistory().first().single()
        assertEquals(FocusOutcome.INTERRUPTED, row.outcome)
        assertNull(row.endedAtUtcMs) // duration unknown — stated, never fabricated
    }

    @Test
    fun `weekly total counts only sessions ended inside the trailing 7 days`() = runTest {
        val dao = db.focusSessionDao()
        val historyWindowStart = LocalDateTime.of(2026, 11, 8, 0, 0).atZone(zone).toInstant().toEpochMilli()

        // Ended Nov 10 10:00, 45 minutes — inside the window.
        dao.insert(
            com.sumi.core.database.entity.FocusSession(
                startedAtUtcMs = LocalDateTime.of(2026, 11, 10, 9, 15).atZone(zone).toInstant().toEpochMilli(),
                endedAtUtcMs = LocalDateTime.of(2026, 11, 10, 10, 0).atZone(zone).toInstant().toEpochMilli(),
                intendedMinutes = 45,
                exitCount = 0,
                outcome = FocusOutcome.COMPLETED,
            ),
        )
        // Ended Nov 13 22:30, 60 minutes — inside the window.
        dao.insert(
            com.sumi.core.database.entity.FocusSession(
                startedAtUtcMs = LocalDateTime.of(2026, 11, 13, 21, 30).atZone(zone).toInstant().toEpochMilli(),
                endedAtUtcMs = LocalDateTime.of(2026, 11, 13, 22, 30).atZone(zone).toInstant().toEpochMilli(),
                intendedMinutes = 60,
                exitCount = 1,
                outcome = FocusOutcome.COMPLETED,
            ),
        )
        // Ended Nov 7 23:00 — before the local-midnight boundary; excluded.
        dao.insert(
            com.sumi.core.database.entity.FocusSession(
                startedAtUtcMs = LocalDateTime.of(2026, 11, 7, 22, 15).atZone(zone).toInstant().toEpochMilli(),
                endedAtUtcMs = LocalDateTime.of(2026, 11, 7, 23, 0).atZone(zone).toInstant().toEpochMilli(),
                intendedMinutes = 45,
                exitCount = 0,
                outcome = FocusOutcome.COMPLETED,
            ),
        )
        // Started Nov 12 08:00, ended never (interrupted, duration unknowable) — excluded
        // from the roll-up rather than counted with a fabricated end.
        dao.insert(
            com.sumi.core.database.entity.FocusSession(
                startedAtUtcMs = LocalDateTime.of(2026, 11, 12, 8, 0).atZone(zone).toInstant().toEpochMilli(),
                endedAtUtcMs = null,
                intendedMinutes = 25,
                exitCount = 0,
                outcome = FocusOutcome.INTERRUPTED,
            ),
        )
        assertTrue(dao.since(historyWindowStart).isNotEmpty())

        val totalMs = repository.weeklyFocusMs(fixedNow)
        assertEquals((45L + 60L) * 60_000L, totalMs)
    }
}

