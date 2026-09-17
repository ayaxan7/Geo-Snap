package com.chirag.geosnap.presentation.gallery

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chirag.geosnap.domain.model.GeoMedia
import com.chirag.geosnap.domain.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    @Immutable
    data class UiState(
        val media: List<GeoMedia> = emptyList(),
        val loading: Boolean = true,
        val loadingError: Boolean = false
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
                _uiState.update { it.copy(media = media, loading = false, loadingError = false) }
            }
            result.onFailure {
                _uiState.update { it.copy(loading = false, loadingError = true) }
            }
        }
    }
}