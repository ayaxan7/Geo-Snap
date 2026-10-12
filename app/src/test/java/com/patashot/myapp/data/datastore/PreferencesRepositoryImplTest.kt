package com.patashot.myapp.data.datastore

import com.patashot.myapp.testutils.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesRepositoryImplTest {

    @Test
    fun `onboarding defaults to false and language to null`() = runTest {
        val repository = PreferencesRepositoryImpl(InMemoryPreferencesDataStore())

        assertFalse(repository.hasCompletedOnboarding.first())
        assertNull(repository.selectedLanguage.first())
    }

    @Test
    fun `completing onboarding persists true`() = runTest {
        val repository = PreferencesRepositoryImpl(InMemoryPreferencesDataStore())

        repository.setOnboardingCompleted()

        assertTrue(repository.hasCompletedOnboarding.first())
    }

    @Test
    fun `selected language persists`() = runTest {
        val repository = PreferencesRepositoryImpl(InMemoryPreferencesDataStore())

        repository.setSelectedLanguage("gu")

        assertEquals("gu", repository.selectedLanguage.first())
    }

    @Test
    fun `state survives repository recreation over the same datastore`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()

        PreferencesRepositoryImpl(dataStore).setOnboardingCompleted()
        PreferencesRepositoryImpl(dataStore).setSelectedLanguage("gu")

        val fresh = PreferencesRepositoryImpl(dataStore)
        assertTrue(fresh.hasCompletedOnboarding.first())
        assertEquals("gu", fresh.selectedLanguage.first())
    }
}