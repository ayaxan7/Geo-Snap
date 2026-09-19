package com.chirag.geosnap.presentation.camera

import android.content.Context
import android.location.Geocoder
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chirag.geosnap.BuildConfig
import com.chirag.geosnap.domain.model.GeoCoordinate
import com.chirag.geosnap.domain.repository.MediaRepository
import com.chirag.geosnap.domain.usecase.GetCurrentLocationUseCase
import com.chirag.geosnap.domain.usecase.IsLocationEnabledUseCase
import com.chirag.geosnap.presentation.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Orchestrates camera captures: owns the temp-file pipeline, geotagging data
 * acquisition, MediaStore persistence and the recording timer. The actual
 * hardware binding lives in the composable-owned [CameraEngine].
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val getCurrentLocation: GetCurrentLocationUseCase,
    private val isLocationEnabled: IsLocationEnabledUseCase
) : ViewModel() {

    val mode: CameraMode = CameraMode.fromRoute(
        savedStateHandle.get<String>(Destination.Camera.ARGUMENT_MODE)
    ) ?: CameraMode.PHOTO

    init {
        checkLocationEnabled()
    }

    private var attachedEngine: CameraEngine? = null

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CameraEvent>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<CameraEvent> = _events.asSharedFlow()

    private var recordingElapsedJob: Job? = null
    private var pendingRecordingFile: File? = null
    private var pendingCoordinate: GeoCoordinate? = null

    /** Gives this ViewModel a reference to the composable-owned engine. */
    fun attachEngine(engine: CameraEngine) {
        if (attachedEngine === engine) return
        attachedEngine = engine
        watchEngineState()
    }

    fun detachEngine() {
        attachedEngine = null
        _uiState.update {
            it.copy(isReady = false, isRecording = false, recordingElapsedMs = 0L)
        }
        stopRecordingTimer()
    }

    private fun watchEngineState() {
        val current = attachedEngine ?: return
        viewModelScope.launch {
            current.isRecording
                .catch { }
                .collect { isRecording ->
                    if (isRecording) {
                        startRecordingTimer()
                    } else {
                        stopRecordingTimer()
                    }
                    _uiState.update { it.copy(isRecording = isRecording) }
                }
        }
        viewModelScope.launch {
            current.hasFlash
                .catch { }
                .collect { hasFlash -> _uiState.update { it.copy(hasFlash = hasFlash) } }
        }
        viewModelScope.launch {
            current.flashEnabled
                .catch { }
                .collect { flash -> _uiState.update { it.copy(flashEnabled = flash) } }
        }
    }

    /** Called by the screen once [CameraEngine.bind] succeeded. */
    fun onCameraReady() {
        _uiState.update { it.copy(isReady = true, isProcessing = false) }
        startLocationUpdates()
    }

    /** Re-reads the device location master switch and prompts when it is off. */
    fun checkLocationEnabled() {
        viewModelScope.launch {
            val enabled = runCatching { isLocationEnabled() }.getOrDefault(false)
            _uiState.update {
                it.copy(
                    isLocationEnabled = enabled,
                    showLocationDialog = it.showLocationDialog || !enabled
                )
            }
        }
    }

    fun dismissLocationDialog() {
        _uiState.update { it.copy(showLocationDialog = false) }
    }

    fun flipLensFacing() {
        val engine = attachedEngine ?: return
        val next = if (_uiState.value.lensFacing == CameraEngine.LensFacing.BACK) {
            CameraEngine.LensFacing.FRONT
        } else {
            CameraEngine.LensFacing.BACK
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            runCatching { engine.setLensFacing(next) }
            _uiState.update { it.copy(lensFacing = next, isProcessing = false, isReady = true) }
        }
    }

    fun toggleFlash() {
        val engine = attachedEngine ?: return
        viewModelScope.launch {
            runCatching { engine.toggleFlash() }
        }
    }

    fun capturePhoto() {
        val engine = attachedEngine ?: return
        if (_uiState.value.isProcessing) return

        _uiState.update { it.copy(isProcessing = true, error = null) }
        viewModelScope.launch {
            try {
                val coordinate = fetchCoordinateForTagging()
                val file = engine.capturePhoto()
                val uri = try {
                    mediaRepository.saveCapturedImage(file, coordinate)
                } catch (e: Exception) {
                    throw CaptureException(CameraError.SAVE_FAILED)
                }
                file.delete()
                _uiState.update { it.copy(isProcessing = false) }
                if (mode == CameraMode.QUICK_SHARE) {
                    _events.tryEmit(CameraEvent.QuickShareRequested(uri))
                } else {
                    _events.tryEmit(CameraEvent.MediaSaved(uri, isVideo = false))
                }
            } catch (e: CaptureException) {
                _uiState.update { it.copy(isProcessing = false, error = e.error) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isProcessing = false, error = CameraError.CAPTURE_FAILED)
                }
            }
        }
    }

    fun startRecording() {
        val engine = attachedEngine ?: return
        if (_uiState.value.isRecording || _uiState.value.isProcessing) return

        _uiState.update { it.copy(isProcessing = true, error = null) }
        viewModelScope.launch {
            try {
                pendingCoordinate = fetchCoordinateForTagging()
                val file = engine.startRecording()
                pendingRecordingFile = file
                _uiState.update { it.copy(isProcessing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, error = CameraError.RECORD_FAILED) }
            }
        }
    }

    fun stopRecording() {
        val engine = attachedEngine ?: return
        if (!_uiState.value.isRecording) return

        viewModelScope.launch {
            try {
                val file = engine.stopRecording()
                val coordinate = pendingCoordinate
                pendingCoordinate = null
                val uri = try {
                    mediaRepository.saveCapturedVideo(file, coordinate)
                } catch (e: Exception) {
                    throw CaptureException(CameraError.SAVE_FAILED)
                }
                file.delete()
                pendingRecordingFile = null
                _events.tryEmit(CameraEvent.MediaSaved(uri, isVideo = true))
            } catch (e: CaptureException) {
                _uiState.update { it.copy(isProcessing = false, error = e.error) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, error = CameraError.RECORD_FAILED) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Lets the screen surface errors that happen outside capture (e.g. bind). */
    fun reportError(error: CameraError) {
        _uiState.update { it.copy(isProcessing = false, error = error) }
    }

    private suspend fun fetchCoordinateForTagging(): GeoCoordinate? = try {
        getCurrentLocation()
    } catch (e: Exception) {
        null
    }

    private fun startLocationUpdates() {
        viewModelScope.launch {
            while (true) {
                val coord = fetchCoordinateForTagging()
                if (coord != null) {
                    val address = resolveAddressForUi(coord)
                    _uiState.update {
                        it.copy(
                            currentLocation = coord,
                            cityString = address.city.ifBlank { null },
                            fullAddress = address.full
                        )
                    }
                }
                delay(10000L.milliseconds) // fetch every 10s
            }
        }
    }

    private data class ResolvedAddress(val city: String, val full: String?)

    /**
     * City/street come from the framework [Geocoder]; the complete formatted
     * address is fetched from the Google Maps Geocoding API when a key exists
     * (falling back to the local geocoder when it does not).
     */
    private suspend fun resolveAddressForUi(coord: GeoCoordinate): ResolvedAddress {
        val local = reverseGeocode(coord)
        val full = fetchGoogleFormattedAddress(coord) ?: local?.full
        return ResolvedAddress(city = local?.city ?: "", full = full)
    }

    private fun reverseGeocode(coord: GeoCoordinate): ResolvedAddress? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(coord.latitude, coord.longitude, 1)
            val address = addresses?.firstOrNull() ?: return null
            val city = address.locality ?: address.subAdminArea ?: address.adminArea ?: ""
            val state = address.adminArea ?: ""
            val place = buildList {
                add(city)
                if (state.isNotBlank() && state != city) add(state)
            }.filter { it.isNotBlank() }
            val full = listOfNotNull(
                address.thoroughfare,
                address.subLocality,
                address.locality,
                address.subAdminArea ?: address.adminArea,
                address.postalCode,
                address.countryName
            ).filter { it.isNotBlank() }.joinToString(", ")
            ResolvedAddress(
                city = place.joinToString(", "),
                full = full.ifBlank { null }
            )
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchGoogleFormattedAddress(coord: GeoCoordinate): String? {
        val apiKey = BuildConfig.MAPS_API_KEY
        if (apiKey.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(
                    "https://maps.googleapis.com/maps/api/geocode/json" +
                        "?latlng=${coord.latitude},${coord.longitude}" +
                        "&key=$apiKey"
                )
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                try {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    extractFormattedAddress(body)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }

    private fun extractFormattedAddress(body: String): String? {
        val formattedRegex = Regex("\"formatted_address\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        return formattedRegex.find(body)?.groupValues?.get(1)
    }

    private fun startRecordingTimer() {
        if (recordingElapsedJob != null) return
        val startedAt = SystemClock.elapsedRealtime()
        recordingElapsedJob = viewModelScope.launch {
            while (true) {
                _uiState.update {
                    it.copy(recordingElapsedMs = SystemClock.elapsedRealtime() - startedAt)
                }
                delay(200L)
            }
        }
    }

    private fun stopRecordingTimer() {
        recordingElapsedJob?.cancel()
        recordingElapsedJob = null
    }

    private class CaptureException(val error: CameraError) : Throwable()
}