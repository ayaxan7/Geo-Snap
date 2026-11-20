package com.patashot.myapp.domain.repository

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

    /**
     * Id of the geotag card template applied to new captures, or null when the
     * user has never picked one and [com.patashot.myapp.presentation.camera.CardTemplates.DEFAULT]
     * should be used.
     */
    val selectedCardTemplateId: Flow<String?>

    val isAutoSaveEnabled: Flow<Boolean>

    val homeLaunchCount: Flow<Int>

    suspend fun setOnboardingCompleted()

    suspend fun setSelectedLanguage(languageCode: String)

    suspend fun setSelectedCardTemplateId(templateId: String)

    suspend fun setAutoSaveEnabled(enabled: Boolean)

    suspend fun incrementHomeLaunchCount(): Int
}
