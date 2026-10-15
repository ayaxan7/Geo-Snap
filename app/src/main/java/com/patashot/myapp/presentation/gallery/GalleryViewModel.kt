package com.patashot.myapp.presentation.gallery

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patashot.myapp.domain.model.GeoMedia
import com.patashot.myapp.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Calendar-style buckets used to group the gallery, newest first. */
enum class GallerySection {
    TODAY,
    THIS_WEEK,
    LAST_WEEK,
    THIS_MONTH,
    LAST_MONTH,
    OLDER
}

/** A group of media sharing the same [section], already sorted newest first. */
@Immutable
data class MediaSection(
    val section: GallerySection,
    val items: List<GeoMedia>
)

/**
 * Groups newest-first [media] into Today / This week / Last week / This month /
 * Last month / Older buckets (rolling windows, computed from [now] so it is
 * deterministic in tests).
 */
fun groupMediaByDate(
    media: List<GeoMedia>,
    now: LocalDate = LocalDate.now()
): List<MediaSection> {
    val ordered = media.sortedByDescending { it.timestamp }
    return ordered
        .groupBy { item ->
            val date = Instant.ofEpochMilli(item.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            val daysAgo = ChronoUnit.DAYS.between(date, now)
            when {
                daysAgo < 1 -> GallerySection.TODAY
                daysAgo < 7 -> GallerySection.THIS_WEEK
                daysAgo < 14 -> GallerySection.LAST_WEEK
                daysAgo < 30 -> GallerySection.THIS_MONTH
                daysAgo < 60 -> GallerySection.LAST_MONTH
                else -> GallerySection.OLDER
            }
        }
        .entries
        .sortedBy { it.key.ordinal }
        .map { (section, items) -> MediaSection(section, items) }
        .filter { it.items.isNotEmpty() }
}

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    @Immutable
    data class UiState(
        val media: List<GeoMedia> = emptyList(),
        val sections: List<MediaSection> = emptyList(),
        val loading: Boolean = true,
        val loadingError: Boolean = false,
        val selectionMode: Boolean = false,
        val selection: Set<String> = emptySet(),
        val deletingSelection: Boolean = false,
        val deleteSelectionFailed: Boolean = false,
        val savingSelection: Boolean = false
    )

    sealed interface GalleryEvent {
        data class SavedToGallery(val count: Int) : GalleryEvent
        data object SaveToGalleryFailed : GalleryEvent
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = Channel<GalleryEvent>(Channel.BUFFERED)
    val events: Flow<GalleryEvent> = _events.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(loading = true, loadingError = false) }
        viewModelScope.launch {
            val result = runCatching { mediaRepository.getAppMedia() }
            result.onSuccess { media ->
                _uiState.update {
                    it.copy(
                        media = media,
                        sections = groupMediaByDate(media),
                        loading = false,
                        loadingError = false
                    )
                }
            }
            result.onFailure {
                _uiState.update { it.copy(loading = false, loadingError = true) }
            }
        }
    }

    /**
     * Enter selection mode from the top bar's Select button.
     *
     * Always starts empty so the user picks deliberately rather than acting on a
     * stale selection carried over from an earlier round, which would otherwise
     * make an unattended tap on Delete destructive.
     */
    fun enterSelection() {
        _uiState.update { it.copy(selectionMode = true, selection = emptySet()) }
    }

    /**
     * Enter selection mode with [uri] pre-selected, used when the user holds a
     * thumbnail so it is picked up by the bulk share/delete actions immediately.
     */
    fun startSelectionAt(uri: String) {
        _uiState.update { it.copy(selectionMode = true, selection = setOf(uri)) }
    }

    fun exitSelection() {
        _uiState.update { it.copy(selectionMode = false, selection = emptySet()) }
    }

    fun toggleSelection(uri: String) {
        if (!_uiState.value.selectionMode) return
        _uiState.update {
            val updated = if (uri in it.selection) it.selection - uri else it.selection + uri
            it.copy(selection = updated)
        }
    }

    /**
     * Soft-deletes every selected item (single or multi). Exits selection and
     * refreshes when anything was removed; surfaces an error snackbar when none
     * of the deletions succeeded.
     */
    fun deleteSelection() {
        val uris = _uiState.value.selection.toList()
        if (uris.isEmpty()) return
        _uiState.update { it.copy(deletingSelection = true, deleteSelectionFailed = false) }
        viewModelScope.launch {
            val results = uris.map {
                runCatching { mediaRepository.deleteMedia(it) }.getOrDefault(false)
            }
            _uiState.update { it.copy(deletingSelection = false) }
            if (results.any { it }) {
                exitSelection()
                refresh()
            }
            if (results.none { it }) {
                _uiState.update { it.copy(deleteSelectionFailed = true) }
            }
        }
    }

    fun clearDeleteSelectionFailed() {
        _uiState.update { it.copy(deleteSelectionFailed = false) }
    }

    fun toggleSelectAll() {
        if (!_uiState.value.selectionMode) return
        val allUris = _uiState.value.media.map { it.uri.toString() }.toSet()
        _uiState.update {
            val isAllSelected = it.selection.size == allUris.size && allUris.isNotEmpty()
            it.copy(selection = if (isAllSelected) emptySet() else allUris)
        }
    }

    /**
     * Exports all selected media items to the device gallery (MediaStore).
     */
    fun saveSelection() {
        val selected = _uiState.value.media.filter { it.uri.toString() in _uiState.value.selection }
        if (selected.isEmpty() || _uiState.value.savingSelection) return
        _uiState.update { it.copy(savingSelection = true) }
        viewModelScope.launch {
            val results = selected.map { media ->
                runCatching { mediaRepository.saveToDeviceGallery(media) }.getOrDefault(false)
            }
            val successCount = results.count { it }
            _uiState.update { it.copy(savingSelection = false) }
            if (successCount > 0) {
                _events.trySend(GalleryEvent.SavedToGallery(successCount))
                exitSelection()
            } else {
                _events.trySend(GalleryEvent.SaveToGalleryFailed)
            }
        }
    }
}