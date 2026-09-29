package com.chirag.geosnap.presentation.onboarding

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chirag.geosnap.R
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
        @StringRes val nativeNameRes: Int,
        @StringRes val englishNameRes: Int,
        /** Button label shown once this language is selected (in that language). */
        @StringRes val continueLabelRes: Int
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
            Language(
                code = "en",
                nativeNameRes = R.string.language_en_native,
                englishNameRes = R.string.language_en_english,
                continueLabelRes = R.string.language_en_continue
            ),
            Language(
                code = "hi",
                nativeNameRes = R.string.language_hi_native,
                englishNameRes = R.string.language_hi_english,
                continueLabelRes = R.string.language_hi_continue
            ),
            Language(
                code = "te",
                nativeNameRes = R.string.language_te_native,
                englishNameRes = R.string.language_te_english,
                continueLabelRes = R.string.language_te_continue
            ),
            Language(
                code = "ta",
                nativeNameRes = R.string.language_ta_native,
                englishNameRes = R.string.language_ta_english,
                continueLabelRes = R.string.language_ta_continue
            ),
            Language(
                code = "kn",
                nativeNameRes = R.string.language_kn_native,
                englishNameRes = R.string.language_kn_english,
                continueLabelRes = R.string.language_kn_continue
            )
        )
    }
}