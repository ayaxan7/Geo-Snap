package com.chirag.geosnap.domain.repository

import android.graphics.Bitmap
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
     * When [overlay] is provided the location card is burnt into the bottom of the
     * image before it is saved.
     *
     * @return the `content://` URI string of the saved image.
     */
    suspend fun saveCapturedImage(
        sourceFile: File,
        coordinate: GeoCoordinate?,
        overlay: Bitmap? = null
    ): String

    /**
     * Persists a captured video [sourceFile] into the public MediaStore gallery.
     * When [overlay] is provided the location card is burnt into the video frames
     * (via a Media3 Transformer pass) before it is saved.
     *
     * @return the `content://` URI string of the saved video.
     */
    suspend fun saveCapturedVideo(
        sourceFile: File,
        coordinate: GeoCoordinate?,
        overlay: Bitmap? = null
    ): String

    /** Queries the media the app created, newest first. */
    suspend fun getAppMedia(): List<GeoMedia>
}