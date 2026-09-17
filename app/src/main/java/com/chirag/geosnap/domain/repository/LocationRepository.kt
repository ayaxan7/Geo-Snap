package com.chirag.geosnap.domain.repository

import com.chirag.geosnap.domain.model.GeoCoordinate

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
}