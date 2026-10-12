package com.patashot.myapp.domain.model

import java.io.Serializable

/**
 * A location fix produced by the [com.patashot.myapp.domain.repository.LocationRepository].
 *
 * Kept deliberately separate from the camera/UI so geolocation can be consumed by
 * any feature (capture, gallery filters, etc.) without coupling to location APIs.
 * Serializable so the camera can park a pending capture in `SavedStateHandle`
 * across process death.
 */
data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable