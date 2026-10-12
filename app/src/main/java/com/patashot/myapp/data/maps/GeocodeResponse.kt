package com.patashot.myapp.data.maps

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Subset of the Google Geocoding API response GeoSnap actually needs. */
@JsonClass(generateAdapter = true)
data class GeocodeResponse(
    val status: String?,
    val results: List<GeocodeResult>?
)

@JsonClass(generateAdapter = true)
data class GeocodeResult(
    @Json(name = "formatted_address") val formattedAddress: String?
)
