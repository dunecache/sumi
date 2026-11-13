package com.sumi.di

import com.sumi.core.common.AppDispatchers
import com.sumi.core.common.DispatchersProvider
import com.sumi.domain.common.Clock
import com.sumi.domain.common.TimeZoneProvider
import com.sumi.domain.common.systemClock
import com.sumi.domain.common.systemTimeZoneProvider
import com.sumi.platform.applications.AppIconLoader
import com.sumi.platform.applications.AppLabelResolver
import com.sumi.platform.applications.PackageManagerIconLoader
import com.sumi.platform.applications.PackageManagerLabelResolver
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

    @Provides
    @Singleton
    fun provideAppIconLoader(impl: PackageManagerIconLoader): AppIconLoader = impl

    @Provides
    @Singleton
    fun provideAppLabelResolver(impl: PackageManagerLabelResolver): AppLabelResolver = impl
}
