package com.sumi.core.database.entity

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
    // Migration 1→2 adds these columns; DDL default 0 in the migration keeps old rows valid.
    val sortOrder: Int = 0,
    val isUserCreated: Boolean = false,
)

@Entity(tableName = "applications")
data class Application(
    @PrimaryKey val packageName: String,
    val displayName: String,
    val categoryId: String? = null,
    val isHidden: Boolean = false,
    val isCategoryUserSet: Boolean = false,
    val isInstalled: Boolean = true,
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

@Entity(tableName = "daily_usage")
data class DailyUsage(
    @PrimaryKey val dateKey: String,
    val totalDurationMs: Long, // union of intervals (avoids multi-window double count)
    val lastCollectedAt: Long,
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
    val hour: Int, // 0-23 in zone used for dateKey (23/25 on DST days)
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
)
