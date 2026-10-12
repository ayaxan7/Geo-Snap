package com.patashot.myapp.domain.usecase

import com.patashot.myapp.domain.repository.PreferencesRepository
import com.patashot.myapp.presentation.camera.CardTemplate
import javax.inject.Inject

/** Persists the geotag card template the user picked as their new default. */
class SetSelectedCardTemplateUseCase @Inject constructor(
    private val repository: PreferencesRepository
) {
    suspend operator fun invoke(template: CardTemplate) {
        repository.setSelectedCardTemplateId(template.id)
    }
}
