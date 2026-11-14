package com.sumi.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities per DATA_MODEL.md. All timestamps are UTC epoch milliseconds.
 * Date keys are `YYYY-MM-DD` derived from the device zone at aggregation time.
 */

@Entity(tableName = "app_categories")
data class AppCategory(
    @PrimaryKey val id: String, // stable slug, e.g. social, entertainment
    val name: String,
    val colorToken: String, // design-system token name, not a hex value
    // Migration 1→2: display order (0-based) and whether the user created it.
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "0") val isUserCreated: Boolean = false,
)

@Entity(tableName = "applications")
data class Application(
    @PrimaryKey val packageName: String,
    val displayName: String,
    val categoryId: String? = null,
    val isHidden: Boolean = false,
    val isCategoryUserSet: Boolean = false,
    val isInstalled: Boolean = true,
    // Migration 3→4: system-app policy (device furniture, hidden by default) and the
    // quiet "New" marker. 0 / default rows keep pre-v4 behavior (not new, not system).
    @ColumnInfo(defaultValue = "0") val isSystemApp: Boolean = false,
    @ColumnInfo(defaultValue = "0") val firstSeenAt: Long = 0L,
)

@Entity(
    tableName = "usage_sessions",
    indices = [
        Index(value = ["packageName", "startTime"], unique = true),
        Index("dateKey"),
        Index("packageName", "dateKey"),
        Index("startTime"),
    ],
)
data class UsageSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startTime: Long, // UTC ms
    val endTime: Long?, // null = open current session
    val durationMs: Long,
    val dateKey: String,
    val isEstimated: Boolean = false,
)

/** Current version of the collection pipeline (see [CollectionState.pipelineVersion]). */
const val CURRENT_PIPELINE_VERSION = 2

@Entity(tableName = "daily_usage")
data class DailyUsage(
    @PrimaryKey val dateKey: String,
    val totalDurationMs: Long, // union of intervals (avoids multi-window double count)
    val lastCollectedAt: Long,
    /**
     * `SESSIONS` — derived from stored sessions; `USAGE_STATS_AGGREGATE` — imported
     * upper bound from `UsageStats` for days without sessions (sum of per-app time;
     * the per-day union is unknowable from aggregates). Session-derived rows replace
     * aggregate rows for the same day once collected. Migration 4→5 default.
     */
    @ColumnInfo(defaultValue = "'SESSIONS'") val source: String = "SESSIONS",
)

@Entity(
    tableName = "daily_app_usage",
    primaryKeys = ["dateKey", "packageName"],
    indices = [Index("packageName", "dateKey")],
)
data class DailyAppUsage(
    val dateKey: String,
    val packageName: String,
    val durationMs: Long,
    val source: String, // SESSIONS | USAGE_STATS_AGGREGATE
)

@Entity(
    tableName = "hourly_usage",
    primaryKeys = ["dateKey", "hour", "packageName"],
    indices = [Index("dateKey")],
)
data class HourlyUsage(
    val dateKey: String,
    val hour: Int, // 0-23 in zone used for dateKey (fall-back 01:00 bucket can hold up to 120 min; spring-forward 02:00 absent)
    val packageName: String,
    val durationMs: Long,
)

@Entity(tableName = "collection_state")
data class CollectionState(
    @PrimaryKey val id: Int = 1, // always 1
    val lastEventTimeCollected: Long = 0,
    val earliestSessionTime: Long? = null,
    val lastTimeZoneId: String = "",
    val initialSyncCompleted: Boolean = false,
    /**
     * Version of the collection pipeline that produced the stored rows. Below
     * [CURRENT_PIPELINE_VERSION] triggers the one-time repair: retained-range
     * session-derived rows are wiped and the cursor is reset so the next run
     * rebuilds them with the corrected rules. Migrated (v4) databases read 0.
     */
    @ColumnInfo(defaultValue = "0") val pipelineVersion: Int = CURRENT_PIPELINE_VERSION,
)

/** Outcomes a Focus session can end with. The OPEN marker means "still running". */
object FocusOutcome {
    const val OPEN = "open"
    const val COMPLETED = "completed"
    const val ENDED_EARLY = "ended_early"
    const val AWAY_LIMIT = "away_limit"
    const val INTERRUPTED = "interrupted"
    const val REBOOT = "reboot"
}

/** A Focus session. Timestamps UTC epoch ms; ends exactly at wall-clock truth. */
@Entity(
    tableName = "focus_sessions",
    indices = [Index("startedAtUtcMs")],
)
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtUtcMs: Long,
    /** Null while running — unique "open session" marker (at most one row open at a time). */
    val endedAtUtcMs: Long? = null,
    /** Minutes the user aimed for; remembered as the default duration for the next session. */
    val intendedMinutes: Int,
    val exitCount: Int = 0,
    /** See FocusOutcome; CLOSED after any ending. */
    val outcome: String = FocusOutcome.OPEN,
)
