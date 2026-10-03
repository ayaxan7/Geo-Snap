package com.chirag.geosnap.presentation.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chirag.geosnap.domain.usecase.GetSelectedCardTemplateUseCase
import com.chirag.geosnap.domain.usecase.SetSelectedCardTemplateUseCase
import com.chirag.geosnap.presentation.camera.CardTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the geotag card template picker.
 *
 * Selection is written straight through to preferences, which is what makes the
 * chosen card the default for subsequent captures: the camera observes the same
 * persisted value, so no extra in-memory hand-off is needed.
 */
@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val getSelectedCardTemplate: GetSelectedCardTemplateUseCase,
    private val setSelectedCardTemplate: SetSelectedCardTemplateUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TemplatesUiState())
    val uiState: StateFlow<TemplatesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getSelectedCardTemplate().collect { template ->
                _uiState.update { it.copy(selectedTemplateId = template.id) }
            }
        }
    }

    /** Makes [template] the default card for future captures. */
    fun selectTemplate(template: CardTemplate) {
        // Reflect the choice immediately so the row highlights without waiting for
        // the preference write to round-trip back through the flow.
        _uiState.update { it.copy(selectedTemplateId = template.id) }
        viewModelScope.launch { setSelectedCardTemplate(template) }
    }
}
