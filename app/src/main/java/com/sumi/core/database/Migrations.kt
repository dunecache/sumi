package com.sumi.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema history (schema JSONs are exported to app/schemas).
 *
 * 1 → 2 (category system): `app_categories` gains `sortOrder` (display order, 0-based)
 * and `isUserCreated`. The table was never populated before this migration (seeding is
 * introduced in v2), so migrated databases receive the seeded defaults with their
 * canonical order on first launch — no data transform needed.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_categories ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_categories ADD COLUMN isUserCreated INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * 2 → 3 (Focus sessions): adds `focus_sessions` (start, optional end, intended minutes,
 * exit count, outcome). No existing tables are touched; `endedAtUtcMs` NULL + `outcome
 * = 'open'` is the at-most-one open-session invariant.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `focus_sessions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `startedAtUtcMs` INTEGER NOT NULL,
                `endedAtUtcMs` INTEGER,
                `intendedMinutes` INTEGER NOT NULL,
                `exitCount` INTEGER NOT NULL,
                `outcome` TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_sessions_startedAtUtcMs` ON `focus_sessions` (`startedAtUtcMs`)")
    }
}
