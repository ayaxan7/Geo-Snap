package com.chirag.geosnap.data.media

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chirag.geosnap.domain.model.MediaType

/**
 * Mirrors the fields of [com.chirag.geosnap.domain.model.GeoMedia] so the app
 * gallery can be served entirely from Room. [contentUri] is unique; the file
 * itself always lives in the device MediaStore.
 *
 * [deleted] is a soft-delete flag: delete only removes the app record, the file
 * stays in the device gallery, so backfill must never re-import a soft-deleted
 * uri (matching on the unique [contentUri] index handles that).
 */
@Entity(
    tableName = "captured_media",
    indices = [Index(value = ["contentUri"], unique = true)]
)
data class CapturedMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentUri: String,
    val type: MediaType,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val displayName: String? = null,
    val deleted: Boolean = false
)