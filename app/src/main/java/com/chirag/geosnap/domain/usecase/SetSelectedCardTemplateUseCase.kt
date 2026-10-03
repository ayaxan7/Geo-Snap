package com.chirag.geosnap.domain.usecase

import com.chirag.geosnap.domain.repository.PreferencesRepository
import com.chirag.geosnap.presentation.camera.CardTemplate
import javax.inject.Inject

/** Persists the geotag card template the user picked as their new default. */
class SetSelectedCardTemplateUseCase @Inject constructor(
    private val repository: PreferencesRepository
) {
    suspend operator fun invoke(template: CardTemplate) {
        repository.setSelectedCardTemplateId(template.id)
    }
}
