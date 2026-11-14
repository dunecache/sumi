package com.sumi.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sumi.core.database.entity.AppCategory
import com.sumi.core.database.entity.Application
import com.sumi.core.database.entity.CollectionState
import com.sumi.core.database.entity.DailyAppUsage
import com.sumi.core.database.entity.DailyUsage
import com.sumi.core.database.entity.HourlyUsage
import com.sumi.core.database.entity.UsageSession
import kotlinx.coroutines.flow.Flow

@Dao
interface AppCategoryDao {
    @Query("SELECT * FROM app_categories ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<AppCategory>>

    @Query("SELECT * FROM app_categories ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    suspend fun getAll(): List<AppCategory>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<AppCategory>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: AppCategory)

    @Query("DELETE FROM app_categories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM app_categories")
    suspend fun count(): Int

    @Query("SELECT * FROM app_categories WHERE id = :id")
    suspend fun getById(id: String): AppCategory?
}

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

    /** Library rows of the given packages (exclusion pruning). */
    @Query("SELECT * FROM applications WHERE packageName IN (:packages)")
    suspend fun getByPackages(packages: List<String>): List<Application>

    @Query("DELETE FROM applications WHERE packageName IN (:packages)")
    suspend fun deleteByPackages(packages: List<String>)

    @Query("SELECT * FROM applications WHERE packageName = :packageName")
    fun observeByPackage(packageName: String): Flow<Application?>

    @Query("UPDATE applications SET categoryId = :categoryId, isCategoryUserSet = :userSet WHERE packageName = :packageName")
    suspend fun setCategory(packageName: String, categoryId: String?, userSet: Boolean)

    /** Bulk category assignment (Apps multi-select → "Move to…"). Always user-assigned. */
    @Query("UPDATE applications SET categoryId = :categoryId, isCategoryUserSet = 1 WHERE packageName IN (:packages)")
    suspend fun setCategoryAll(packages: List<String>, categoryId: String?)

    @Query("SELECT * FROM applications WHERE categoryId = :categoryId ORDER BY displayName COLLATE NOCASE")
    fun observeByCategory(categoryId: String): Flow<List<Application>>

    @Query("SELECT * FROM applications WHERE categoryId = :categoryId ORDER BY displayName COLLATE NOCASE")
    suspend fun getByCategory(categoryId: String): List<Application>

    @Query("SELECT * FROM applications WHERE categoryId IS NULL ORDER BY displayName COLLATE NOCASE")
    fun observeUncategorized(): Flow<List<Application>>

    @Query("SELECT * FROM applications WHERE categoryId IS NULL ORDER BY displayName COLLATE NOCASE")
    suspend fun getUncategorized(): List<Application>

    /** Rows never explicitly classified by the user; eligible for default assignment. */
    @Query("SELECT * FROM applications WHERE isCategoryUserSet = 0 AND categoryId IS NULL")
    suspend fun getUnclassified(): List<Application>

    /** Auto-classified rows follow the deletion target (defaults still eligible afterwards). */
    @Query("UPDATE applications SET categoryId = :to, isCategoryUserSet = 0 WHERE categoryId = :from AND isCategoryUserSet = 0")
    suspend fun reassignAutoAssigned(from: String, to: String?)

    /** User-assigned rows follow the deletion target and stay user-assigned. */
    @Query("UPDATE applications SET categoryId = :to, isCategoryUserSet = 1 WHERE categoryId = :from AND isCategoryUserSet = 1")
    suspend fun reassignUserAssigned(from: String, to: String?)
}

@Dao
interface UsageSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<UsageSession>)

    @Query("SELECT * FROM usage_sessions WHERE dateKey = :dateKey ORDER BY startTime")
    suspend fun getByDateKey(dateKey: String): List<UsageSession>

    @Query("SELECT * FROM usage_sessions WHERE dateKey = :dateKey ORDER BY startTime")
    fun observeByDateKey(dateKey: String): Flow<List<UsageSession>>

    @Query("SELECT * FROM usage_sessions WHERE dateKey IN (:dateKeys) ORDER BY startTime")
    fun observeByDateKeys(dateKeys: List<String>): Flow<List<UsageSession>>

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

    /** Days that hold sessions of the given packages (exclusion pruning). */
    @Query("SELECT DISTINCT dateKey FROM usage_sessions WHERE packageName IN (:packages)")
    suspend fun getDateKeysByPackages(packages: List<String>): List<String>

    @Query("DELETE FROM usage_sessions WHERE packageName IN (:packages)")
    suspend fun deleteByPackages(packages: List<String>)

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

    @Query("SELECT * FROM daily_usage WHERE dateKey IN (:dateKeys)")
    fun observeByDateKeys(dateKeys: List<String>): Flow<List<DailyUsage>>

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

    /** Days that hold per-app rows of the given packages (exclusion pruning). */
    @Query("SELECT DISTINCT dateKey FROM daily_app_usage WHERE packageName IN (:packages)")
    suspend fun getDateKeysByPackages(packages: List<String>): List<String>

    /** Packages present in imported history with no Application row yet (library self-heal). */
    @Query("SELECT DISTINCT packageName FROM daily_app_usage WHERE packageName NOT IN (SELECT packageName FROM applications)")
    suspend fun getPackagesMissingApplicationRows(): List<String>

    @Query("DELETE FROM daily_app_usage WHERE packageName IN (:packages)")
    suspend fun deleteByPackages(packages: List<String>)

    @Query("DELETE FROM daily_app_usage")
    suspend fun deleteAll()
}

@Dao
interface HourlyUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(values: List<HourlyUsage>)

    @Query("SELECT * FROM hourly_usage WHERE dateKey = :dateKey")
    suspend fun getByDateKey(dateKey: String): List<HourlyUsage>

    @Query("SELECT * FROM hourly_usage WHERE dateKey IN (:dateKeys)")
    fun observeByDateKeys(dateKeys: List<String>): Flow<List<HourlyUsage>>

    @Query("DELETE FROM hourly_usage WHERE dateKey IN (:dateKeys)")
    suspend fun deleteByDateKeys(dateKeys: List<String>)

    /** Days that hold hourly rows of the given packages (exclusion pruning). */
    @Query("SELECT DISTINCT dateKey FROM hourly_usage WHERE packageName IN (:packages)")
    suspend fun getDateKeysByPackages(packages: List<String>): List<String>

    @Query("DELETE FROM hourly_usage WHERE packageName IN (:packages)")
    suspend fun deleteByPackages(packages: List<String>)

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
