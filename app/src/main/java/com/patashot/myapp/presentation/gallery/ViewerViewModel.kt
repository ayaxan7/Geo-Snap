package com.patashot.myapp.presentation.gallery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patashot.myapp.domain.model.GeoMedia
import com.patashot.myapp.domain.model.MediaType
import com.patashot.myapp.domain.repository.MediaRepository
import com.patashot.myapp.presentation.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the full-screen viewer. Loads the same Room-backed gallery list as
 * [GalleryViewModel] so swipe navigation works over a stable in-memory list, and
 * deletes items from the app gallery only (the device file is untouched).
 */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val initialIndex: Int =
        savedStateHandle.get<Int>(Destination.GalleryViewer.ARGUMENT_INDEX) ?: 0

    data class UiState(
        val media: List<GeoMedia> = emptyList(),
        val loading: Boolean = true,
        val loadingError: Boolean = false,
        val deleting: Boolean = false,
        val deleteError: Boolean = false,
        val saving: Boolean = false
    )

    sealed interface ViewerEvent {
        data class SavedToGallery(val isVideo: Boolean) : ViewerEvent
        data object SaveFailed : ViewerEvent
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = Channel<ViewerEvent>(Channel.BUFFERED)
    val events: Flow<ViewerEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(loading = true, loadingError = false) }
        viewModelScope.launch {
            val result = runCatching { mediaRepository.getAppMedia() }
            result.onSuccess { media ->
                _uiState.update { it.copy(media = media, loading = false, loadingError = false) }
            }
            result.onFailure {
                _uiState.update { it.copy(loading = false, loadingError = true) }
            }
        }
    }

    /**
     * Removes the item at [index] from the app gallery. When the list becomes
     * empty [onEmpty] is invoked so the screen can pop itself.
     */
    fun deleteAt(index: Int, onEmpty: () -> Unit) {
        if (_uiState.value.media.isEmpty()) return
        val item = _uiState.value.media.getOrNull(index) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(deleting = true, deleteError = false) }
            val removed = runCatching { mediaRepository.deleteMedia(item.uri.toString()) }
                .getOrDefault(false)
            if (removed) {
                val remaining = _uiState.value.media.toMutableList().apply { removeAt(index) }
                _uiState.update { it.copy(media = remaining, deleting = false) }
                if (remaining.isEmpty()) onEmpty()
            } else {
                _uiState.update { it.copy(deleting = false, deleteError = true) }
            }
        }
    }

    fun clearDeleteError() {
        _uiState.update { it.copy(deleteError = false) }
    }

    /**
     * Saves the item at [index] to the device gallery (MediaStore).
     */
    fun saveToDeviceGallery(index: Int) {
        val item = _uiState.value.media.getOrNull(index) ?: return
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            val success = runCatching { mediaRepository.saveToDeviceGallery(item) }.getOrDefault(false)
            _uiState.update { it.copy(saving = false) }
            if (success) {
                _events.trySend(ViewerEvent.SavedToGallery(item.type == MediaType.VIDEO))
            } else {
                _events.trySend(ViewerEvent.SaveFailed)
            }
        }
    }
}
