package com.patashot.myapp.presentation.gallery

import android.content.Context
import android.graphics.Bitmap
import android.location.Geocoder
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patashot.myapp.BuildConfig
import com.patashot.myapp.data.maps.MapsApi
import com.patashot.myapp.data.media.CapturedMediaEntity
import com.patashot.myapp.domain.model.GeoCoordinate
import com.patashot.myapp.domain.model.MediaType
import com.patashot.myapp.domain.model.NormalizedCropRect
import com.patashot.myapp.domain.repository.MediaRepository
import com.patashot.myapp.presentation.camera.CardTemplates
import com.patashot.myapp.presentation.camera.CaptureFilter
import com.patashot.myapp.presentation.camera.GeoTagCardContent
import com.patashot.myapp.presentation.camera.GeoTagOverlayRenderer
import com.patashot.myapp.presentation.camera.staticMapUrl
import com.patashot.myapp.presentation.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class EditMediaViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val mapsApi: MapsApi,
    private val geoTagOverlayRenderer: GeoTagOverlayRenderer
) : ViewModel() {

    val mediaId: Long = savedStateHandle.get<Long>(Destination.EditMedia.ARGUMENT_ID) ?: 0L

    data class EditUiState(
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val isSaved: Boolean = false,
        val entity: CapturedMediaEntity? = null,
        val file: File? = null,
        val uri: Uri? = null,
        val isVideo: Boolean = false,
        val originalCoordinate: GeoCoordinate? = null,
        val currentCoordinate: GeoCoordinate? = null,
        val cardContent: GeoTagCardContent? = null,
        val rotationDegrees: Int = 0,
        val cropRect: NormalizedCropRect? = null,
        val filter: CaptureFilter = CaptureFilter.ORIGINAL,
        val filterStrength: Float = 1f,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    private val _shareEvents = MutableSharedFlow<String>(replay = 0)
    val shareEvents: SharedFlow<String> = _shareEvents.asSharedFlow()

    init {
        loadMedia()
    }

    private fun loadMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val entity = mediaRepository.getMediaEntityById(mediaId)
            if (entity == null) {
                _uiState.update { it.copy(isLoading = false, error = "Media not found") }
                return@launch
            }

            val cleanFile = entity.cleanPath?.let { File(it) }?.takeIf { it.exists() }
                ?: mediaRepository.resolveMediaFile(entity.contentUri)

            if (cleanFile == null || !cleanFile.exists()) {
                _uiState.update { it.copy(isLoading = false, error = "Media file not found") }
                return@launch
            }

            val origLat = entity.originalLatitude ?: entity.latitude ?: 0.0
            val origLng = entity.originalLongitude ?: entity.longitude ?: 0.0
            val curLat = entity.latitude ?: 0.0
            val curLng = entity.longitude ?: 0.0

            val originalCoord = GeoCoordinate(latitude = origLat, longitude = origLng, timestamp = entity.timestamp)
            val currentCoord = GeoCoordinate(latitude = curLat, longitude = curLng, timestamp = entity.timestamp)

            val template = entity.templateId?.let { CardTemplates.fromId(it) } ?: CardTemplates.DEFAULT

            val cardContent = GeoTagCardContent(
                cityString = entity.city,
                fullAddress = entity.fullAddress,
                latitude = curLat,
                longitude = curLng,
                timestamp = entity.timestamp,
                template = template,
                staticMapUrl = staticMapUrl(curLat, curLng)
            )

            _uiState.update {
                it.copy(
                    isLoading = false,
                    entity = entity,
                    file = cleanFile,
                    uri = Uri.fromFile(cleanFile),
                    isVideo = entity.type == MediaType.VIDEO,
                    originalCoordinate = originalCoord,
                    currentCoordinate = currentCoord,
                    cardContent = cardContent
                )
            }
        }
    }

    fun rotateLeft() {
        _uiState.update { it.copy(rotationDegrees = (it.rotationDegrees - 90) % 360) }
    }

    fun rotateRight() {
        _uiState.update { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
    }

    fun setCropRect(cropRect: NormalizedCropRect?) {
        _uiState.update { it.copy(cropRect = cropRect) }
    }

    fun setFilter(filter: CaptureFilter) {
        _uiState.update { it.copy(filter = filter, filterStrength = 1f) }
    }

    fun setFilterStrength(strength: Float) {
        _uiState.update { it.copy(filterStrength = strength.coerceIn(0f, 2f)) }
    }

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

    private data class ResolvedAddress(val city: String, val full: String?)

    fun updateLocation(city: String?, address: String?, lat: Double, lng: Double) {
        val currentCard = _uiState.value.cardContent ?: return
        val newContent = currentCard.copy(
            cityString = city,
            fullAddress = address,
            latitude = lat,
            longitude = lng,
            staticMapUrl = staticMapUrl(lat, lng)
        )
        val editedCoordinate = GeoCoordinate(
            latitude = lat,
            longitude = lng,
            timestamp = currentCard.timestamp
        )
        _uiState.update {
            it.copy(
                cardContent = newContent,
                currentCoordinate = editedCoordinate
            )
        }
    }

    fun saveChanges() {
        val state = _uiState.value
        val cardContent = state.cardContent ?: return
        val file = state.file ?: return
        if (state.isSaving) return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val overlayContent = cardContent.copy(
                    staticMapUrl = staticMapUrl(
                        latitude = cardContent.latitude,
                        longitude = cardContent.longitude,
                        sizePx = 320
                    )
                )
                val overlay = geoTagOverlayRenderer.render(overlayContent)
                val recipe = if (state.filter != CaptureFilter.ORIGINAL && state.filterStrength > 0f) {
                    state.filter.recipe(state.filterStrength)
                } else null

                val success = mediaRepository.updateCapturedMedia(
                    id = mediaId,
                    coordinate = state.currentCoordinate,
                    overlay = overlay,
                    filter = recipe,
                    rotationDegrees = state.rotationDegrees,
                    cropRect = state.cropRect,
                    cardContent = cardContent
                )
                if (success) {
                    _uiState.update { it.copy(isSaving = false, isSaved = true) }
                } else {
                    _uiState.update { it.copy(isSaving = false, error = "Failed to save changes") }
                }
            } catch (e: Exception) {
                Log.e("EditMediaViewModel", "Save failed", e)
                _uiState.update { it.copy(isSaving = false, error = "Failed to save changes") }
            }
        }
    }

    fun share() {
        val state = _uiState.value
        val cardContent = state.cardContent ?: return
        val file = state.file ?: return
        if (state.isSaving) return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val overlayContent = cardContent.copy(
                    staticMapUrl = staticMapUrl(
                        latitude = cardContent.latitude,
                        longitude = cardContent.longitude,
                        sizePx = 320
                    )
                )
                val overlay = geoTagOverlayRenderer.render(overlayContent)
                val recipe = if (state.filter != CaptureFilter.ORIGINAL && state.filterStrength > 0f) {
                    state.filter.recipe(state.filterStrength)
                } else null

                val shareUri = mediaRepository.prepareShareCopy(
                    sourceFile = file,
                    coordinate = state.currentCoordinate,
                    overlay = overlay,
                    filter = recipe,
                    isVideo = state.isVideo,
                    rotationDegrees = state.rotationDegrees,
                    cropRect = state.cropRect
                )
                _uiState.update { it.copy(isSaving = false) }
                _shareEvents.emit(shareUri)
            } catch (e: Exception) {
                Log.e("EditMediaViewModel", "Share failed", e)
                _uiState.update { it.copy(isSaving = false, error = "Failed to prepare share") }
            }
        }
    }
}
