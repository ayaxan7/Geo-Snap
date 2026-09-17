package com.chirag.geosnap.domain.repository

import com.chirag.geosnap.domain.model.GeoCoordinate
import com.chirag.geosnap.domain.model.GeoMedia
import java.io.File

/**
 * Owns all persistence of captured media into the device MediaStore.
 */
interface MediaRepository {

    /**
     * Persists a captured image [sourceFile] into the public MediaStore gallery.
     * When [coordinate] is provided the GPS metadata is embedded into the JPEG.
     *
     * @return the `content://` URI string of the saved image.
     */
    suspend fun saveCapturedImage(sourceFile: File, coordinate: GeoCoordinate?): String

    /**
     * Persists a captured video [sourceFile] into the public MediaStore gallery.
     *
     * @return the `content://` URI string of the saved video.
     */
    suspend fun saveCapturedVideo(sourceFile: File, coordinate: GeoCoordinate?): String

    /** Queries the media the app created, newest first. */
    suspend fun getAppMedia(): List<GeoMedia>
}