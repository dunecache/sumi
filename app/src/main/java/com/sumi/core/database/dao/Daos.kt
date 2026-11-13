package com.sumi.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sumi.core.database.entity.Application
import com.sumi.core.database.entity.CollectionState
import com.sumi.core.database.entity.DailyAppUsage
import com.sumi.core.database.entity.DailyUsage
import com.sumi.core.database.entity.HourlyUsage
import com.sumi.core.database.entity.UsageSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ApplicationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(applications: List<Application>)

    @Query("SELECT * FROM applications WHERE isInstalled = 1 ORDER BY displayName COLLATE NOCASE")
    fun observeInstalled(): Flow<List<Application>>

    @Query("SELECT * FROM applications")
    suspend fun getAll(): List<Application>

    @Query("SELECT * FROM applications WHERE packageName = :packageName")
    suspend fun getByPackage(packageName: String): Application?
}

@Dao
interface UsageSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<UsageSession>)

    @Query("SELECT * FROM usage_sessions WHERE dateKey = :dateKey ORDER BY startTime")
    suspend fun getByDateKey(dateKey: String): List<UsageSession>

    @Query("SELECT * FROM usage_sessions WHERE dateKey = :dateKey ORDER BY startTime")
    fun observeByDateKey(dateKey: String): Flow<List<UsageSession>>

    @Query("SELECT * FROM usage_sessions WHERE packageName = :packageName AND dateKey = :dateKey")
    suspend fun getByPackageAndDateKey(packageName: String, dateKey: String): List<UsageSession>

    @Query("SELECT COUNT(*) FROM usage_sessions")
    suspend fun count(): Long

    @Query("SELECT * FROM usage_sessions WHERE startTime BETWEEN :from AND :to ORDER BY startTime")
    suspend fun getAllInRange(from: Long, to: Long): List<UsageSession>

    @Query("SELECT * FROM usage_sessions ORDER BY startTime")
    suspend fun getAll(): List<UsageSession>

    @Query("SELECT DISTINCT dateKey FROM usage_sessions")
    suspend fun getAllDateKeys(): List<String>

    @Query("DELETE FROM usage_sessions")
    suspend fun deleteAll()
}

@Dao
interface DailyUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(values: List<DailyUsage>)

    @Query("SELECT * FROM daily_usage WHERE dateKey = :dateKey")
    suspend fun get(dateKey: String): DailyUsage?

    @Query("SELECT * FROM daily_usage ORDER BY dateKey DESC")
    fun observeAll(): Flow<List<DailyUsage>>

    @Query("DELETE FROM daily_usage WHERE dateKey IN (:dateKeys)")
    suspend fun deleteByDateKeys(dateKeys: List<String>)

    @Query("DELETE FROM daily_usage")
    suspend fun deleteAll()
}

@Dao
interface DailyAppUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(values: List<DailyAppUsage>)

    @Query("SELECT * FROM daily_app_usage WHERE dateKey = :dateKey")
    suspend fun getByDateKey(dateKey: String): List<DailyAppUsage>

    @Query("SELECT * FROM daily_app_usage WHERE dateKey = :dateKey")
    fun observeByDateKey(dateKey: String): Flow<List<DailyAppUsage>>

    @Query("SELECT * FROM daily_app_usage WHERE dateKey IN (:dateKeys)")
    fun observeByDateKeys(dateKeys: List<String>): Flow<List<DailyAppUsage>>

    @Query("SELECT * FROM daily_app_usage WHERE packageName = :packageName ORDER BY dateKey DESC")
    fun observeByPackage(packageName: String): Flow<List<DailyAppUsage>>

    @Query("DELETE FROM daily_app_usage WHERE dateKey IN (:dateKeys)")
    suspend fun deleteByDateKeys(dateKeys: List<String>)

    @Query("DELETE FROM daily_app_usage")
    suspend fun deleteAll()
}

@Dao
interface HourlyUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(values: List<HourlyUsage>)

    @Query("SELECT * FROM hourly_usage WHERE dateKey = :dateKey")
    suspend fun getByDateKey(dateKey: String): List<HourlyUsage>

    @Query("DELETE FROM hourly_usage WHERE dateKey IN (:dateKeys)")
    suspend fun deleteByDateKeys(dateKeys: List<String>)

    @Query("DELETE FROM hourly_usage")
    suspend fun deleteAll()
}

@Dao
interface CollectionStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: CollectionState)

    @Query("SELECT * FROM collection_state WHERE id = 1")
    suspend fun get(): CollectionState?

    @Query("SELECT * FROM collection_state WHERE id = 1")
    fun observe(): Flow<CollectionState?>
}
