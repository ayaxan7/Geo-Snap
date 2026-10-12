package com.patashot.myapp.domain.usecase

import com.patashot.myapp.domain.repository.PreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetOnboardingStateUseCase @Inject constructor(
    private val repository: PreferencesRepository
) {
    operator fun invoke(): Flow<Boolean> = repository.hasCompletedOnboarding
}