package com.sumi.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration 4 → 5: `daily_usage` gains `source` (default `'SESSIONS'`) and
 * `collection_state` gains `pipelineVersion` (default `0` — which is what forces
 * the one-time pipeline repair on the first collection after the upgrade).
 * Pre-v5 rows survive untouched; imported `USAGE_STATS_AGGREGATE` per-app rows
 * are not touched by the migration at all.
 *
 * **Status: written, not executed in CI.** CI runs the JVM unit-test workflow only;
 * this needs an emulator or device (`./gradlew :app:connectedDebugAndroidTest`).
 * Schemas 4.json (v4) and 5.json (v5) are committed under app/schemas, so the
 * helper has a reference database for the validation to compare against.
 */
@RunWith(AndroidJUnit4::class)
class Migration45Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SumiDatabase::class.java,
    )

    @Test
    fun migrate4_to5_addsPipelineColumnsAndPreservesRows() {
        val dbFile = "migration-4-5-test"
        helper.createDatabase(dbFile, 4).apply {
            // A collected day and the cursor row, as written by the v4 pipeline.
            execSQL("INSERT INTO daily_usage (dateKey, totalDurationMs, lastCollectedAt) " +
                "VALUES ('2026-11-13', 5400000, 1762977600000)")
            execSQL("INSERT INTO collection_state (id, lastEventTimeCollected, earliestSessionTime, " +
                "lastTimeZoneId, initialSyncCompleted) VALUES (1, 1762977600000, 1762876800000, 'Europe/Berlin', 1)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbFile, 5, true, MIGRATION_4_5)
        migrated.query(
            "SELECT dateKey, totalDurationMs, lastCollectedAt, source FROM daily_usage WHERE dateKey = '2026-11-13'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-11-13", cursor.getString(0))
            assertEquals(5400000L, cursor.getLong(1))
            assertEquals(1762977600000L, cursor.getLong(2))
            // Pre-v5 rows read back as session-derived, which matches their origin.
            assertEquals("SESSIONS", cursor.getString(3))
        }
        migrated.query(
            "SELECT lastEventTimeCollected, earliestSessionTime, lastTimeZoneId, initialSyncCompleted, " +
                "pipelineVersion FROM collection_state WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1762977600000L, cursor.getLong(0))
            assertEquals(1762876800000L, cursor.getLong(1))
            assertEquals("Europe/Berlin", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
            // 0 triggers the one-time repair on first collection after upgrade.
            assertEquals(0, cursor.getInt(4))
        }
        migrated.close()
    }
}
