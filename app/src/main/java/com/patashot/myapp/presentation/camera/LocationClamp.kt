package com.patashot.myapp.presentation.camera

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure geodesic helpers used by [EditLocationScreen] to keep the selectable
 * location inside a fixed radius circle. Kept free of Android dependencies so
 * they can be unit-tested.
 */
object LocationClamp {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /** Great-circle distance between two coordinates, in meters. */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    data class Coords(val latitude: Double, val longitude: Double)

    /**
     * Returns [target] unchanged when it lies within [radiusMeters] of
     * [center]; otherwise the point on the circle's boundary along the bearing
     * from [center] toward [target] (so a far drag lands exactly on the edge).
     */
    fun clampToRadius(
        target: Coords,
        center: Coords,
        radiusMeters: Double
    ): Coords {
        val distance = distanceMeters(
            center.latitude, center.longitude,
            target.latitude, target.longitude
        )
        if (distance <= radiusMeters) return target

        val d = radiusMeters / EARTH_RADIUS_METERS
        val lat1 = Math.toRadians(center.latitude)
        val lng1 = Math.toRadians(center.longitude)
        val lat2 = Math.toRadians(target.latitude)
        val lng2 = Math.toRadians(target.longitude)

        val deltaLng = lng2 - lng1
        val bearing = atan2(
            sin(deltaLng) * cos(lat2),
            cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLng)
        )

        val clampedLat = asin(
            sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(bearing)
        )
        val clampedLng = lng1 + atan2(
            sin(bearing) * sin(d) * cos(lat1),
            cos(d) - sin(lat1) * sin(clampedLat)
        )

        return Coords(
            latitude = Math.toDegrees(clampedLat),
            longitude = Math.toDegrees(clampedLng)
        )
    }
}