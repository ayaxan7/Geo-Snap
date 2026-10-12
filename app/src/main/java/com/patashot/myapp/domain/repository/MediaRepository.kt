package com.patashot.myapp.domain.repository

import android.graphics.Bitmap
import com.patashot.myapp.domain.model.GeoCoordinate
import com.patashot.myapp.domain.model.GeoMedia
import java.io.File

/**
 * Owns persistence of every capture. Each capture always keeps a private copy
 * in app storage (the canonical URI the app gallery serves); when
 * `saveToMediaStore` is set the same bytes are also written to the public
 * device gallery.
 */
interface MediaRepository {

    /**
     * Persists a captured image [sourceFile] into the app's private gallery,
     * and, when [saveToMediaStore] is true, a copy into the public MediaStore.
     * When [coordinate] is provided the GPS metadata is embedded into the JPEG.
     * When [overlay] is provided the location card is burnt into the bottom of the
     * image, after [filter] (a `4x5` color-matrix recipe) is applied to the pixels,
     * before it is saved.
     *
     * @return the `content://` URI string of the app's own copy of the image.
     */
    suspend fun saveCapturedImage(
        sourceFile: File,
        coordinate: GeoCoordinate?,
        overlay: Bitmap? = null,
        filter: FloatArray? = null,
        saveToMediaStore: Boolean = true
    ): String

    /**
     * Persists a captured video [sourceFile] into the app's private gallery,
     * and, when [saveToMediaStore] is true, a copy into the public MediaStore.
     * When [overlay] is provided the location card is burnt into the video frames
     * and when [filter] is provided the color recipe is applied to every frame
     * (via a Media3 Transformer pass, before the card is composited) before it is
     * saved.
     *
     * @return the `content://` URI string of the app's own copy of the video.
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
     * Removes [contentUri] from the app gallery (Room). When it points at the
     * app's private copy, that file is physically deleted too. A copy in the
     * device gallery (if any) is left untouched.
     *
     * @return true when the media was removed successfully.
     */
    suspend fun deleteMedia(contentUri: String): Boolean
}