package com.chirag.geosnap.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.chirag.geosnap.domain.model.GeoCoordinate
import com.chirag.geosnap.domain.model.GeoMedia
import com.chirag.geosnap.domain.model.MediaType
import com.chirag.geosnap.domain.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
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
@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaRepository {

    override suspend fun saveCapturedImage(
        sourceFile: File,
        coordinate: GeoCoordinate?
    ): String = withContext(Dispatchers.IO) {
        if (coordinate != null) {
            writeGpsExifTo(sourceFile, coordinate)
        }
        val now = System.currentTimeMillis()
        persist(
            sourceFile = sourceFile,
            displayName = "${FILE_PREFIX}_${timestampText(now)}.jpg",
            mimeType = "image/jpeg",
            relativeFolder = "Pictures",
            publicDirectory = Environment.DIRECTORY_PICTURES,
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            now = now
        ).toString()
    }

    override suspend fun saveCapturedVideo(
        sourceFile: File,
        coordinate: GeoCoordinate?
    ): String = withContext(Dispatchers.IO) {
        // MP4 EXIF container support is inconsistent across devices; GPS is kept
        // in the GeoMedia model for videos so it can be written once an MP4 GPS
        // writer is introduced.
        val now = System.currentTimeMillis()
        persist(
            sourceFile = sourceFile,
            displayName = "${FILE_PREFIX}_${timestampText(now)}.mp4",
            mimeType = "video/mp4",
            relativeFolder = "Movies",
            publicDirectory = Environment.DIRECTORY_MOVIES,
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            now = now
        ).toString()
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
    }
}