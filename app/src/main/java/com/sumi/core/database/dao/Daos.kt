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
import com.sumi.core.database.entity.FocusSession
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

    /**
     * Flip the installed flag without touching any other field (an app that left the
     * launcher catalog is not deleted — its assignment and history stay; a reinstall
     * flips the flag back via discovery).
     */
    @Query("UPDATE applications SET isInstalled = :installed WHERE packageName IN (:packages)")
    suspend fun setInstalled(packages: List<String>, installed: Boolean)

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

    /**
     * Sessions overlapping the half-open range [[from], [toExclusive]). Open rows
     * (`endTime` NULL) are always included regardless of start — a stale open row must
     * never leak out of view. Aggregation and session views split rows at day/hour
     * boundaries themselves.
     */
    @Query("SELECT * FROM usage_sessions WHERE startTime < :toExclusive AND (endTime IS NULL OR endTime > :from) ORDER BY startTime")
    fun observeOverlapping(from: Long, toExclusive: Long): Flow<List<UsageSession>>

    /** [observeOverlapping] restricted to one package. */
    @Query("SELECT * FROM usage_sessions WHERE packageName = :packageName AND startTime < :toExclusive AND (endTime IS NULL OR endTime > :from) ORDER BY startTime")
    fun observeOverlappingForPackage(packageName: String, from: Long, toExclusive: Long): Flow<List<UsageSession>>

    /** [observeOverlapping] as a one-shot read. */
    @Query("SELECT * FROM usage_sessions WHERE startTime < :toExclusive AND (endTime IS NULL OR endTime > :from) ORDER BY startTime")
    suspend fun getOverlapping(from: Long, toExclusive: Long): List<UsageSession>

    /** Rows whose package matches and that overlap the range (exclusion pruning). */
    @Query("SELECT * FROM usage_sessions WHERE packageName IN (:packages) ORDER BY startTime")
    suspend fun getByPackages(packages: List<String>): List<UsageSession>

    /** Repair: delete rows overlapping [[from], [toExclusive]). */
    @Query("DELETE FROM usage_sessions WHERE startTime < :toExclusive AND (endTime IS NULL OR endTime > :from)")
    suspend fun deleteOverlapping(from: Long, toExclusive: Long)

    /** Rows still open. */
    @Query("SELECT * FROM usage_sessions WHERE endTime IS NULL ORDER BY startTime")
    suspend fun getOpen(): List<UsageSession>

    /** Oldest stored session start, for the "history begins on …" marker. */
    @Query("SELECT MIN(startTime) FROM usage_sessions")
    suspend fun earliestStart(): Long?

    @Query("SELECT * FROM usage_sessions ORDER BY startTime")
    suspend fun getAll(): List<UsageSession>

    @Query("DELETE FROM usage_sessions WHERE packageName IN (:packages)")
    suspend fun deleteByPackages(packages: List<String>)
}

@Dao
interface DailyUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(values: List<DailyUsage>)

    @Query("SELECT * FROM daily_usage WHERE dateKey IN (:dateKeys) ORDER BY dateKey ASC")
    fun observeByDateKeys(dateKeys: List<String>): Flow<List<DailyUsage>>

    /** Delete rows of one source (e.g. SESSIONS) — used by repair and full rebuilds. */
    @Query("DELETE FROM daily_usage WHERE source = :source")
    suspend fun deleteWhereSource(source: String)

    /** Delete rows of one source among [dateKeys] — used by repair and prune. */
    @Query("DELETE FROM daily_usage WHERE source = :source AND dateKey IN (:dateKeys)")
    suspend fun deleteByDateKeysAndSource(dateKeys: List<String>, source: String)

    @Query("DELETE FROM daily_usage WHERE dateKey IN (:dateKeys)")
    suspend fun deleteByDateKeys(dateKeys: List<String>)
}

@Dao
interface DailyAppUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(values: List<DailyAppUsage>)

    @Query("SELECT * FROM daily_app_usage WHERE dateKey = :dateKey")
    fun observeByDateKey(dateKey: String): Flow<List<DailyAppUsage>>

    @Query("SELECT * FROM daily_app_usage WHERE dateKey IN (:dateKeys) ORDER BY dateKey ASC")
    fun observeByDateKeys(dateKeys: List<String>): Flow<List<DailyAppUsage>>

    @Query("SELECT * FROM daily_app_usage WHERE packageName = :packageName ORDER BY dateKey DESC")
    fun observeByPackage(packageName: String): Flow<List<DailyAppUsage>>

    @Query("DELETE FROM daily_app_usage WHERE dateKey IN (:dateKeys)")
    suspend fun deleteByDateKeys(dateKeys: List<String>)

    /** Delete rows of one source (e.g. SESSIONS) — used by repair and full rebuilds. */
    @Query("DELETE FROM daily_app_usage WHERE source = :source")
    suspend fun deleteWhereSource(source: String)

    /** Delete rows of one source among [dateKeys] — used by repair and prune. */
    @Query("DELETE FROM daily_app_usage WHERE source = :source AND dateKey IN (:dateKeys)")
    suspend fun deleteByDateKeysAndSource(dateKeys: List<String>, source: String)

    /** Days that hold per-app rows of the given packages (exclusion pruning). */
    @Query("SELECT DISTINCT dateKey FROM daily_app_usage WHERE packageName IN (:packages)")
    suspend fun getDateKeysByPackages(packages: List<String>): List<String>

    /** Packages present in imported history with no Application row yet (library self-heal). */
    @Query("SELECT DISTINCT packageName FROM daily_app_usage WHERE packageName NOT IN (SELECT packageName FROM applications)")
    suspend fun getPackagesMissingApplicationRows(): List<String>

    @Query("DELETE FROM daily_app_usage WHERE packageName IN (:packages)")
    suspend fun deleteByPackages(packages: List<String>)
}

