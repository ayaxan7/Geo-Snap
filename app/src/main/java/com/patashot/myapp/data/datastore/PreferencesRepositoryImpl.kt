package com.patashot.myapp.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.patashot.myapp.domain.repository.PreferencesRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Shared key so startup locale application can read the persisted language off the same DataStore. */
internal val SELECTED_LANGUAGE_KEY = stringPreferencesKey("selected_language")

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : PreferencesRepository {

    private object Keys {
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
        val SELECTED_CARD_TEMPLATE = stringPreferencesKey("selected_card_template")
        val AUTO_SAVE_TO_DEVICE = booleanPreferencesKey("auto_save_to_device")
    }

    override val hasCompletedOnboarding: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[Keys.HAS_COMPLETED_ONBOARDING] ?: false }

    override val selectedLanguage: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[Keys.SELECTED_LANGUAGE] }

    override val selectedCardTemplateId: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[Keys.SELECTED_CARD_TEMPLATE] }

    override val isAutoSaveEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[Keys.AUTO_SAVE_TO_DEVICE] ?: false }

    override suspend fun setOnboardingCompleted() {
        dataStore.edit { preferences ->
            preferences[Keys.HAS_COMPLETED_ONBOARDING] = true
        }
    }

    override suspend fun setSelectedLanguage(languageCode: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SELECTED_LANGUAGE] = languageCode
        }
    }

    override suspend fun setSelectedCardTemplateId(templateId: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SELECTED_CARD_TEMPLATE] = templateId
        }
    }

    override suspend fun setAutoSaveEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_SAVE_TO_DEVICE] = enabled
        }
    }
}
