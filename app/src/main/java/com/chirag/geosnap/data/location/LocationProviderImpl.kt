package com.chirag.geosnap.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.HandlerThread
import android.os.Looper
import androidx.core.content.ContextCompat
import com.chirag.geosnap.domain.model.GeoCoordinate
import com.chirag.geosnap.domain.repository.LocationRepository
import java.util.concurrent.TimeUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Framework [LocationManager]-based implementation.
 *
 * Uses the last known fix when it is fresh enough, otherwise requests a single
 * new update with a bounded timeout. Returns `null` (rather than throwing) for
 * every expected failure: missing permission, disabled provider, or timeout.
 */
@Singleton
class LocationProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationRepository {

    private val locationManager: LocationManager
        get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val singleFixDispatcher = SingleFixDispatcher()

    override suspend fun getCurrentLocation(): GeoCoordinate? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) return@withContext null

        val bestLastKnown = locationManager.allProviders
            .asSequence()
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filter { it.isFreshEnough() }
            .maxByOrNull { it.time }

        bestLastKnown?.toGeoCoordinate() ?: requestSingleFix()
    }

    override suspend fun isLocationEnabled(): Boolean = withContext(Dispatchers.IO) {
        PROVIDER_PRIORITY.any { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    private suspend fun requestSingleFix(): GeoCoordinate? = withTimeoutOrNull(FIX_TIMEOUT_MS) {
        val provider = enabledProvider() ?: return@withTimeoutOrNull null
        if (!hasLocationPermission()) return@withTimeoutOrNull null

        suspendCancellableCoroutine { continuation ->
            val listener = ContinuationLocationListener { coordinate ->
                if (!continuation.isCancelled) {
                    continuation.resume(coordinate)
                }
            }
            val looper = singleFixDispatcher.ensureStarted()
            moveToMainThread { locationManager.requestSingleUpdate(provider, listener, looper) }
            continuation.invokeOnCancellation {
                runCatching { moveToMainThread { locationManager.removeUpdates(listener) } }
            }
        }
    }

    private fun enabledProvider(): String? = PROVIDER_PRIORITY
        .firstOrNull { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    private fun Location.isFreshEnough(): Boolean =
        System.currentTimeMillis() - time <= MAX_LAST_KNOWN_AGE_MS

    private fun Location.toGeoCoordinate(): GeoCoordinate = GeoCoordinate(
        latitude = latitude,
        longitude = longitude,
        altitude = if (hasAltitude()) altitude else null,
        accuracy = if (hasAccuracy()) accuracy else null,
        timestamp = time
    )

    private fun moveToMainThread(block: () -> Unit) {
        android.os.Handler(Looper.getMainLooper()).post(block)
    }

    private class ContinuationLocationListener(
        private val onResult: (GeoCoordinate?) -> Unit
    ) : LocationListener {
        override fun onLocationChanged(location: Location) {
            onResult(
                GeoCoordinate(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = if (location.hasAltitude()) location.altitude else null,
                    accuracy = if (location.hasAccuracy()) location.accuracy else null,
                    timestamp = location.time
                )
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onProviderDisabled(provider: String) {
            onResult(null)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    private class SingleFixDispatcher {
        private var thread: HandlerThread? = null

        fun ensureStarted(): Looper {
            val current = thread
            if (current?.isAlive == true) return current.looper
            val created = HandlerThread("geosnap-location").also { it.start() }
            thread = created
            return created.looper
        }
    }

    private companion object {
        val PROVIDER_PRIORITY = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        val MAX_LAST_KNOWN_AGE_MS: Long = TimeUnit.MINUTES.toMillis(5)
        val FIX_TIMEOUT_MS: Long = TimeUnit.SECONDS.toMillis(8)
    }
}