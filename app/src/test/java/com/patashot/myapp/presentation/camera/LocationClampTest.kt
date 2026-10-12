package com.patashot.myapp.presentation.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationClampTest {

    private val center = LocationClamp.Coords(12.9716, 77.5946) // Bangalore

    @Test
    fun `points inside the radius are returned unchanged`() {
        val near = LocationClamp.Coords(12.9725, 77.5946) // ~100 m north
        assertEquals(near, LocationClamp.clampToRadius(near, center, 1000.0))
    }

    @Test
    fun `points just inside the radius boundary are returned unchanged`() {
        val nearBoundary = LocationClamp.Coords(center.latitude + 0.0089, center.longitude)
        val distance = LocationClamp.distanceMeters(
            center.latitude, center.longitude,
            nearBoundary.latitude, nearBoundary.longitude
        )
        assertTrue("expected ~990 m but was $distance", distance in 985.0..995.0)
        assertEquals(nearBoundary, LocationClamp.clampToRadius(nearBoundary, center, 1000.0))
    }

    @Test
    fun `points far outside are clamped onto the radius boundary`() {
        val far = LocationClamp.Coords(center.latitude + 0.5, center.longitude) // ~55 km north
        val clamped = LocationClamp.clampToRadius(far, center, 1000.0)

        val distance = LocationClamp.distanceMeters(
            center.latitude, center.longitude,
            clamped.latitude, clamped.longitude
        )
        assertEquals(1000.0, distance, 1.0)
        assertTrue(clamped.latitude < far.latitude)
    }

    @Test
    fun `clamped point lies along the bearing toward the original point`() {
        // Target straight east ~55 km away.
        val far = LocationClamp.Coords(center.latitude, center.longitude + 0.5)
        val clamped = LocationClamp.clampToRadius(far, center, 1000.0)

        // Longitude grew to the east; latitude barely moved relative to center.
        assertTrue(clamped.longitude > center.longitude)
        assertEquals(center.latitude, clamped.latitude, 0.01)
    }
}