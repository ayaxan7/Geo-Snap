package com.patashot.myapp.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.StaticOverlaySettings
import com.patashot.myapp.domain.model.NormalizedCropRect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.patashot.myapp.domain.model.GeoCoordinate
import com.patashot.myapp.domain.model.GeoMedia
import com.patashot.myapp.domain.model.MediaType
import com.patashot.myapp.domain.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap

/**
 * Media persistence for the app gallery.
 *
 * Design notes:
 *  - Every capture gets a permanent private copy under `filesDir/GeoSnap`;
 *    its FileProvider `content://` URI is what Room records, so deleting the
 *    photo from the device gallery never affects the app gallery (and vice
 *    versa).
 *  - When phone-gallery saving is enabled (`saveToMediaStore`) the same bytes
 *    are also written to the public MediaStore under `Pictures/GeoSnap` /
 *    `Movies/GeoSnap` (scoped storage needs no permission on API 29+; API 28
 *    requires `WRITE_EXTERNAL_STORAGE`, see AndroidManifest `maxSdkVersion`).
 *  - Images are geotagged by writing EXIF GPS tags before they are copied out,
 *    so metadata survives on the final files without re-opening URIs for write.
 *  - All returned URIs are `content://` URIs suitable for the gallery, Coil
 *    and sharing.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capturedMediaDao: CapturedMediaDao
) : MediaRepository {

    override suspend fun saveCapturedImage(
        sourceFile: File,
        coordinate: GeoCoordinate?,
        overlay: Bitmap?,
        filter: FloatArray?,
        saveToMediaStore: Boolean,
        rotationDegrees: Int,
        cropRect: NormalizedCropRect?
    ): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val hasModifications = overlay != null || filter != null || rotationDegrees % 360 != 0 || (cropRect != null && !cropRect.isDefault)
        val toPersist = if (hasModifications) {
            try {
                // Burn from the pristine CameraX JPEG (no prior EXIF rewrite so the
                // decoder sees the untouched file); GPS is written onto the output.
                burnOverlayIntoImage(sourceFile, overlay, filter, coordinate, rotationDegrees = rotationDegrees, cropRect = cropRect)
            } catch (t: Throwable) {
                Log.w(TAG, "Image processing failed, saving original", t)
                writeGpsExifTo(sourceFile, coordinate, now)
                sourceFile
            }
        } else {
            writeGpsExifTo(sourceFile, coordinate, now)
            sourceFile
        }
        try {
            val displayName = "${FILE_PREFIX}_${timestampText(now)}.jpg"
            // The app's own copy is the source of truth for the app gallery, so
            // the phone-gallery copy can be deleted without affecting it.
            val uri = persistInternal(toPersist, displayName).toString()
            if (saveToMediaStore) {
                runCatching {
                    persist(
                        sourceFile = toPersist,
                        displayName = displayName,
                        mimeType = "image/jpeg",
                        relativeFolder = "Pictures",
                        publicDirectory = Environment.DIRECTORY_PICTURES,
                        collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        now = now
                    )
                }.onFailure {
                    Log.w(TAG, "MediaStore copy failed for image, app copy kept", it)
                }
            }
            capturedMediaDao.upsert(
                CapturedMediaEntity(
                    contentUri = uri,
                    type = MediaType.IMAGE,
                    timestamp = now,
                    latitude = coordinate?.latitude,
                    longitude = coordinate?.longitude,
                    displayName = displayName
                )
            )
            uri
        } finally {
            if (toPersist !== sourceFile) toPersist.delete()
        }
    }

    override suspend fun saveCapturedVideo(
        sourceFile: File,
        coordinate: GeoCoordinate?,
        overlay: Bitmap?,
        filter: FloatArray?,
        saveToMediaStore: Boolean,
        rotationDegrees: Int,
        cropRect: NormalizedCropRect?
    ): String = withContext(Dispatchers.IO) {
        // MP4 EXIF container support is inconsistent across devices; GPS is kept
        // in the GeoMedia model for videos so it can be written once an MP4 GPS
        // writer is introduced.
        val hasModifications = overlay != null || filter != null || rotationDegrees % 360 != 0 || (cropRect != null && !cropRect.isDefault)
        val toPersist = if (hasModifications) {
            try {
                transcodeWithOverlay(sourceFile, overlay, filter, rotationDegrees = rotationDegrees, cropRect = cropRect)
            } catch (t: Throwable) {
                Log.w(TAG, "Video transcode failed, saving original", t)
                sourceFile
            }
        } else {
            sourceFile
        }
        try {
            val now = System.currentTimeMillis()
            val displayName = "${FILE_PREFIX}_${timestampText(now)}.mp4"
            // The app's own copy is the source of truth for the app gallery, so
            // the phone-gallery copy can be deleted without affecting it.
            val uri = persistInternal(toPersist, displayName).toString()
            if (saveToMediaStore) {
                runCatching {
                    persist(
                        sourceFile = toPersist,
                        displayName = displayName,
                        mimeType = "video/mp4",
                        relativeFolder = "Movies",
                        publicDirectory = Environment.DIRECTORY_MOVIES,
                        collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        now = now
                    )
                }.onFailure {
                    Log.w(TAG, "MediaStore copy failed for video, app copy kept", it)
                }
            }
            capturedMediaDao.upsert(
                CapturedMediaEntity(
                    contentUri = uri,
                    type = MediaType.VIDEO,
                    timestamp = now,
                    latitude = coordinate?.latitude,
                    longitude = coordinate?.longitude,
                    displayName = displayName
                )
            )
            uri
        } finally {
            if (toPersist !== sourceFile) toPersist.delete()
        }
    }

    override suspend fun prepareShareCopy(
        sourceFile: File,
        coordinate: GeoCoordinate?,
        overlay: Bitmap?,
        filter: FloatArray?,
        isVideo: Boolean,
        rotationDegrees: Int,
        cropRect: NormalizedCropRect?
    ): String = withContext(Dispatchers.IO) {
        pruneShareCopies()
        val ext = if (isVideo) "mp4" else "jpg"
        val output = File(shareDir(), "share_${System.currentTimeMillis()}.$ext")
        val hasModifications = overlay != null || filter != null || rotationDegrees % 360 != 0 || (cropRect != null && !cropRect.isDefault)
        val burned = try {
            when {
                isVideo && hasModifications ->
                    transcodeWithOverlay(sourceFile, overlay, filter, output, rotationDegrees, cropRect)
                !isVideo && hasModifications ->
                    burnOverlayIntoImage(sourceFile, overlay, filter, coordinate, output, rotationDegrees, cropRect)
                else -> {
                    // Nothing to burn: hand out a copy so the pending source file
                    // is never mutated (or deleted) while the share sheet holds it.
                    sourceFile.copyTo(output, overwrite = true)
                    if (!isVideo) writeGpsExifTo(output, coordinate)
                    output
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Share burn failed, sharing the original", t)
            output.delete()
            // Fall back to the untouched source; the share sheet only reads it.
            return@withContext FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                sourceFile
            ).toString()
        }
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            burned
        ).toString()
    }

    private fun shareDir(): File = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }

    /** Share copies are disposable; anything older than [SHARE_COPY_TTL_MS] goes. */
    private fun pruneShareCopies() {
        val cutoff = System.currentTimeMillis() - SHARE_COPY_TTL_MS
        runCatching {
            shareDir().listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) file.delete()
            }
        }
    }

    override suspend fun getAppMedia(): List<GeoMedia> = withContext(Dispatchers.IO) {
        backfillFromMediaStore()
        capturedMediaDao.listActive()
            .map(::toGeoMedia)
            .sortedByDescending { it.timestamp }
    }

