package com.chirag.geosnap.data.media

import androidx.room.TypeConverter
import com.chirag.geosnap.domain.model.MediaType

/** Converts [MediaType] to a Room-storable string and back. */
class MediaTypeConverters {

    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType = MediaType.valueOf(value)
}