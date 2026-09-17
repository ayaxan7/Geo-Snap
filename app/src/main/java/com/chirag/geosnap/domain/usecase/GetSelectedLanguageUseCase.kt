package com.chirag.geosnap.domain.usecase

import com.chirag.geosnap.domain.repository.PreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetSelectedLanguageUseCase @Inject constructor(
    private val repository: PreferencesRepository
) {
    operator fun invoke(): Flow<String?> = repository.selectedLanguage
}