override suspend fun deleteMedia(contentUri: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        capturedMediaDao.markDeleted(contentUri)
        deleteAppCopy(contentUri)
    }.isSuccess
}

    override suspend fun saveToDeviceGallery(media: GeoMedia): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val isVideo = media.type == MediaType.VIDEO
            val collection = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val relativeFolder = if (isVideo) "Movies" else "Pictures"
            val publicDir = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
            val extension = if (isVideo) "mp4" else "jpg"
            val displayName = media.displayName?.takeIf { it.isNotBlank() }
                ?: "${FILE_PREFIX}_${timestampText(media.timestamp)}.$extension"
            val now = media.timestamp

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.DATE_ADDED, now / 1000)
                put(MediaStore.MediaColumns.DATE_TAKEN, now)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativeFolder/$DIRECTORY_NAME")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                } else {
                    @Suppress("DEPRECATION")
                    val pubDir = Environment.getExternalStoragePublicDirectory(publicDir)
                    @Suppress("DEPRECATION")
                    val targetDir = File(pubDir, DIRECTORY_NAME).apply { mkdirs() }
                    @Suppress("DEPRECATION")
                    put(MediaStore.MediaColumns.DATA, File(targetDir, displayName).absolutePath)
                }
            }

            val targetUri = resolver.insert(collection, values)
                ?: throw IOException("MediaStore insert failed")

            val inserted = runCatching {
                resolver.openInputStream(media.uri)?.use { input ->
                    resolver.openOutputStream(targetUri, "w")?.use { output ->
                        input.copyTo(output)
                    } ?: throw IOException("Could not open output stream for $targetUri")
                } ?: throw IOException("Could not open input stream for ${media.uri}")
            }

            if (inserted.isFailure) {
                resolver.delete(targetUri, null, null)
                throw inserted.exceptionOrNull() ?: IOException("Insert failed")
            }

            if (!isVideo) {
                runCatching {
                    resolver.openFileDescriptor(targetUri, "rw")?.use { pfd ->
                        val exif = ExifInterface(pfd.fileDescriptor)
                        writeExifMetadata(
                            exif = exif,
                            latitude = media.latitude,
                            longitude = media.longitude,
                            altitude = null,
                            timestamp = now
                        )
                        exif.saveAttributes()
                    }
                }.onFailure {
                    Log.w(TAG, "Failed to write EXIF attributes to MediaStore image", it)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    targetUri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
            }
            true
        }.onFailure {
            Log.e(TAG, "saveToDeviceGallery failed", it)
        }.getOrDefault(false)
    }

