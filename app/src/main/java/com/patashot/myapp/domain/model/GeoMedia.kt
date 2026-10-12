package com.patashot.myapp.domain.model

import android.net.Uri

/**
 * A single piece of media captured by the app, along with the geolocation
 * metadata captured at the moment it was taken (when available).
 */
data class GeoMedia(
    val uri: Uri,
    val type: MediaType,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val displayName: String? = null
)