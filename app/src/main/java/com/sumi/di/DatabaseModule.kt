package com.sumi.di

import com.sumi.core.database.DatabaseFactory
import com.sumi.core.database.SumiDatabase
import com.sumi.core.database.dao.ApplicationDao
import com.sumi.core.database.dao.CollectionStateDao
import com.sumi.core.database.dao.DailyAppUsageDao
import com.sumi.core.database.dao.DailyUsageDao
import com.sumi.core.database.dao.HourlyUsageDao
import com.sumi.core.database.dao.UsageSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(factory: DatabaseFactory): SumiDatabase = factory.get()

    @Provides fun provideApplicationDao(db: SumiDatabase): ApplicationDao = db.applicationDao()
    @Provides fun provideUsageSessionDao(db: SumiDatabase): UsageSessionDao = db.usageSessionDao()
    @Provides fun provideDailyUsageDao(db: SumiDatabase): DailyUsageDao = db.dailyUsageDao()
    @Provides fun provideDailyAppUsageDao(db: SumiDatabase): DailyAppUsageDao = db.dailyAppUsageDao()
    @Provides fun provideHourlyUsageDao(db: SumiDatabase): HourlyUsageDao = db.hourlyUsageDao()
    @Provides fun provideCollectionStateDao(db: SumiDatabase): CollectionStateDao = db.collectionStateDao()
}
