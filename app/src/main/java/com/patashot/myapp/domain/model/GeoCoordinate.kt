package com.patashot.myapp.domain.model

/**
 * A location fix produced by the [com.patashot.myapp.domain.repository.LocationRepository].
 *
 * Kept deliberately separate from the camera/UI so geolocation can be consumed by
 * any feature (capture, gallery filters, etc.) without coupling to location APIs.
 */
data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)