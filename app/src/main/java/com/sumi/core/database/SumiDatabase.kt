package com.sumi.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.core.database.dao.CollectionStateDao
import com.sumi.core.database.dao.DailyAppUsageDao
import com.sumi.core.database.dao.DailyUsageDao
import com.sumi.core.database.dao.HourlyUsageDao
import com.sumi.core.database.dao.UsageSessionDao
import com.sumi.core.database.entity.AppCategory
import com.sumi.core.database.entity.Application
import com.sumi.core.database.entity.CollectionState
import com.sumi.core.database.entity.DailyAppUsage
import com.sumi.core.database.entity.DailyUsage
import com.sumi.core.database.entity.HourlyUsage
import com.sumi.core.database.entity.UsageSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// M0 foundation: schema per DATA_MODEL.md. M1 fills the usage engine.
@Database(
    entities = [
        Application::class,
        UsageSession::class,
        DailyUsage::class,
        DailyAppUsage::class,
        HourlyUsage::class,
        AppCategory::class,
        CollectionState::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SumiDatabase : RoomDatabase() {
    abstract fun applicationDao(): ApplicationDao
    abstract fun usageSessionDao(): UsageSessionDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun dailyAppUsageDao(): DailyAppUsageDao
    abstract fun hourlyUsageDao(): HourlyUsageDao
    abstract fun collectionStateDao(): CollectionStateDao

    companion object {
        const val DB_NAME = "sumi.db"

        fun create(context: Context): SumiDatabase = Room.databaseBuilder(
            context.applicationContext,
            SumiDatabase::class.java,
            DB_NAME,
        ).addMigrations().build()
    }
}

@Singleton
class DatabaseFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Synchronized
    fun get(): SumiDatabase = SumiDatabase.create(context)
}
