package com.chirag.geosnap.domain.usecase

import com.chirag.geosnap.presentation.camera.CardTemplate
import com.chirag.geosnap.presentation.camera.CardTemplates
import com.chirag.geosnap.domain.repository.PreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observes the geotag card template applied to new captures.
 *
 * The persisted value is only an id; an unknown or absent id resolves to
 * [CardTemplates.DEFAULT] here so callers always receive a usable [CardTemplate].
 */
class GetSelectedCardTemplateUseCase @Inject constructor(
    private val repository: PreferencesRepository
) {
    operator fun invoke(): Flow<CardTemplate> = repository.selectedCardTemplateId
        .map { id -> CardTemplates.fromId(id) }
}
