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
 * Migration 2 → 3: `applications` gains `isSystemApp` and `firstSeenAt` (both
 * `INTEGER NOT NULL DEFAULT 0`). Pre-v3 rows survive untouched and read back as
 * `isSystemApp = 0` / `firstSeenAt = 0` ("not new, not system") — which is exactly
 * the pre-v3 behavior.
 *
 * **Status: written, not executed in CI.** CI runs the JVM unit-test workflow only;
 * this needs an emulator or device (`./gradlew :app:connectedDebugAndroidTest`).
 * Schemas 2.json and 3.json are committed under app/schemas, so the helper has a
 * reference database for the validation to compare against.
 */
@RunWith(AndroidJUnit4::class)
class Migration23Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SumiDatabase::class.java,
    )

    @Test
    fun migrate2_to3_addsColumnsAndPreservesRows() {
        val dbFile = "migration-2-3-test"
        // Create the v2 database with an application row (and a user assignment)
        // to prove rows survive untouched.
        helper.createDatabase(dbFile, 2).apply {
            execSQL(
                "INSERT INTO applications (packageName, displayName, categoryId, isHidden, " +
                    "isCategoryUserSet, isInstalled) VALUES ('com.example.app', 'Example', 'social', 0, 1, 1)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbFile, 3, true, MIGRATION_2_3)
        migrated.query(
            "SELECT packageName, categoryId, isCategoryUserSet, isSystemApp, firstSeenAt " +
                "FROM applications WHERE packageName = 'com.example.app'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("com.example.app", cursor.getString(0))
            // The user's assignment survives the migration untouched.
            assertEquals("social", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals(0, cursor.getInt(3))
            assertEquals(0L, cursor.getLong(4))
        }
        migrated.close()
    }
}
