package com.chirag.geosnap.data.media

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The app-gallery database: a persistent index of every photo/video GeoSnap
 * captured. The actual files live in MediaStore; rows hold the content uri and
 * geolocation metadata.
 */
@Database(entities = [CapturedMediaEntity::class], version = 1, exportSchema = false)
@TypeConverters(MediaTypeConverters::class)
abstract class CapturedMediaDatabase : RoomDatabase() {
    abstract fun capturedMediaDao(): CapturedMediaDao
}