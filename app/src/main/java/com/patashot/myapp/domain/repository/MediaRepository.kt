package com.patashot.myapp.domain.repository

import android.graphics.Bitmap
import com.patashot.myapp.data.media.CapturedMediaEntity
import com.patashot.myapp.domain.model.GeoCoordinate
import com.patashot.myapp.domain.model.GeoMedia
import com.patashot.myapp.domain.model.NormalizedCropRect
import com.patashot.myapp.presentation.camera.GeoTagCardContent
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
        saveToMediaStore: Boolean = true,
        rotationDegrees: Int = 0,
        cropRect: NormalizedCropRect? = null,
        cardContent: GeoTagCardContent? = null
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
        saveToMediaStore: Boolean = true,
        rotationDegrees: Int = 0,
        cropRect: NormalizedCropRect? = null,
        cardContent: GeoTagCardContent? = null
    ): String

    /**
     * Burns [overlay] and [filter] into a disposable copy of [sourceFile] for
     * sharing: the location card is composited into the media, the color recipe
     * is applied, and (for images) GPS metadata is written from [coordinate].
     * Nothing is persisted to the app gallery or MediaStore, and [sourceFile]
     * itself is never modified, so a pending preview can still be edited,
     * re-shared or saved afterwards.
     *
     * @return the `content://` URI of the shareable copy.
     */
    suspend fun prepareShareCopy(
        sourceFile: File,
        coordinate: GeoCoordinate?,
        overlay: Bitmap? = null,
        filter: FloatArray? = null,
        isVideo: Boolean,
        rotationDegrees: Int = 0,
        cropRect: NormalizedCropRect? = null
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

    /**
     * Exports a media item from the app's gallery to the public device gallery (MediaStore).
     *
     * @return true when the media was exported successfully.
     */
    suspend fun saveToDeviceGallery(media: GeoMedia): Boolean

    /** Fetches the full database entity including cleanPath and location details by database ID. */
    suspend fun getMediaEntityById(id: Long): CapturedMediaEntity?

    /** Resolves the local file corresponding to an app media content URI. */
    suspend fun resolveMediaFile(contentUri: String): File?

    /**
     * Overwrites an existing captured media item in the gallery with newly rendered
     * modifications (location, overlay, filter, crop, rotation) applied to the clean original.
     */
    suspend fun updateCapturedMedia(
        id: Long,
        coordinate: GeoCoordinate?,
        overlay: Bitmap?,
        filter: FloatArray?,
        rotationDegrees: Int,
        cropRect: NormalizedCropRect?,
        cardContent: GeoTagCardContent
    ): Boolean
}
