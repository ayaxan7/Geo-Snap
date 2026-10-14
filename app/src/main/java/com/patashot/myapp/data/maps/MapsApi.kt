package com.patashot.myapp.data.maps

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Retrofit surface for the Google Maps endpoints PataShot calls: reverse
 * geocoding (JSON) for the location card address and static-map images
 * (fetched by full url so [staticMapUrl] stays the single source of truth
 * for map thumbnail requests).
 */
interface MapsApi {

    @GET("maps/api/geocode/json")
    suspend fun reverseGeocode(
        @Query("latlng") latLng: String,
        @Query("language") language: String,
        @Query("key") apiKey: String
    ): GeocodeResponse

    @Streaming
    @GET
    suspend fun staticMap(@Url url: String): ResponseBody
}
