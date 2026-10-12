package com.patashot.myapp.domain.repository

import com.patashot.myapp.domain.model.GeoCoordinate

/**
 * Abstraction over the platform location services so callers (e.g. the camera
 * feature) can request the current location without depending on Android APIs.
 */
interface LocationRepository {

    /**
     * Returns the best available current location, or `null` when the permission
     * is missing, location is disabled, or a fix cannot be obtained in time.
     * Never throws for these expected conditions.
     */
    suspend fun getCurrentLocation(): GeoCoordinate?

    /**
     * Whether the device location service (master switch) is turned on.
     * Does not require the location permission and never throws for expected
     * conditions (e.g. a provider being unavailable).
     */
    suspend fun isLocationEnabled(): Boolean
}