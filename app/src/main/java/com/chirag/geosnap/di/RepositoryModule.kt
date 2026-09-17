package com.chirag.geosnap.di

import com.chirag.geosnap.data.datastore.PreferencesRepositoryImpl
import com.chirag.geosnap.data.location.LocationProviderImpl
import com.chirag.geosnap.data.media.MediaRepositoryImpl
import com.chirag.geosnap.domain.repository.LocationRepository
import com.chirag.geosnap.domain.repository.MediaRepository
import com.chirag.geosnap.domain.repository.PreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(
        impl: PreferencesRepositoryImpl
    ): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        impl: LocationProviderImpl
    ): LocationRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(
        impl: MediaRepositoryImpl
    ): MediaRepository
}