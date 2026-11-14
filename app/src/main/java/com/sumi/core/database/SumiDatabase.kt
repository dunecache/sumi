package com.sumi.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sumi.core.database.dao.AppCategoryDao
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.core.database.dao.CollectionStateDao
import com.sumi.core.database.dao.DailyAppUsageDao
import com.sumi.core.database.dao.DailyUsageDao
import com.sumi.core.database.dao.HourlyUsageDao
import com.sumi.core.database.dao.UsageSessionDao
import com.sumi.core.database.dao.FocusSessionDao
import com.sumi.core.database.entity.AppCategory
import com.sumi.core.database.entity.Application
import com.sumi.core.database.entity.CollectionState
import com.sumi.core.database.entity.DailyAppUsage
import com.sumi.core.database.entity.DailyUsage
import com.sumi.core.database.entity.FocusSession
import com.sumi.core.database.entity.HourlyUsage
import com.sumi.core.database.entity.UsageSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Schema per DATA_MODEL.md. Migration 1→2 adds the category system (sortOrder, isUserCreated).
// Migration 2→3 adds Focus sessions (focus_sessions).
// Migration 3→4 adds the Apps-redesign columns (isSystemApp, firstSeenAt).
// Migration 4→5 adds the usage-pipeline columns (daily_usage.source, collection_state.pipelineVersion).
@Database(
    entities = [
        Application::class,
        UsageSession::class,
        DailyUsage::class,
        DailyAppUsage::class,
        HourlyUsage::class,
        AppCategory::class,
        CollectionState::class,
        FocusSession::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class SumiDatabase : RoomDatabase() {
    abstract fun applicationDao(): ApplicationDao
    abstract fun appCategoryDao(): AppCategoryDao
    abstract fun usageSessionDao(): UsageSessionDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun dailyAppUsageDao(): DailyAppUsageDao
    abstract fun hourlyUsageDao(): HourlyUsageDao
    abstract fun collectionStateDao(): CollectionStateDao
    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        const val DB_NAME = "sumi.db"

        fun create(context: Context): SumiDatabase = Room.databaseBuilder(
            context.applicationContext,
            SumiDatabase::class.java,
            DB_NAME,
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
    }
}

@Singleton
class DatabaseFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Synchronized
    fun get(): SumiDatabase = SumiDatabase.create(context)
}