/**
 * Physically removes the app's private copy of [contentUri] (if the URI points
 * at one). MediaStore copies are deliberately left untouched.
 */
private fun deleteAppCopy(contentUri: String) {
    val uri = runCatching { contentUri.toUri() }.getOrNull() ?: return
    if (uri.authority != "${context.packageName}.fileprovider") return
    val segments = uri.pathSegments
    if (segments.size == 2 && segments[0] == "app_media") {
        runCatching {
            File(context.filesDir, DIRECTORY_NAME).resolve(segments[1]).delete()
        }
    }
}

    /**
     * One-way sync from MediaStore into Room so captures made before the app
     * kept its own copies appear in the app gallery anyway. Media the user
     * soft-deleted is never re-imported because its uri is already present in
     * Room (upsert is a no-op for the unique contentUri index, and `deleted` is
     * never flipped back). New captures are indexed by their app-copy row, so a
     * MediaStore item whose name is already known is skipped instead of
     * producing a duplicate (both copies share a unique display name).
     */
    private suspend fun backfillFromMediaStore() {
        val images = queryCollection(
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            type = MediaType.IMAGE,
            latitudeColumn = MediaStore.Images.ImageColumns.LATITUDE,
            longitudeColumn = MediaStore.Images.ImageColumns.LONGITUDE
        )
        val videos = queryCollection(
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            type = MediaType.VIDEO,
            latitudeColumn = MediaStore.Video.VideoColumns.LATITUDE,
            longitudeColumn = MediaStore.Video.VideoColumns.LONGITUDE
        )
        val mediaStore = images + videos
        val knownUris = capturedMediaDao.allUris().toSet()
        val knownNames = capturedMediaDao.allDisplayNames().toSet()
        val missing = mediaStore.filter {
            it.uri.toString() !in knownUris && it.displayName !in knownNames
        }
        if (missing.isNotEmpty()) {
            capturedMediaDao.upsertAll(missing.map(::toEntity))
        }
    }

    private fun toEntity(media: GeoMedia): CapturedMediaEntity =
        CapturedMediaEntity(
            contentUri = media.uri.toString(),
            type = media.type,
            timestamp = media.timestamp,
            latitude = media.latitude,
            longitude = media.longitude,
            displayName = media.displayName
        )

    private fun toGeoMedia(entity: CapturedMediaEntity): GeoMedia =
        GeoMedia(
            uri = entity.contentUri.toUri(),
            type = entity.type,
            timestamp = entity.timestamp,
            latitude = entity.latitude,
            longitude = entity.longitude,
            displayName = entity.displayName
        )

    private fun persist(
        sourceFile: File,
        displayName: String,
        mimeType: String,
        relativeFolder: String,
        publicDirectory: String,
        collection: Uri,
        now: Long
    ): Uri {
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.DATE_ADDED, now / 1000)
            put(MediaStore.MediaColumns.DATE_TAKEN, now)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativeFolder/$DIRECTORY_NAME")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val publicDir = Environment.getExternalStoragePublicDirectory(publicDirectory)
                @Suppress("DEPRECATION")
                val targetDir = File(publicDir, DIRECTORY_NAME).apply { mkdirs() }
                @Suppress("DEPRECATION")
                put(MediaStore.MediaColumns.DATA, File(targetDir, displayName).absolutePath)
            }
        }

        val uri = resolver.insert(collection, values)
            ?: throw IOException("MediaStore insert failed")

        val inserted = runCatching {
            resolver.openOutputStream(uri, "w")?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("Could not open output stream for $uri")
        }

        if (inserted.isFailure) {
            Log.e(TAG, "MediaStore write failed for $uri", inserted.exceptionOrNull())
            resolver.delete(uri, null, null)
            throw inserted.exceptionOrNull() ?: IOException("Insert failed")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
        }

        return uri
    }

    private fun persistInternal(sourceFile: File, displayName: String): Uri {
        val targetDir = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }
        val destFile = File(targetDir, displayName)
        sourceFile.copyTo(destFile, overwrite = true)
        // A file:// URI in a ClipData trips FileUriExposedException on API 24+,
        // so quick-shared captures go through FileProvider.
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            destFile
        )
    }

    private fun writeExifMetadata(
        exif: ExifInterface,
        latitude: Double?,
        longitude: Double?,
        altitude: Double? = null,
        timestamp: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ) {
        val localFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
            this.timeZone = timeZone
        }
        val localDateTime = localFormat.format(Date(timestamp))
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, localDateTime)
        exif.setAttribute(ExifInterface.TAG_DATETIME, localDateTime)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, localDateTime)

        // Dynamic ISO-8601 offset (e.g. "+05:30", "-04:00") matching the active timezone
        val offsetFormat = SimpleDateFormat("XXX", Locale.US).apply {
            this.timeZone = timeZone
        }
        val offsetString = offsetFormat.format(Date(timestamp))
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offsetString)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offsetString)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offsetString)

        if (latitude != null && longitude != null) {
            val utcFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
                this.timeZone = TimeZone.getTimeZone("UTC")
            }
            val utcDateTime = utcFormat.format(Date(timestamp))
            val gpsDate = utcDateTime.substringBefore(' ')
            val gpsTime = utcDateTime.substringAfter(' ')

            exif.setLatLong(latitude, longitude)
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, gpsDate)
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, gpsTime)
            exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, DIRECTORY_NAME)
            altitude?.let { exif.setAltitude(it) }
        }
    }

    private fun writeGpsExifTo(
        file: File,
        coordinate: GeoCoordinate?,
        timestamp: Long = coordinate?.timestamp ?: System.currentTimeMillis()
    ) {
        runCatching {
            val exif = ExifInterface(file.absolutePath)
            writeExifMetadata(
                exif = exif,
                latitude = coordinate?.latitude,
                longitude = coordinate?.longitude,
                altitude = coordinate?.altitude,
                timestamp = timestamp
            )
            exif.saveAttributes()
        }
    }

    /**
     * Composites the (already rendered) location card [overlay] onto the
     * bottom-center of [sourceFile] and returns a new JPEG, applying the color
     * [filter] recipe to the pixels first. The source is decoded with
     * `inSampleSize` so the long edge is capped at [MAX_DECODE_EDGE], any EXIF
     * rotation is baked upright, and GPS tags are copied onto the output.
     */
    private fun burnOverlayIntoImage(
        sourceFile: File,
        overlay: Bitmap?,
        filter: FloatArray?,
        coordinate: GeoCoordinate?,
        output: File = File(context.cacheDir, "burned_${System.currentTimeMillis()}.jpg"),
        rotationDegrees: Int = 0,
        cropRect: NormalizedCropRect? = null
    ): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, MAX_DECODE_EDGE)
            // decodeFile() returns an immutable bitmap by default (API 26+); the canvas
            // burn below needs to draw onto it, so request a mutable allocation.
            inMutable = true
        }
        var base = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions)
            ?: throw IOException("Failed to decode ${sourceFile.absolutePath}")
        val orientation = runCatching {
            ExifInterface(sourceFile.absolutePath)
                .getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val upright = rotateToUpright(base, orientation)
        if (upright !== base) {
            base.recycle()
            base = upright
        }

        val normRot = (rotationDegrees % 360 + 360) % 360
        if (normRot != 0) {
            val rotMatrix = Matrix().apply { postRotate(normRot.toFloat()) }
            val rotated = Bitmap.createBitmap(base, 0, 0, base.width, base.height, rotMatrix, true)
            if (rotated !== base) {
                base.recycle()
                base = rotated
            }
        }

        if (cropRect != null && !cropRect.isDefault) {
            val cropL = (cropRect.left.coerceIn(0f, 1f) * base.width).toInt().coerceIn(0, base.width - 1)
            val cropT = (cropRect.top.coerceIn(0f, 1f) * base.height).toInt().coerceIn(0, base.height - 1)
            val cropR = (cropRect.right.coerceIn(0f, 1f) * base.width).toInt().coerceIn(cropL + 1, base.width)
            val cropB = (cropRect.bottom.coerceIn(0f, 1f) * base.height).toInt().coerceIn(cropT + 1, base.height)
            val cropped = Bitmap.createBitmap(base, cropL, cropT, cropR - cropL, cropB - cropT)
            if (cropped !== base) {
                base.recycle()
                base = cropped
            }
        }

        if (filter != null) {
            val filtered = applyRecipe(base, filter)
            base.recycle()
            base = filtered
        }

        if (overlay != null) {
            val cardWidth = (base.width * CARD_WIDTH_FRACTION).toInt().coerceAtLeast(1)
            val cardHeight =
                (cardWidth.toFloat() * overlay.height / overlay.width).toInt().coerceAtLeast(1)
            val marginX = (base.width - cardWidth) / 2
            val marginBottom = (base.height * CARD_MARGIN_BOTTOM_FRACTION).toInt()
            Canvas(base).drawBitmap(
                overlay,
                Rect(0, 0, overlay.width, overlay.height),
                Rect(
                    marginX,
                    base.height - marginBottom - cardHeight,
                    marginX + cardWidth,
                    base.height - marginBottom
                ),
                SMOOTH_PAINT
            )
        }

        FileOutputStream(output).use { stream ->
            base.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        }
        base.recycle()
        writeGpsExifTo(output, coordinate, coordinate?.timestamp ?: System.currentTimeMillis())
        return output
    }

    /** Renders [source] onto a fresh bitmap through the color recipe. */
    private fun applyRecipe(source: Bitmap, recipe: FloatArray): Bitmap {
        val filtered =
            createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(recipe))
        }
        Canvas(filtered).drawBitmap(source, 0f, 0f, paint)
        return filtered
    }

    private fun rotateToUpright(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun computeSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sampleSize = 1
        while (width / sampleSize > maxEdge || height / sampleSize > maxEdge) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * Runs the video through a Media3 [Transformer] pass that first applies the
     * color [filter] recipe to every frame and then overlays the location card
     * at the bottom-center. Audio passes through untouched. Returns the
     * transcoded temp file, deleting it is the caller's responsibility once
     * persisted.
     */
    private suspend fun transcodeWithOverlay(
        sourceFile: File,
        overlay: Bitmap?,
        filter: FloatArray?,
        output: File = File(context.cacheDir, "burned_${System.currentTimeMillis()}.mp4"),
        rotationDegrees: Int = 0,
        cropRect: NormalizedCropRect? = null
    ): File =
        withContext(Dispatchers.Main.immediate) {
            val finished = CompletableDeferred<Unit>()
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        finished.complete(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        finished.completeExceptionally(exportException)
                    }
                })
                .build()

            val normRot = (rotationDegrees % 360 + 360) % 360
            val (rawDisplayWidth, rawDisplayHeight) = videoFrameSize(sourceFile)
            val (rotWidth, rotHeight) = if (normRot == 90 || normRot == 270) {
                rawDisplayHeight to rawDisplayWidth
            } else {
                rawDisplayWidth to rawDisplayHeight
            }
            val finalFrameWidth = if (cropRect != null && !cropRect.isDefault) {
                ((cropRect.right - cropRect.left).coerceIn(0.05f, 1f) * rotWidth).toInt().coerceAtLeast(1)
            } else {
                rotWidth
            }

            // Media3 composits BitmapOverlays in the output (post-rotation/display)
            // frame space, so size the card against the rotation-aware display
            // width so it occupies the same ~90% as the on-screen preview.
            val overlayEffect = overlay?.let { cardOverlay ->
                val cardWidthPx =
                    (finalFrameWidth * CARD_WIDTH_FRACTION).toInt().coerceAtLeast(1)
                val cardHeightPx =
                    (cardWidthPx.toFloat() * cardOverlay.height / cardOverlay.width)
                        .toInt()
                        .coerceAtLeast(1)
                // Bottom margin keeps the card just above the frame edge (~5%).
                // NDC y=-1 is the frame bottom, so 5% up is -1 + 2*0.05.
                val bottomAnchor = -1f + 2f * CARD_MARGIN_BOTTOM_FRACTION
                val settings = StaticOverlaySettings.Builder()
                    .setBackgroundFrameAnchor(0f, bottomAnchor)
                    .setOverlayFrameAnchor(0f, -1f)
                    .setScale(
                        cardWidthPx.toFloat() / cardOverlay.width,
                        cardHeightPx.toFloat() / cardOverlay.height
                    )
                    .build()
                OverlayEffect(
                    listOf(
                        BitmapOverlay.createStaticBitmapOverlay(cardOverlay, settings)
                    )
                )
            }
            val videoEffects = buildList {
                if (normRot != 0) {
                    // Media3 ScaleAndRotateTransformation takes counterclockwise degrees.
                    // normRot is clockwise degrees, so convert to counterclockwise.
                    val ccwDegrees = (360 - normRot) % 360
                    add(ScaleAndRotateTransformation.Builder().setRotationDegrees(ccwDegrees.toFloat()).build())
                }
                if (cropRect != null && !cropRect.isDefault) {
                    val l = (-1f + 2f * cropRect.left.coerceIn(0f, 0.99f)).coerceIn(-1f, 1f)
                    val r = (-1f + 2f * cropRect.right.coerceIn(0.01f, 1f)).coerceIn(l + 0.01f, 1f)
                    val b = (1f - 2f * cropRect.bottom.coerceIn(0.01f, 1f)).coerceIn(-1f, 1f)
                    val t = (1f - 2f * cropRect.top.coerceIn(0f, 0.99f)).coerceIn(b + 0.01f, 1f)
                    add(Crop(l, r, b, t))
                }
                if (filter != null) add(FilterRgbMatrix(filter))
                if (overlayEffect != null) add(overlayEffect)
            }
            val mediaItem = EditedMediaItem.Builder(
                MediaItem.Builder().setUri(Uri.fromFile(sourceFile)).build()
            )
                .setEffects(Effects(emptyList(), videoEffects))
                .build()

            try {
                transformer.start(mediaItem, output.absolutePath)
                finished.await()
            } catch (t: Throwable) {
                output.delete()
                throw t
            }
            output
        }

    /**
     * Returns the DISPLAYED (rotation-aware) frame size of the video. Portrait
     * recordings are often stored with landscape pixel dimensions plus a 90/270
     * degree rotation matrix, so raw metadata width/height are swapped to match
     * what the user actually sees.
     */
    private fun videoFrameSize(file: File): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val rawWidth = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: DEFAULT_VIDEO_WIDTH
            val rawHeight = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: DEFAULT_VIDEO_HEIGHT
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            return if (rotation == 90 || rotation == 270) {
                rawHeight to rawWidth
            } else {
                rawWidth to rawHeight
            }
        } catch (e: Exception) {
            return DEFAULT_VIDEO_WIDTH to DEFAULT_VIDEO_HEIGHT
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun queryCollection(
        collection: Uri,
        type: MediaType,
        latitudeColumn: String,
        longitudeColumn: String
    ): List<GeoMedia> {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_TAKEN,
            latitudeColumn,
            longitudeColumn
        )

        val selection: String
        val selectionArgs = arrayOf("%$DIRECTORY_NAME%")
        selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.MediaColumns.DATA} LIKE ?"
        }

        val latitudeIndex = projection.indexOf(latitudeColumn)
        val longitudeIndex = projection.indexOf(longitudeColumn)

        return runCatching {
            resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                buildList {
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val addedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                    val takenIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIndex)
                        val name = cursor.getString(nameIndex) ?: ""
                        val dateAdded = cursor.getLong(addedIndex) * 1000
                        val dateTaken = cursor.getLong(takenIndex)
                        val lat = if (latitudeIndex >= 0) cursor.getDoubleOrNull(latitudeIndex) else null
                        val lon = if (longitudeIndex >= 0) cursor.getDoubleOrNull(longitudeIndex) else null
                        add(
                            GeoMedia(
                                uri = ContentUris.withAppendedId(collection, id),
                                type = type,
                                timestamp = if (dateTaken > 0) dateTaken else dateAdded,
                                latitude = lat,
                                longitude = lon,
                                displayName = name
                            )
                        )
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun android.database.Cursor.getDoubleOrNull(columnIndex: Int): Double? =
        if (isNull(columnIndex)) null else getDouble(columnIndex)

    /** Millisecond resolution keeps two same-second captures from colliding. */
    private fun timestampText(millis: Long): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(millis))

    private companion object {
        const val TAG = "MediaRepositoryImpl"
        const val DIRECTORY_NAME = "GeoSnap"
        const val FILE_PREFIX = "GeoSnap"
        const val SHARE_DIR = "share"
        const val SHARE_COPY_TTL_MS = 24 * 60 * 60 * 1000L
        const val MAX_DECODE_EDGE = 12288
        const val CARD_WIDTH_FRACTION = 0.9f
        const val CARD_MARGIN_BOTTOM_FRACTION = 0.05f
        const val DEFAULT_VIDEO_WIDTH = 1920
        const val DEFAULT_VIDEO_HEIGHT = 1080
        val SMOOTH_PAINT = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    }
}