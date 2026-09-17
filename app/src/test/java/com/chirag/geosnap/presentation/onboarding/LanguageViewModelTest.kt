package com.chirag.geosnap.presentation.onboarding

import com.chirag.geosnap.domain.usecase.CompleteOnboardingUseCase
import com.chirag.geosnap.domain.usecase.GetSelectedLanguageUseCase
import com.chirag.geosnap.domain.usecase.SetSelectedLanguageUseCase
import com.chirag.geosnap.testutils.FakePreferencesRepository
import com.chirag.geosnap.testutils.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `defaults to English when nothing stored`() = runTest(mainDispatcherRule.scheduler) {
        val repository = FakePreferencesRepository()
        val vm = createViewModel(repository)

        advanceUntilIdle()

        assertEquals(LanguageViewModel.DEFAULT_LANGUAGE, vm.uiState.value.selectedCode)
    }

    @Test
    fun `restores previously selected language`() = runTest(mainDispatcherRule.scheduler) {
        val repository = FakePreferencesRepository(initialLanguage = "gu")
        val vm = createViewModel(repository)

        advanceUntilIdle()

        assertEquals("gu", vm.uiState.value.selectedCode)
    }

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
}