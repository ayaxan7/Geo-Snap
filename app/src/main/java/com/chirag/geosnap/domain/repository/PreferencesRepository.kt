package com.chirag.geosnap.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Persistent application preferences backed by Jetpack Preferences DataStore.
 *
 * Onboarding/session state is intentionally exposed as a [Flow] so consumers
 * (navigation) can react to the value asynchronously without blocking the UI thread.
 */
interface PreferencesRepository {

    val hasCompletedOnboarding: Flow<Boolean>

    val selectedLanguage: Flow<String?>

    suspend fun setOnboardingCompleted()

    suspend fun setSelectedLanguage(languageCode: String)
}