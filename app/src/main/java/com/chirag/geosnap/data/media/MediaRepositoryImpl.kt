package com.chirag.geosnap.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.chirag.geosnap.domain.model.GeoCoordinate
import com.chirag.geosnap.domain.model.GeoMedia
import com.chirag.geosnap.domain.model.MediaType
import com.chirag.geosnap.domain.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MediaStore-backed media persistence.
 *
 * Design notes:
 *  - On API 29+ media is written via [MediaStore.MediaColumns.RELATIVE_PATH] under
 *    `Pictures/GeoSnap` / `Movies/GeoSnap` (scoped storage, no permission needed).
 *  - On API 28 the DATA column is set to a public gallery path; writing there
 *    requires `WRITE_EXTERNAL_STORAGE`, which the app requests only on those
 *    devices (see AndroidManifest `maxSdkVersion="28"`).
 *  - Images are geotagged by writing EXIF GPS tags before they are copied into
 *    MediaStore, so metadata survives on the final file without the need to
 *    re-open the content URI for writes.
 *  - All returned URIs are `content://` URIs suitable for sharing and gallery.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaRepository {

    override suspend fun saveCapturedImage(
        sourceFile: File,
        coordinate: GeoCoordinate?,
        overlay: Bitmap?
    ): String = withContext(Dispatchers.IO) {
        if (coordinate != null) {
            writeGpsExifTo(sourceFile, coordinate)
        }
        val toPersist = if (overlay != null) {
            burnOverlayIntoImage(sourceFile, overlay, coordinate)
        } else {
            sourceFile
        }
        try {
            val now = System.currentTimeMillis()
            persist(
                sourceFile = toPersist,
                displayName = "${FILE_PREFIX}_${timestampText(now)}.jpg",
                mimeType = "image/jpeg",
                relativeFolder = "Pictures",
                publicDirectory = Environment.DIRECTORY_PICTURES,
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                now = now
            ).toString()
        } finally {
            if (toPersist !== sourceFile) toPersist.delete()
        }
    }

    override suspend fun saveCapturedVideo(
        sourceFile: File,
        coordinate: GeoCoordinate?,
        overlay: Bitmap?
    ): String = withContext(Dispatchers.IO) {
        // MP4 EXIF container support is inconsistent across devices; GPS is kept
        // in the GeoMedia model for videos so it can be written once an MP4 GPS
        // writer is introduced.
        val toPersist = if (overlay != null) {
            transcodeWithOverlay(sourceFile, overlay)
        } else {
            sourceFile
        }
        try {
            val now = System.currentTimeMillis()
            persist(
                sourceFile = toPersist,
                displayName = "${FILE_PREFIX}_${timestampText(now)}.mp4",
                mimeType = "video/mp4",
                relativeFolder = "Movies",
                publicDirectory = Environment.DIRECTORY_MOVIES,
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                now = now
            ).toString()
        } finally {
            if (toPersist !== sourceFile) toPersist.delete()
        }
    }

    override suspend fun getAppMedia(): List<GeoMedia> = withContext(Dispatchers.IO) {
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
        (images + videos).sortedByDescending { it.timestamp }
    }

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

    private fun writeGpsExifTo(file: File, coordinate: GeoCoordinate) {
        runCatching {
            val dateTime = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                .format(Date(coordinate.timestamp))
            val gpsDate = dateTime.substringBefore(' ')
            val gpsTime = dateTime.substringAfter(' ')

            val exif = ExifInterface(file.absolutePath)
            exif.setLatLong(coordinate.latitude, coordinate.longitude)
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, gpsDate)
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, gpsTime)
            exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, DIRECTORY_NAME)
            coordinate.altitude?.let { exif.setAltitude(it) }
            exif.saveAttributes()
        }
    }

    /**
     * Composites the (already rendered) location card [overlay] onto the
     * bottom-center of [sourceFile] and returns a new JPEG. The source is decoded
     * with `inSampleSize` so the long edge is capped at [MAX_DECODE_EDGE], any
     * EXIF rotation is baked upright, and GPS tags are copied onto the output.
     */
    private fun burnOverlayIntoImage(
        sourceFile: File,
        overlay: Bitmap,
        coordinate: GeoCoordinate?
    ): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, MAX_DECODE_EDGE)
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

        val cardWidth = (base.width * IMAGE_CARD_WIDTH_FRACTION).toInt().coerceAtLeast(1)
        val cardHeight =
            (cardWidth.toFloat() * overlay.height / overlay.width).toInt().coerceAtLeast(1)
        val marginX = (base.width * IMAGE_CARD_MARGIN_FRACTION_X).toInt()
        val marginBottom = (base.height * IMAGE_CARD_MARGIN_FRACTION_BOTTOM).toInt()
        val canvas = Canvas(base)
        canvas.drawBitmap(
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

        val output = File(context.cacheDir, "burned_${System.currentTimeMillis()}.jpg")
        FileOutputStream(output).use { stream ->
            base.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        }
        base.recycle()
        if (coordinate != null) {
            writeGpsExifTo(output, coordinate)
        }
        return output
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
     * Runs the video through a Media3 [Transformer] pass that overlays the
     * location card at the bottom-center of every frame. Audio passes through
     * untouched. Returns the transcoded temp file, deleting it is the caller's
     * responsibility once persisted.
     */
    private suspend fun transcodeWithOverlay(sourceFile: File, overlay: Bitmap): File =
        withContext(Dispatchers.Main.immediate) {
            val output = File(context.cacheDir, "burned_${System.currentTimeMillis()}.mp4")
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

            val (frameWidth, frameHeight) = videoFrameSize(sourceFile)
            val cardWidthPx =
                (frameWidth * VIDEO_CARD_WIDTH_FRACTION).toInt().coerceAtLeast(1)
            val cardHeightPx =
                (cardWidthPx.toFloat() * overlay.height / overlay.width).toInt().coerceAtLeast(1)
            val settings = StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(0f, -1f)
                .setOverlayFrameAnchor(0f, -1f)
                .setScale(cardWidthPx.toFloat() / frameWidth, cardHeightPx.toFloat() / frameHeight)
                .build()
            val effect = OverlayEffect(
                listOf(BitmapOverlay.createStaticBitmapOverlay(overlay, settings))
            )
            val mediaItem = EditedMediaItem.Builder(
                MediaItem.Builder().setUri(Uri.fromFile(sourceFile)).build()
            )
                .setEffects(Effects(emptyList(), listOf(effect)))
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

    private fun videoFrameSize(file: File): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: DEFAULT_VIDEO_WIDTH
            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: DEFAULT_VIDEO_HEIGHT
            width to height
        } catch (e: Exception) {
            DEFAULT_VIDEO_WIDTH to DEFAULT_VIDEO_HEIGHT
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

    private fun timestampText(millis: Long): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(millis))

    private companion object {
        const val DIRECTORY_NAME = "GeoSnap"
        const val FILE_PREFIX = "GeoSnap"
        const val MAX_DECODE_EDGE = 4096
        const val IMAGE_CARD_WIDTH_FRACTION = 0.7f
        const val IMAGE_CARD_MARGIN_FRACTION_X = 0.04f
        const val IMAGE_CARD_MARGIN_FRACTION_BOTTOM = 0.05f
        const val VIDEO_CARD_WIDTH_FRACTION = 0.55f
        const val DEFAULT_VIDEO_WIDTH = 1920
        const val DEFAULT_VIDEO_HEIGHT = 1080
        val SMOOTH_PAINT = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    }
}