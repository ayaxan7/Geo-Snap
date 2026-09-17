package com.chirag.geosnap.domain.usecase

import com.chirag.geosnap.domain.repository.PreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetOnboardingStateUseCase @Inject constructor(
    private val repository: PreferencesRepository
) {
    operator fun invoke(): Flow<Boolean> = repository.hasCompletedOnboarding
}