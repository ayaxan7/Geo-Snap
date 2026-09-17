package com.chirag.geosnap.domain.usecase

import com.chirag.geosnap.domain.repository.PreferencesRepository
import javax.inject.Inject

class SetSelectedLanguageUseCase @Inject constructor(
    private val repository: PreferencesRepository
) {
    suspend operator fun invoke(languageCode: String) {
        repository.setSelectedLanguage(languageCode)
    }
}