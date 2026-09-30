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
     * image, after [filter] (a `4x5` color-matrix recipe) is applied to the pixels,
     * before it is saved.
     *
     * @return the `content://` URI string of the saved image.
     */
    suspend fun saveCapturedImage(
        sourceFile: File,
        coordinate: GeoCoordinate?,
        overlay: Bitmap? = null,
        filter: FloatArray? = null,
        saveToMediaStore: Boolean = true
    ): String

    /**
     * Persists a captured video [sourceFile] into the public MediaStore gallery.
     * When [overlay] is provided the location card is burnt into the video frames
     * and when [filter] is provided the color recipe is applied to every frame
     * (via a Media3 Transformer pass, before the card is composited) before it is
     * saved.
     *
     * @return the `content://` URI string of the saved video.
     */
    suspend fun saveCapturedVideo(
        sourceFile: File,
        coordinate: GeoCoordinate?,
        overlay: Bitmap? = null,
        filter: FloatArray? = null,
        saveToMediaStore: Boolean = true
    ): String

    /** Queries the app gallery (Room-backed), newest first. */
    suspend fun getAppMedia(): List<GeoMedia>

    /**
     * Removes [contentUri] from the app gallery (Room) only. The file itself
     * stays in the device MediaStore.
     *
     * @return true when the media was removed successfully.
     */
    suspend fun deleteMedia(contentUri: String): Boolean
}