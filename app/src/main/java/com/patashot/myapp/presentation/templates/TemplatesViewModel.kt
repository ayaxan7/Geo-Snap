package com.patashot.myapp.presentation.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patashot.myapp.domain.usecase.GetSelectedCardTemplateUseCase
import com.patashot.myapp.domain.usecase.SetSelectedCardTemplateUseCase
import com.patashot.myapp.presentation.camera.CardTemplate
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
 * The picker edits a draft: tapping a row moves [TemplatesUiState.pendingTemplateId]
 * and writes nothing, while only [saveSelection] persists to DataStore. That makes
 * Save the single commit point, so navigating back out of the screen leaves the
 * stored default exactly as it was.
 *
 * The write is still what makes the chosen card the default for later captures:
 * the camera observes the same persisted value, so no extra hand-off is needed.
 */
@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val getSelectedCardTemplate: GetSelectedCardTemplateUseCase,
    private val setSelectedCardTemplate: SetSelectedCardTemplateUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TemplatesUiState())
    val uiState: StateFlow<TemplatesUiState> = _uiState.asStateFlow()

    init {
        // Seed the saved value and the draft from whatever is already stored.
        viewModelScope.launch {
            getSelectedCardTemplate().collect { template ->
                _uiState.update { state ->
                    val saved = state.copy(selectedTemplateId = template.id)
                    if (state.hasPendingEdit) {
                        // Every preference shares one DataStore, so an unrelated write
                        // (a language change, say) re-emits this flow. It must not
                        // silently discard the choice being made on screen.
                        saved
                    } else {
                        saved.copy(pendingTemplateId = template.id)
                    }
                }
            }
        }
    }

    /** Marks [template] as the pending choice without persisting it. */
    fun selectTemplate(template: CardTemplate) {
        _uiState.update { state ->
            state.copy(
                pendingTemplateId = template.id,
                hasPendingEdit = template.id != state.selectedTemplateId
            )
        }
    }

    /**
     * Commits the pending choice to preferences.
     *
     * Writes only when the draft actually differs from what is saved, so a Save
     * with no changes is a no-op.
     */
    fun saveSelection() {
        val pending = _uiState.value.pendingTemplate
        if (!_uiState.value.hasPendingEdit) return
        viewModelScope.launch { setSelectedCardTemplate(pending) }
    }
}
