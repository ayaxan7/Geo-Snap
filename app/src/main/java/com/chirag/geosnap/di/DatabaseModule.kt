package com.chirag.geosnap.di

import android.content.Context
import androidx.room.Room
import com.chirag.geosnap.data.media.CapturedMediaDao
import com.chirag.geosnap.data.media.CapturedMediaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideCapturedMediaDatabase(@ApplicationContext context: Context): CapturedMediaDatabase =
        Room.databaseBuilder(context, CapturedMediaDatabase::class.java, "geosnap.db")
            .build()

    @Provides
    fun provideCapturedMediaDao(database: CapturedMediaDatabase): CapturedMediaDao =
        database.capturedMediaDao()
}