@Dao
interface HourlyUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(values: List<HourlyUsage>)

    @Query("SELECT * FROM hourly_usage WHERE dateKey IN (:dateKeys) ORDER BY dateKey ASC, hour ASC")
    fun observeByDateKeys(dateKeys: List<String>): Flow<List<HourlyUsage>>

    @Query("DELETE FROM hourly_usage WHERE dateKey IN (:dateKeys)")
    suspend fun deleteByDateKeys(dateKeys: List<String>)

    /** Days that hold hourly rows of the given packages (exclusion pruning). */
    @Query("SELECT DISTINCT dateKey FROM hourly_usage WHERE packageName IN (:packages)")
    suspend fun getDateKeysByPackages(packages: List<String>): List<String>

    @Query("DELETE FROM hourly_usage WHERE packageName IN (:packages)")
    suspend fun deleteByPackages(packages: List<String>)

    /** Session-derived hourly rows — wiped and re-derived by full rebuilds. */
    @Query("DELETE FROM hourly_usage")
    suspend fun deleteAll()
}

@Dao
interface FocusSessionDao {
    @Insert
    suspend fun insert(session: FocusSession): Long

    /** Updates an existing open session (finish any open row atomically at write time). */
    @Query("UPDATE focus_sessions SET endedAtUtcMs = :endedAtUtcMs, exitCount = :exitCount, outcome = :outcome WHERE id = :id")
    suspend fun finish(id: Long, endedAtUtcMs: Long, exitCount: Int, outcome: String)

    /** The at most one "open" row — marker semantics only; callers never expect a list here. */
    @Query("SELECT * FROM focus_sessions WHERE outcome = 'open' ORDER BY startedAtUtcMs DESC LIMIT 1")
    suspend fun open(): FocusSession?

    /** Live view of the open session; null when nobody is focusing. */
    @Query("SELECT * FROM focus_sessions WHERE outcome = 'open' ORDER BY startedAtUtcMs DESC LIMIT 1")
    fun observeOpen(): Flow<FocusSession?>

    /** Bump the exit counter of the open session only; keeps the open marker untouched. */
    @Query("UPDATE focus_sessions SET exitCount = exitCount + 1 WHERE outcome = 'open'")
    suspend fun incrementOpenExitCount(): Int

    /** Marks an open session with [outcome] without inventing a wall-clock end it cannot know. */
    @Query("UPDATE focus_sessions SET outcome = :outcome WHERE id = :id AND outcome = 'open'")
    suspend fun sealOpenAs(id: Long, outcome: String): Int

    /** Closed sessions, newest first. */
    @Query("SELECT * FROM focus_sessions WHERE outcome != 'open' ORDER BY startedAtUtcMs DESC LIMIT 50")
    fun observeHistory(): Flow<List<FocusSession>>

    /** Sessions ended inside a caller-supplied window (history roll-up uses trailing 7 days). */
    @Query("SELECT * FROM focus_sessions WHERE outcome != 'open' AND COALESCE(endedAtUtcMs, startedAtUtcMs) >= :windowStart ORDER BY startedAtUtcMs ASC")
    suspend fun since(windowStart: Long): List<FocusSession>

    /** Seal every open row as ended-early at [endedAtUtcMs]; part of [insertSealingOpen]. */
    @Query("UPDATE focus_sessions SET outcome = 'ended_early', endedAtUtcMs = :endedAtUtcMs WHERE outcome = 'open'")
    suspend fun closeAllOpen(endedAtUtcMs: Long): Int

    /**
     * Atomic insert: seals any open rows and inserts the new one inside a single
     * transaction — the at-most-one-open invariant holds even under a race.
     */
    @androidx.room.Transaction
    suspend fun insertSealingOpen(session: FocusSession): Long {
        closeAllOpen(session.startedAtUtcMs)
        return insert(session)
    }

    @Query("DELETE FROM focus_sessions")
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
