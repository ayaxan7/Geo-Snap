package com.patashot.myapp.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.patashot.myapp.data.media.CapturedMediaDao
import com.patashot.myapp.data.media.CapturedMediaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE captured_media ADD COLUMN cleanPath TEXT")
            db.execSQL("ALTER TABLE captured_media ADD COLUMN originalLatitude REAL")
            db.execSQL("ALTER TABLE captured_media ADD COLUMN originalLongitude REAL")
            db.execSQL("ALTER TABLE captured_media ADD COLUMN city TEXT")
            db.execSQL("ALTER TABLE captured_media ADD COLUMN fullAddress TEXT")
            db.execSQL("ALTER TABLE captured_media ADD COLUMN templateId TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideCapturedMediaDatabase(@ApplicationContext context: Context): CapturedMediaDatabase =
        Room.databaseBuilder(context, CapturedMediaDatabase::class.java, "myapp.db")
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCapturedMediaDao(database: CapturedMediaDatabase): CapturedMediaDao =
        database.capturedMediaDao()
}