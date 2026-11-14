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
 * Migration 2 → 3: adds `focus_sessions` (start, end, intended minutes, exit count, outcome).
 *
 * **Status: written, never executed.** No device/emulator was available; CI runs unit
 * tests only. Like [Migration12Test], it also depends on the Room schema JSONs for
 * v2 and v3 being present under `app/schemas` — the v2 export was never committed
 * (see PR notes), so this test cannot run until someone builds the app on a machine
 * with a working Android SDK and commits the exported `2.json`/`3.json`.
 *
 * Run with: `./gradlew :app:connectedDebugAndroidTest` on an emulator or device.
 */
@RunWith(AndroidJUnit4::class)
class Migration23Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SumiDatabase::class.java,
    )

    @Test
    fun migrate2_to3_createsFocusSessionsAndPreservesRows() {
        val dbFile = "migration-test-focus"
        // Create a v2 database with a category row to prove existing data survives.
        val migrated = helper.createDatabase(dbFile, 2).apply {
            execSQL("INSERT INTO app_categories (id, name, colorToken, sortOrder, isUserCreated) VALUES ('social', 'Social', 'categoryColor1', 1, 0)")
            close()
        }
        migrated.close()

        val after = helper.runMigrationsAndValidate(dbFile, 3, true, MIGRATION_2_3)
        after.query("SELECT id, name FROM app_categories WHERE id = 'social'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("social", cursor.getString(0))
        }
        // Table exists, holds no rows, and the index was created.
        after.query("SELECT COUNT(*) FROM focus_sessions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        after.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'focus_sessions'",
        ).use { cursor ->
            val indexes = buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
            assertTrue(indexes.contains("index_focus_sessions_startedAtUtcMs"))
        }
        // Insert a live session row through SQL to prove the table's invariant shape.
        after.execSQL("INSERT INTO focus_sessions (startedAtUtcMs, endedAtUtcMs, intendedMinutes, exitCount, outcome) VALUES (1000, NULL, 25, 0, 'open')")
        after.query("SELECT outcome, exitCount FROM focus_sessions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("open", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
        }
        after.close()
    }
}
