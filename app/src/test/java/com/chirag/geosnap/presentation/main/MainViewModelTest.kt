package com.chirag.geosnap.presentation.main

import com.chirag.geosnap.domain.usecase.GetOnboardingStateUseCase
import com.chirag.geosnap.testutils.FakePreferencesRepository
import com.chirag.geosnap.testutils.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows loading then routes to onboarding when not completed`() =
        runTest(mainDispatcherRule.scheduler) {
            val vm = MainViewModel(
                getOnboardingState = GetOnboardingStateUseCase(FakePreferencesRepository())
            )

            assertTrue(vm.uiState.value.loading)

            advanceUntilIdle()

            assertFalse(vm.uiState.value.loading)
            assertEquals(false, vm.uiState.value.onboardingCompleted)
        }

    @Test
    fun `routes to home when onboarding completed`() = runTest(mainDispatcherRule.scheduler) {
        val vm = MainViewModel(
            getOnboardingState = GetOnboardingStateUseCase(
                FakePreferencesRepository(initialOnboarding = true)
            )
        )

        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.onboardingCompleted)
    }
}