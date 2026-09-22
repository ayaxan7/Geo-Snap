package com.chirag.geosnap.data.media

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CapturedMediaDao {

    /** Media the user still wants in the app gallery, newest first. */
    @Query("SELECT * FROM captured_media WHERE deleted = 0 ORDER BY timestamp DESC")
    suspend fun listActive(): List<CapturedMediaEntity>

    /** Every uri ever recorded (including soft-deleted), for backfill dedup. */
    @Query("SELECT contentUri FROM captured_media")
    suspend fun allUris(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CapturedMediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CapturedMediaEntity>)

    @Query("UPDATE captured_media SET deleted = 1 WHERE contentUri = :contentUri")
    suspend fun markDeleted(contentUri: String)
}