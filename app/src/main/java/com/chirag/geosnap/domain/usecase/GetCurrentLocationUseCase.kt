package com.chirag.geosnap.domain.usecase

import com.chirag.geosnap.domain.model.GeoCoordinate
import com.chirag.geosnap.domain.repository.LocationRepository
import javax.inject.Inject

class GetCurrentLocationUseCase @Inject constructor(
    private val repository: LocationRepository
) {
    suspend operator fun invoke(): GeoCoordinate? = repository.getCurrentLocation()
}