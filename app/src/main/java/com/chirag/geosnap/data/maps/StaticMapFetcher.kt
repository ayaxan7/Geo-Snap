package com.chirag.geosnap.data.maps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads the Google Static Maps thumbnail used in the location card.
 *
 * A single map image is cached keyed by its URL, so consecutive captures in the
 * same spot reuse the bitmap instead of re-hitting the network.
 */
@Singleton
class StaticMapFetcher @Inject constructor(
    private val mapsApi: MapsApi
) {

    private var cachedUrl: String? = null
    private var cachedBitmap: Bitmap? = null

    suspend fun fetch(url: String?): Bitmap? {
        if (url == null) return null
        if (url == cachedUrl) return cachedBitmap
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                mapsApi.staticMap(url).use { body ->
                    BitmapFactory.decodeStream(body.byteStream())
                }
            }.getOrNull()
        }
        if (bitmap != null) {
            cachedUrl = url
            cachedBitmap = bitmap
        }
        return bitmap
    }
}
