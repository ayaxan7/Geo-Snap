package com.chirag.geosnap.presentation.gallery

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chirag.geosnap.domain.model.GeoMedia
import com.chirag.geosnap.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        val deleteSelectionFailed: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

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

    fun enterSelection() {
        _uiState.update { it.copy(selectionMode = true) }
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
}