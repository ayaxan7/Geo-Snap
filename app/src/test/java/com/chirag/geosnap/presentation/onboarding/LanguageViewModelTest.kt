package com.chirag.geosnap.presentation.onboarding

import com.chirag.geosnap.domain.usecase.CompleteOnboardingUseCase
import com.chirag.geosnap.domain.usecase.GetSelectedLanguageUseCase
import com.chirag.geosnap.domain.usecase.SetSelectedLanguageUseCase
import com.chirag.geosnap.testutils.FakePreferencesRepository
import com.chirag.geosnap.testutils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LanguageViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(repository: FakePreferencesRepository): LanguageViewModel =
        LanguageViewModel(
            getSelectedLanguage = GetSelectedLanguageUseCase(repository),
            setSelectedLanguage = SetSelectedLanguageUseCase(repository),
            completeOnboarding = CompleteOnboardingUseCase(repository)
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `nothing is selected when nothing is stored`() = runTest(mainDispatcherRule.scheduler) {
        val repository = FakePreferencesRepository()
        val vm = createViewModel(repository)

        advanceUntilIdle()

        assertNull(vm.uiState.value.selectedCode)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `no radio button is pre-selected as soon as the screen opens`() =
        runTest(mainDispatcherRule.scheduler) {
            val repository = FakePreferencesRepository()
            val vm = createViewModel(repository)

            // No advanceUntilIdle: nothing must be selected even before any async emission.
            assertNull(vm.uiState.value.selectedCode)
        }

    @Test
    fun `every supported language has a continue label`() {
        val languages = LanguageViewModel.supportedLanguages()
        assertTrue(languages.isNotEmpty())
        languages.forEach { language ->
            assertTrue(
                "language ${language.code} has a blank continue label",
                language.continueWith.isNotBlank()
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `restores previously selected language`() = runTest(mainDispatcherRule.scheduler) {
        val repository = FakePreferencesRepository(initialLanguage = "gu")
        val vm = createViewModel(repository)

        advanceUntilIdle()

        assertEquals("gu", vm.uiState.value.selectedCode)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `selecting a language persists it`() = runTest(mainDispatcherRule.scheduler) {
        val repository = FakePreferencesRepository()
        val vm = createViewModel(repository)

        advanceUntilIdle()
        vm.selectLanguage("hi")
        advanceUntilIdle()

        assertEquals("hi", repository.languageValue)
        assertEquals("hi", vm.uiState.value.selectedCode)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `confirm completes onboarding once and invokes callback`() =
        runTest(mainDispatcherRule.scheduler) {
            val repository = FakePreferencesRepository()
            val vm = createViewModel(repository)

            advanceUntilIdle()
            var completed = 0
            vm.confirmAndContinue { completed++ }
            advanceUntilIdle()

            assertTrue(repository.onboardingValue)
            assertFalse(vm.uiState.value.saving)
            assertEquals(1, completed)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `confirm persists the selected language before completing`() =
        runTest(mainDispatcherRule.scheduler) {
            val repository = FakePreferencesRepository()
            val vm = createViewModel(repository)

            advanceUntilIdle()
            vm.selectLanguage("ta")
            advanceUntilIdle()
            vm.confirmAndContinue {}
            advanceUntilIdle()

            assertEquals("ta", repository.languageValue)
            assertTrue(repository.onboardingValue)
        }
}