package com.chirag.geosnap.domain.usecase

import com.chirag.geosnap.domain.repository.LocationRepository
import javax.inject.Inject

class IsLocationEnabledUseCase @Inject constructor(
    private val repository: LocationRepository
) {
    suspend operator fun invoke(): Boolean = repository.isLocationEnabled()
}