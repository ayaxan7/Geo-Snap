package com.patashot.myapp.di

import com.patashot.myapp.data.datastore.PreferencesRepositoryImpl
import com.patashot.myapp.data.location.LocationProviderImpl
import com.patashot.myapp.data.media.MediaRepositoryImpl
import com.patashot.myapp.domain.repository.LocationRepository
import com.patashot.myapp.domain.repository.MediaRepository
import com.patashot.myapp.domain.repository.PreferencesRepository
import com.patashot.myapp.presentation.camera.CanvasGeoTagOverlayRenderer
import com.patashot.myapp.presentation.camera.GeoTagOverlayRenderer
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

    @Binds
    @Singleton
    abstract fun bindGeoTagOverlayRenderer(
        impl: CanvasGeoTagOverlayRenderer
    ): GeoTagOverlayRenderer
}