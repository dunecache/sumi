package com.sumi.di

import com.sumi.core.common.AppDispatchers
import com.sumi.core.common.DispatchersProvider
import com.sumi.domain.common.Clock
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.common.systemClock
import com.sumi.domain.common.systemTimeZoneProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDispatchers(provider: DispatchersProvider): AppDispatchers = provider.get()

    @Provides
    @Singleton
    fun provideClock(): Clock = systemClock()

    @Provides
    @Singleton
    fun provideTimeZoneProvider(): TimeZoneProvider = systemTimeZoneProvider()
}
