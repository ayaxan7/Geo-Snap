package com.patashot.myapp.domain.usecase

import com.patashot.myapp.domain.model.GeoCoordinate
import com.patashot.myapp.domain.repository.LocationRepository
import javax.inject.Inject

class GetCurrentLocationUseCase @Inject constructor(
    private val repository: LocationRepository
) {
    suspend operator fun invoke(): GeoCoordinate? = repository.getCurrentLocation()
}