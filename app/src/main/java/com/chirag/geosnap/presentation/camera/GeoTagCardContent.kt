package com.chirag.geosnap.presentation.camera

import com.chirag.geosnap.BuildConfig
import com.chirag.geosnap.domain.model.GeoCoordinate

/**
 * Capture-time snapshot of the location card contents. This pure data holder is
 * what gets rendered into a [android.graphics.Bitmap] and burned into photos and
 * videos, so burning it never depends on live UI state.
 */
data class GeoTagCardContent(
    val cityString: String?,
    val fullAddress: String?,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: Long,
    val staticMapUrl: String?
)

/**
 * Builds the Google Static Maps URL shown in the card thumbnail.
 * Returns null when there are no coordinates or no API key configured.
 */
fun staticMapUrl(latitude: Double?, longitude: Double?, sizePx: Int = 200): String? {
    val apiKey = BuildConfig.MAPS_API_KEY
    if (latitude == null || longitude == null || apiKey.isBlank()) return null
    return "https://maps.googleapis.com/maps/api/staticmap" +
        "?center=$latitude,$longitude&zoom=15&size=${sizePx}x${sizePx}" +
        "&markers=color:red%7C$latitude,$longitude&key=$apiKey"
}

/**
 * Snapshots the current UI state into a burnable card. The preferred
 * coordinates come from the [coordinate] captured at capture time, falling back
 * to the most recent fix held on the UI state.
 */
fun CameraUiState.toGeoTagCardContent(
    coordinate: GeoCoordinate? = null,
    mapSizePx: Int = 200
): GeoTagCardContent {
    val lat = coordinate?.latitude ?: currentLocation?.latitude
    val lng = coordinate?.longitude ?: currentLocation?.longitude
    return GeoTagCardContent(
        cityString = cityString,
        fullAddress = fullAddress,
        latitude = lat,
        longitude = lng,
        timestamp = coordinate?.timestamp ?: System.currentTimeMillis(),
        staticMapUrl = staticMapUrl(lat, lng, mapSizePx)
    )
}