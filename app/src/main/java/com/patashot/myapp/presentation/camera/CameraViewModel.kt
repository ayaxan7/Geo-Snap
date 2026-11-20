package com.patashot.myapp.presentation.camera

import android.content.Context
import android.graphics.Bitmap
import android.location.Geocoder
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patashot.myapp.BuildConfig
import com.patashot.myapp.data.maps.MapsApi
import com.patashot.myapp.domain.model.GeoCoordinate
import com.patashot.myapp.domain.model.NormalizedCropRect
import com.patashot.myapp.domain.repository.MediaRepository
import com.patashot.myapp.domain.repository.PreferencesRepository
import com.patashot.myapp.domain.usecase.GetCurrentLocationUseCase
import com.patashot.myapp.domain.usecase.GetSelectedCardTemplateUseCase
import com.patashot.myapp.domain.usecase.IsLocationEnabledUseCase
import com.patashot.myapp.presentation.core.hasMediaPermission
import com.patashot.myapp.presentation.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds

/**
 * Orchestrates camera captures: owns the temp-file pipeline, geotagging data
 * acquisition, MediaStore persistence and the recording timer. The actual
 * hardware binding lives in the composable-owned [CameraEngine].
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val getCurrentLocation: GetCurrentLocationUseCase,
    private val isLocationEnabled: IsLocationEnabledUseCase,
    private val geoTagOverlayRenderer: GeoTagOverlayRenderer,
    private val mapsApi: MapsApi,
    private val getSelectedCardTemplate: GetSelectedCardTemplateUseCase,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    // Replaced by NoOpCameraSound in unit tests via setCameraSound()
    private var cameraSound: CameraSound = try {
        CameraSoundImpl(context)
    } catch (_: Exception) {
        NoOpCameraSound()
    }

    @androidx.annotation.VisibleForTesting
    fun setCameraSound(sound: CameraSound) {
        cameraSound = sound
    }

    var mode: CameraMode = CameraMode.fromRoute(
        savedStateHandle.get<String>(Destination.Camera.ARGUMENT_MODE)
    ) ?: CameraMode.PHOTO
        private set

    /** The UI lets the user switch modes in-camera; keep the VM in sync. */
    fun setMode(newMode: CameraMode) {
        mode = newMode
    }

    private var attachedEngine: CameraEngine? = null

    private val _uiState = MutableStateFlow(
        // Restored before anything observes the flow so a preview survives process death.
        CameraUiState(
            pendingPreview = restorePendingPreview(),
            filter = restoreFilter(),
            filterStrength = savedStateHandle[KEY_FILTER_STRENGTH] ?: FILTER_STRENGTH_NORMAL
        )
    )
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CameraEvent>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<CameraEvent> = _events.asSharedFlow()

    init {
        checkLocationEnabled()
        observeSelectedTemplate()
        persistPreviewForProcessDeath()
    }

    /**
     * Keeps the geotag card template in sync with the persisted selection, so the
     * template chosen from Settings or the camera top bar applies to the next
     * capture without the user having to reopen the picker.
     */
    private fun observeSelectedTemplate() {
        viewModelScope.launch {
            getSelectedCardTemplate().collect { template ->
                _uiState.update { it.copy(selectedTemplate = template) }
            }
        }
    }

    private var recordingElapsedJob: Job? = null
    private var locationUpdatesJob: Job? = null
    private var pendingRecordingFile: File? = null
    private var pendingCoordinate: GeoCoordinate? = null
    private var lastLocatedCoordinate: GeoCoordinate? = null
    private var lastFailedCoordinate: GeoCoordinate? = null
    private var geocodeBackoffPolls: Int = 0

    /** Gives this ViewModel a reference to the composable-owned engine. */
    fun attachEngine(engine: CameraEngine) {
        if (attachedEngine === engine) return
        attachedEngine = engine
        watchEngineState()
    }

    fun detachEngine() {
        attachedEngine = null
        locationUpdatesJob?.cancel()
        locationUpdatesJob = null
        _uiState.update {
            it.copy(
                isReady = false,
                isRecording = false,
                recordingElapsedMs = 0L,
                countdownSeconds = null,
                isCountdownActive = false
            )
        }
        stopRecordingTimer()
    }

    private fun watchEngineState() {
        val current = attachedEngine ?: return
        viewModelScope.launch {
            current.isRecording
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
                .collect { hasFlash -> _uiState.update { it.copy(hasFlash = hasFlash) } }
        }
        viewModelScope.launch {
            current.flashEnabled
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

    fun zoomBy(factor: Float) {
        val engine = attachedEngine ?: return
        viewModelScope.launch { runCatching { engine.zoomBy(factor) } }
    }

    fun capturePhoto() {
        val engine = attachedEngine ?: return
        if (_uiState.value.isProcessing || _uiState.value.isCountdownActive) return

        _uiState.update { it.copy(isProcessing = true, error = null) }
        viewModelScope.launch {
            try {
                val coordinate = fetchCoordinateForTagging()
                executeWithTimer(_uiState.value.timerSeconds) {
                    cameraSound.playCapture()
                    val file = engine.capturePhoto()
                    val cardContent = resolveCardContent(coordinate)

                    if (mode == CameraMode.QUICK_SHARE) {
                        val uri = try {
                            mediaRepository.saveCapturedImage(
                                sourceFile = file,
                                coordinate = coordinate,
                                overlay = renderOverlay(cardContent),
                                saveToMediaStore = false,
                                cardContent = cardContent
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Photo save failed", e)
                            throw CaptureException(CameraError.SAVE_FAILED)
                        }
                        file.delete()
                        _uiState.update { it.copy(isProcessing = false) }
                        _events.tryEmit(CameraEvent.QuickShareRequested(uri))
                    } else {
                        val hasMediaPermission = context.hasMediaPermission()
                        val isAutoSaveEnabled = preferencesRepository.isAutoSaveEnabled.first()
                        val shouldSaveToDevice = hasMediaPermission && isAutoSaveEnabled
                        val uri = try {
                            mediaRepository.saveCapturedImage(
                                sourceFile = file,
                                coordinate = coordinate,
                                overlay = renderOverlay(cardContent),
                                saveToMediaStore = shouldSaveToDevice,
                                cardContent = cardContent
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Photo save failed", e)
                            throw CaptureException(CameraError.SAVE_FAILED)
                        }
                        file.delete()
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                filter = CaptureFilter.ORIGINAL,
                                filterStrength = FILTER_STRENGTH_NORMAL
                            )
                        }
                        _events.tryEmit(CameraEvent.MediaSaved(uri, isVideo = false))
                    }
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
        if (_uiState.value.isRecording || _uiState.value.isProcessing || _uiState.value.isCountdownActive) return

        _uiState.update { it.copy(isProcessing = true, error = null) }
        viewModelScope.launch {
            try {
                pendingCoordinate = fetchCoordinateForTagging()
                executeWithTimer(_uiState.value.timerSeconds) {
                    cameraSound.playVideoStart()
                    val file = engine.startRecording()

                    pendingRecordingFile = file
                    _uiState.update { it.copy(isProcessing = false) }
                }
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
                cameraSound.playVideoStop()
                val file = engine.stopRecording()

                val coordinate = pendingCoordinate
                pendingCoordinate = null
                pendingRecordingFile = null
                val cardContent = resolveCardContent(coordinate)

                val hasMediaPermission = context.hasMediaPermission()
                val isAutoSaveEnabled = preferencesRepository.isAutoSaveEnabled.first()
                val shouldSaveToDevice = hasMediaPermission && isAutoSaveEnabled
                val uri = try {
                    mediaRepository.saveCapturedVideo(
                        sourceFile = file,
                        coordinate = coordinate,
                        overlay = renderOverlay(cardContent),
                        saveToMediaStore = shouldSaveToDevice,
                        cardContent = cardContent
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Video save failed", e)
                    throw Exception("Video save failed", e)
                }
                file.delete()
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        filter = CaptureFilter.ORIGINAL,
                        filterStrength = FILTER_STRENGTH_NORMAL
                    )
                }
                _events.tryEmit(CameraEvent.MediaSaved(uri, isVideo = true))
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, error = CameraError.RECORD_FAILED) }
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    fun setPendingPreview(preview: PreviewData?) {
        _uiState.update { it.copy(pendingPreview = preview) }
    }

    fun rotatePreviewLeft() {
        _uiState.update { state ->
            val preview = state.pendingPreview ?: return@update state
            val newRotation = (preview.rotationDegrees - 90) % 360
            state.copy(pendingPreview = preview.copy(rotationDegrees = newRotation))
        }
    }

    fun rotatePreviewRight() {
        _uiState.update { state ->
            val preview = state.pendingPreview ?: return@update state
            val newRotation = (preview.rotationDegrees + 90) % 360
            state.copy(pendingPreview = preview.copy(rotationDegrees = newRotation))
        }
    }

    fun setPreviewCropRect(cropRect: NormalizedCropRect?) {
        _uiState.update { state ->
            val preview = state.pendingPreview ?: return@update state
            state.copy(pendingPreview = preview.copy(cropRect = cropRect))
        }
    }

    fun discardPreview() {
        if (_uiState.value.isProcessing) return
        _uiState.value.pendingPreview?.file?.delete()
        savedStateHandle[KEY_PENDING_PREVIEW] = null
        _uiState.update { it.copy(pendingPreview = null) }
    }

    /** Selects the filter, starting it at its normal (slider-center) strength. */
    fun setFilter(filter: CaptureFilter) {
        _uiState.update { it.copy(filter = filter, filterStrength = FILTER_STRENGTH_NORMAL) }
    }

    /** Sets the filter strength in `0..2` (1 = normal look, 0 = untouched). */
    fun setFilterStrength(strength: Float) {
        _uiState.update { it.copy(filterStrength = strength.coerceIn(0f, FILTER_STRENGTH_MAX)) }
    }

    fun setTimerSeconds(seconds: Int) {
        _uiState.update { it.copy(timerSeconds = seconds.coerceIn(0, 10)) }
    }

    fun updatePendingLocation(city: String?, address: String?, lat: Double, lng: Double) {
        val currentPreview = _uiState.value.pendingPreview ?: return
        val newContent = currentPreview.cardContent.copy(
            cityString = city,
            fullAddress = address,
            latitude = lat,
            longitude = lng,
            // Also refresh the thumbnail so the preview card shows the edited spot.
            staticMapUrl = staticMapUrl(lat, lng)
        )
        // The edited pin is what gets reviewed, so it is also what gets written
        // into the GPS EXIF on save/share; the original timestamp is kept so the
        // card date and the GPS time stamp still agree.
        val original = currentPreview.coordinate
        val editedCoordinate = GeoCoordinate(
            latitude = lat,
            longitude = lng,
            altitude = original?.altitude,
            accuracy = original?.accuracy,
            timestamp = original?.timestamp ?: System.currentTimeMillis()
        )
        _uiState.update {
            it.copy(
                pendingPreview = currentPreview.copy(
                    cardContent = newContent,
                    coordinate = editedCoordinate
                )
            )
        }
    }

    fun savePreviewToGallery() {
        val preview = _uiState.value.pendingPreview ?: return
        if (_uiState.value.isProcessing) return

        val hasMediaPermission = context.hasMediaPermission()

        _uiState.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            try {
                val isAutoSaveEnabled = preferencesRepository.isAutoSaveEnabled.first()
                val shouldSaveToDevice = hasMediaPermission && isAutoSaveEnabled
                val overlay = renderBurnOverlay(preview)
                val uri = if (preview.isVideo) {
                    mediaRepository.saveCapturedVideo(
                        preview.file,
                        preview.coordinate,
                        overlay,
                        filterRecipe(uiState.value),
                        shouldSaveToDevice,
                        preview.rotationDegrees,
                        preview.cropRect
                    )
                } else {
                    mediaRepository.saveCapturedImage(
                        preview.file,
                        preview.coordinate,
                        overlay,
                        filterRecipe(uiState.value),
                        shouldSaveToDevice,
                        preview.rotationDegrees,
                        preview.cropRect
                    )
                }
                preview.file.delete()
                savedStateHandle[KEY_PENDING_PREVIEW] = null
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        pendingPreview = null,
                        filter = CaptureFilter.ORIGINAL,
                        filterStrength = FILTER_STRENGTH_NORMAL
                    )
                }
                _events.tryEmit(CameraEvent.MediaSaved(uri, isVideo = preview.isVideo))
            } catch (e: Exception) {
                Log.e(TAG, "Media save failed", e)
                _uiState.update { it.copy(isProcessing = false, error = CameraError.SAVE_FAILED) }
            }
        }
    }

    /**
     * Burns the reviewed card and filter into a disposable copy and hands the
     * screen a `content://` URI for the share sheet. The pending preview is left
     * untouched, so returning from the share sheet keeps retake/edit/delete/save
     * available.
     */
    fun sharePreview() {
        val preview = _uiState.value.pendingPreview ?: return
        if (_uiState.value.isProcessing) return

        _uiState.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            try {
                val overlay = renderBurnOverlay(preview)
                val uri = mediaRepository.prepareShareCopy(
                    sourceFile = preview.file,
                    coordinate = preview.coordinate,
                    overlay = overlay,
                    filter = filterRecipe(uiState.value),
                    isVideo = preview.isVideo,
                    rotationDegrees = preview.rotationDegrees,
                    cropRect = preview.cropRect
                )
                _uiState.update { it.copy(isProcessing = false) }
                _events.tryEmit(CameraEvent.SharePrepared(uri, isVideo = preview.isVideo))
            } catch (e: Exception) {
                Log.e(TAG, "Share preparation failed", e)
                _uiState.update { it.copy(isProcessing = false, error = CameraError.SHARE_FAILED) }
            }
        }
    }

    /**
     * Renders the card the user reviewed for burning, re-resolving the static map
     * at the on-disk burn size. Re-resolving from live state here could tag the
     * capture with a later address, so the snapshot is taken from [preview].
     */
    private suspend fun renderBurnOverlay(preview: PreviewData): Bitmap? = renderOverlay(
        preview.cardContent.copy(
            staticMapUrl = staticMapUrl(
                latitude = preview.cardContent.latitude,
                longitude = preview.cardContent.longitude,
                sizePx = BURN_MAP_SIZE_PX
            )
        )
    )

    /**
     * Parks the pending preview (plus the in-progress filter) in [SavedStateHandle]
     * so an Android process death while the user is in the share sheet does not
     * lose the capture. On restore, a preview whose file has vanished from the
     * cache is dropped silently.
     */
    private fun persistPreviewForProcessDeath() {
        viewModelScope.launch {
            // Projected so the 200ms recording-timer ticks do not rewrite the bundle.
            _uiState
                .map { Triple(it.pendingPreview, it.filter, it.filterStrength) }
                .distinctUntilChanged()
                .collect { (preview, filter, strength) ->
                    savedStateHandle[KEY_PENDING_PREVIEW] = preview
                    savedStateHandle[KEY_FILTER] = filter.name
                    savedStateHandle[KEY_FILTER_STRENGTH] = strength
                }
        }
    }

    private fun restorePendingPreview(): PreviewData? =
        savedStateHandle.get<PreviewData>(KEY_PENDING_PREVIEW)
            ?.takeIf { it.file.exists() }

    private fun restoreFilter(): CaptureFilter =
        savedStateHandle.get<String>(KEY_FILTER)
            ?.let { name -> CaptureFilter.entries.firstOrNull { it.name == name } }
            ?: CaptureFilter.ORIGINAL

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Lets the screen surface errors that happen outside capture (e.g. bind). */
    fun reportError(error: CameraError) {
        _uiState.update { it.copy(isProcessing = false, error = error) }
    }

    /**
     * The `4x5` color recipe to burn in, or null when the capture is left
     * untouched. Called at save time so strength tweaks made up to the last
     * moment are included.
     */
    private fun filterRecipe(state: CameraUiState): FloatArray? =
        if (state.filter != CaptureFilter.ORIGINAL && state.filterStrength > 0f) {
            state.filter.recipe(state.filterStrength)
        } else {
            null
        }

    private suspend fun executeWithTimer(
        timerSeconds: Int,
        capture: suspend () -> Unit
    ) {
        try {
            if (timerSeconds > 0) {
                _uiState.update { it.copy(isCountdownActive = true) }
                for (remaining in timerSeconds downTo 1) {
                    _uiState.update { it.copy(countdownSeconds = remaining) }
                    delay(1000L)
                }
            }
            capture()
        } finally {
            _uiState.update { it.copy(countdownSeconds = null, isCountdownActive = false) }
        }
    }

    private suspend fun fetchCoordinateForTagging(): GeoCoordinate? = try {
        getCurrentLocation()
    } catch (e: Exception) {
        null
    }

    /**
     * Snapshots the location card for [coordinate], resolving the address first
     * when the camera has not managed to resolve one yet. Without this a capture
     * taken before the first poll lands is tagged "Fetching location..." forever,
     * because the poll only refreshes once the device moves.
     *
     * The lookup is bounded so a slow network cannot stall the shutter-to-preview
     * transition; on timeout the snapshot keeps the unresolved title and a later
     * poll fills it in.
     */
    private suspend fun resolveCardContent(coordinate: GeoCoordinate?): GeoTagCardContent {
        val state = _uiState.value
        if (coordinate != null && (state.cityString == null || state.fullAddress == null)) {
            withTimeoutOrNull(CAPTURE_GEOCODE_TIMEOUT) {
                publishLocation(coordinate, resolveAddressForUi(coordinate))
            }
        }
        return _uiState.value.toGeoTagCardContent(
            coordinate = coordinate,
            mapSizePx = PREVIEW_MAP_SIZE_PX
        )
    }

    /**
     * Writes a resolved address onto the UI state. A blank result never erases a
     * good one, and nothing is written while a preview is open so the card being
     * reviewed cannot change under the user.
     */
    private fun publishLocation(coord: GeoCoordinate, address: ResolvedAddress) {
        val city = address.city.ifBlank { null }
        _uiState.update {
            if (it.pendingPreview != null) {
                it
            } else {
                it.copy(
                    currentLocation = coord,
                    cityString = city ?: it.cityString,
                    fullAddress = address.full ?: it.fullAddress
                )
            }
        }
    }

    /**
     * Renders the card off-screen for burning into media. Best-effort: a null
     * result simply means the capture is saved without the burnt card.
     */
    private suspend fun renderOverlay(content: GeoTagCardContent): Bitmap? =
        runCatching { geoTagOverlayRenderer.render(content) }.getOrNull()

    private fun startLocationUpdates() {
        locationUpdatesJob?.cancel()
        locationUpdatesJob = viewModelScope.launch {
            while (true) {
                val coord = fetchCoordinateForTagging()
                if (coord != null) {
                    if (shouldResolveAddress(coord)) {
                        geocodeBackoffPolls = 0
                        val address = resolveAddressForUi(coord)
                        if (address.city.isNotBlank() || address.full != null) {
                            lastLocatedCoordinate = coord
                            lastFailedCoordinate = null
                        } else {
                            // A failed lookup must never be recorded as "already
                            // located", or the movement gate would suppress every
                            // retry for the rest of the camera session.
                            lastFailedCoordinate = coord
                            geocodeBackoffPolls = GEOCODE_RETRY_POLL_BACKOFF
                        }
                        publishLocation(coord, address)
                    } else if (geocodeBackoffPolls > 0) {
                        geocodeBackoffPolls--
                    }
                }
                delay(POLL_INTERVAL)
            }
        }
    }

    /**
     * Reverse geocoding (Maps API) and the static-map URL are only refreshed once
     * the device has actually moved, so a sitting phone does not hammer the Maps
     * API every poll. A lookup that resolved nothing is retried after
     * [GEOCODE_RETRY_POLL_BACKOFF] idle polls rather than being cached as a
     * permanent failure, so a transient outage does not pin the card title to
     * "Fetching location..." for the rest of the session.
     */
    private fun shouldResolveAddress(coord: GeoCoordinate): Boolean {
        val lastSuccess = lastLocatedCoordinate
        if (lastSuccess != null &&
            distanceMeters(
                lastSuccess.latitude,
                lastSuccess.longitude,
                coord.latitude,
                coord.longitude
            ) < MOVEMENT_THRESHOLD_METERS
        ) {
            return false
        }
        val lastFailure = lastFailedCoordinate ?: return true
        return lastFailure.latitude != coord.latitude || lastFailure.longitude != coord.longitude || geocodeBackoffPolls <= 0
    }

    private fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        return 6371000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private data class ResolvedAddress(val city: String, val full: String?)

    /**
     * City/street come from the framework [Geocoder]; the complete formatted
     * address is fetched from the Google Maps Geocoding API when a key exists
     * (falling back to the local geocoder when it does not).
     */
    suspend fun resolveLocation(lat: Double, lng: Double): Pair<String, String?> {
        val address = resolveAddressForUi(GeoCoordinate(lat, lng))
        return Pair(address.city, address.full)
    }

    private suspend fun resolveAddressForUi(coord: GeoCoordinate): ResolvedAddress {
        val local = reverseGeocode(coord)
        val full = fetchGoogleFormattedAddress(coord) ?: local?.full
        return ResolvedAddress(city = local?.city ?: "", full = full)
    }

    private fun reverseGeocode(coord: GeoCoordinate): ResolvedAddress? {
        return try {
            val geocoder = Geocoder(context, Locale.ENGLISH)
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
        return runCatching {
            val response = mapsApi.reverseGeocode(
                latLng = "${coord.latitude},${coord.longitude}",
                language = "en",
                apiKey = apiKey
            )
            response.results
                ?.firstOrNull()
                ?.formattedAddress
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
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

    override fun onCleared() {
        cameraSound.release()
    }

    private class CaptureException(val error: CameraError) : Throwable()

    private companion object {
        /** Static map thumbnail size used on the burnt card (pages the URL). */
        const val BURN_MAP_SIZE_PX = 320
        const val TAG = "CameraViewModel"

        /** SavedStateHandle keys for surviving process death with a pending preview. */
        const val KEY_PENDING_PREVIEW = "camera_pending_preview"
        const val KEY_FILTER = "camera_filter"
        const val KEY_FILTER_STRENGTH = "camera_filter_strength"

        /** How often the camera asks for a fresh fix. */
        val POLL_INTERVAL = 10.seconds

        /** Filter strength at the slider center (the filter's normal look). */
        const val FILTER_STRENGTH_NORMAL = 1f

        /** Upper bound for the boost side of the filter slider. */
        const val FILTER_STRENGTH_MAX = 2f

        /** Upper bound on the capture-time address lookup before the preview opens. */
        val CAPTURE_GEOCODE_TIMEOUT = 3.seconds

        /** Minimum device movement (meters) before re-resolving the address and
         *  regenerating the static map, so the Maps API is not polled endlessly. */
        const val MOVEMENT_THRESHOLD_METERS = 30.0

        /** Idle polls to wait before retrying a reverse geocode that resolved
         *  nothing, so a failing lookup is not retried every single poll. */
        const val GEOCODE_RETRY_POLL_BACKOFF = 2
    }
}