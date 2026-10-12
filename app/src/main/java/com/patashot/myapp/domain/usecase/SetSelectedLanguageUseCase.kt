package com.patashot.myapp.domain.usecase

import com.patashot.myapp.domain.repository.PreferencesRepository
import javax.inject.Inject

class SetSelectedLanguageUseCase @Inject constructor(
    private val repository: PreferencesRepository
) {
    suspend operator fun invoke(languageCode: String) {
        repository.setSelectedLanguage(languageCode)
    }
}