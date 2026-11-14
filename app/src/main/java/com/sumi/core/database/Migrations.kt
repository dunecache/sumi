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
 *
 * 2 → 3 (Focus sessions): adds `focus_sessions` (start, optional end, intended minutes,
 * exit count, outcome). No existing tables are touched; `endedAtUtcMs` NULL + `outcome
 * = 'open'` is the at-most-one open-session invariant.
 *
 * 3 → 4 (Apps redesign): `applications` gains `isSystemApp` (device-furniture flag for
 * the Settings "Show system apps" toggle; never changes collection or exclusions) and
 * `firstSeenAt` (UTC ms the row was first created; drives the quiet "New" marker — 0
 * means "unknown", so pre-v3 rows never appear new). Both default to 0; no data
 * transform needed. The Apps refresh also now marks rows whose package vanished from
 * the launcher query as `isInstalled = 0` (assignment is kept, reinstall restores it)
 * — a behavioral change, not a schema change.
 *
 * 4 → 5 (usage pipeline hardening): `daily_usage` gains `source` (`SESSIONS` — derived
 * from stored sessions; `USAGE_STATS_AGGREGATE` — the imported upper bound for days
 * without sessions) and `collection_state` gains `pipelineVersion` (below
 * `CURRENT_PIPELINE_VERSION` triggers the one-time repair: retained-range
 * session-derived rows are wiped and the cursor is reset so the next run rebuilds
 * them with the corrected rules). Existing rows default to `SESSIONS` / version 0 —
 * imported days keep `SESSIONS` labels (they are re-touched by collection anyway)
 * and every migrated install gets exactly one repair pass.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_categories ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_categories ADD COLUMN isUserCreated INTEGER NOT NULL DEFAULT 0")
    }
}

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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE applications ADD COLUMN isSystemApp INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE applications ADD COLUMN firstSeenAt INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_usage ADD COLUMN source TEXT NOT NULL DEFAULT 'SESSIONS'")
        db.execSQL("ALTER TABLE collection_state ADD COLUMN pipelineVersion INTEGER NOT NULL DEFAULT 0")
    }
}
