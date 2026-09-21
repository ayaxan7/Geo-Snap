package com.chirag.geosnap.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chirag.geosnap.domain.usecase.CompleteOnboardingUseCase
import com.chirag.geosnap.domain.usecase.GetSelectedLanguageUseCase
import com.chirag.geosnap.domain.usecase.SetSelectedLanguageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
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
        val englishName: String
    )

    data class UiState(
        val languages: List<Language> = supportedLanguages(),
        val selectedCode: String? = null,
        val saving: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getSelectedLanguage()
                .collect { existing ->
                    setSelected(existing ?: defaultLanguageCode())
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

    private fun defaultLanguageCode(): String {
        val systemLanguage = Locale.getDefault().language.lowercase()
        return _uiState.value.languages
            .firstOrNull { it.code == systemLanguage }?.code
            ?: DEFAULT_LANGUAGE
    }

    companion object {
        const val DEFAULT_LANGUAGE = "en"

        fun supportedLanguages(): List<Language> = listOf(
            Language("en", "English", "English"),
            Language("hi", "हिन्दी", "Hindi"),
            Language("te", "తెలుగు", "Telugu"),
            Language("ta", "தமிழ்", "Tamil"),
            Language("kn", "ಕನ್ನಡ", "Kannada")
        )
    }
}