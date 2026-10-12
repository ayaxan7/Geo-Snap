package com.patashot.myapp.domain.usecase

import com.patashot.myapp.domain.repository.LocationRepository
import javax.inject.Inject

class IsLocationEnabledUseCase @Inject constructor(
    private val repository: LocationRepository
) {
    suspend operator fun invoke(): Boolean = repository.isLocationEnabled()
}