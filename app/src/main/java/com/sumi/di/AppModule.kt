package com.sumi.di

import com.sumi.core.common.AppDispatchers
import com.sumi.core.common.DispatchersProvider
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
}
