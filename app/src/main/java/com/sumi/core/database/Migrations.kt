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
