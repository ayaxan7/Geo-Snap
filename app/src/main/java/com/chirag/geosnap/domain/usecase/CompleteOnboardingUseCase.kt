package com.chirag.geosnap.domain.usecase

import com.chirag.geosnap.domain.repository.PreferencesRepository
import javax.inject.Inject

class CompleteOnboardingUseCase @Inject constructor(
    private val repository: PreferencesRepository
) {
    suspend operator fun invoke() {
        repository.setOnboardingCompleted()
    }
}