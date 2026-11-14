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
 * Migration 1 → 2: `app_categories` gains `sortOrder` and `isUserCreated`.
 *
 * **Status: written, never executed in CI.** CI runs the JVM unit-test workflow only;
 * no device/emulator was available. Schemas 1.json and 2.json are committed under
 * app/schemas (2.json was added with the 2 → 3 migration work), so the reference
 * database is available; run with:
 * `./gradlew :app:connectedDebugAndroidTest` on an emulator or device.
 */
@RunWith(AndroidJUnit4::class)
class Migration12Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SumiDatabase::class.java,
    )

    @Test
    fun migrate1_to2_addsColumnsAndPreservesRows() {
        val dbFile = "migration-test"
        // Create the v1 database with a category row to prove data survives.
        helper.createDatabase(dbFile, 1).apply {
            execSQL("INSERT INTO app_categories (id, name, colorToken) VALUES ('social', 'Social', 'categoryColor1')")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbFile, 2, true, MIGRATION_1_2)
        migrated.query("SELECT id, name, colorToken, sortOrder, isUserCreated FROM app_categories WHERE id = 'social'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("social", cursor.getString(0))
                assertEquals("Social", cursor.getString(1))
                assertEquals("categoryColor1", cursor.getString(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals(0, cursor.getInt(4))
            }
        migrated.close()
    }
}
