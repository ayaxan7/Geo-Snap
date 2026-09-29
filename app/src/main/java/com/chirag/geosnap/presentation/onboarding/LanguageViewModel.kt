package com.chirag.geosnap.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chirag.geosnap.domain.usecase.CompleteOnboardingUseCase
import com.chirag.geosnap.domain.usecase.GetSelectedLanguageUseCase
import com.chirag.geosnap.domain.usecase.SetSelectedLanguageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Supports language selection as the final onboarding step. The selected
 * language is persisted to DataStore and onboarding is completed only when the
 * user confirms, so they always return to Home on subsequent launches.
 */
@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val getSelectedLanguage: GetSelectedLanguageUseCase,
    private val setSelectedLanguage: SetSelectedLanguageUseCase,
    private val completeOnboarding: CompleteOnboardingUseCase
) : ViewModel() {

    data class Language(
        val code: String,
        val nativeName: String,
        val englishName: String,
        /** Button label shown once this language is selected (in that language). */
        val continueWith: String
    )

    data class UiState(
        val languages: List<Language> = supportedLanguages(),
        val selectedCode: String? = null,
        val saving: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Override the synchronous default with whatever is actually persisted.
        viewModelScope.launch {
            getSelectedLanguage().collect { existing ->
                if (existing != null) {
                    setSelected(existing)
                }
            }
        }
    }

    fun selectLanguage(code: String) {
        setSelected(code)
        viewModelScope.launch {
            setSelectedLanguage(code)
        }
    }

    fun confirmAndContinue(onCompleted: () -> Unit) {
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val currentCode = _uiState.value.selectedCode
                if (currentCode != null) {
                    setSelectedLanguage(currentCode)
                }
                completeOnboarding()
                onCompleted()
            } finally {
                _uiState.update { it.copy(saving = false) }
            }
        }
    }

    private fun setSelected(code: String) {
        _uiState.update { it.copy(selectedCode = code) }
    }

    companion object {
        const val DEFAULT_LANGUAGE = "en"

        fun supportedLanguages(): List<Language> = listOf(
            Language("en", "English", "English", "Continue in English"),
            Language("hi", "हिन्दी", "Hindi", "हिन्दी में जारी रखें"),
            Language("te", "తెలుగు", "Telugu", "తెలుగులో కొనసాగించండి"),
            Language("ta", "தமிழ்", "Tamil", "தமிழில் தொடரவும்"),
            Language("kn", "ಕನ್ನಡ", "Kannada", "ಕನ್ನಡದಲ್ಲಿ ಮುಂದುವರಿಸಿ")
        )
    }
}