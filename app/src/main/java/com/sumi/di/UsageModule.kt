package com.sumi.di

import com.sumi.data.usage.UsageRepositoryImpl
import com.sumi.domain.usage.RebootBoundarySource
import com.sumi.domain.usage.UsageEventSource
import com.sumi.domain.usage.UsageRepository
import com.sumi.domain.usage.UsageStatsSource
import com.sumi.platform.usage.PlatformRebootSource
import com.sumi.platform.usage.PlatformUsageSources
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UsageProviderModule {

    @Provides
    @Singleton
    fun provideEventSource(sources: PlatformUsageSources): UsageEventSource = sources

    @Provides
    @Singleton
    fun provideStatsSource(sources: PlatformUsageSources): UsageStatsSource = sources

    @Provides
    @Singleton
    fun provideRebootSource(source: PlatformRebootSource): RebootBoundarySource = source
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UsageBindsModule {

    @Binds
    @Singleton
    abstract fun bindUsageRepository(impl: UsageRepositoryImpl): UsageRepository
}
