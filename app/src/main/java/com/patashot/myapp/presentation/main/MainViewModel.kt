package com.patashot.myapp.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patashot.myapp.domain.usecase.GetOnboardingStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Decides where the user should land on app start (onboarding vs. home)
 * based on the persisted DataStore state. `loading == true` while the
 * preference is still being read so navigation never races the disk read.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val getOnboardingState: GetOnboardingStateUseCase
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val onboardingCompleted: Boolean? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getOnboardingState()
                .map { UiState(loading = false, onboardingCompleted = it) }
                .collect { _uiState.value = it }
        }
    }
}