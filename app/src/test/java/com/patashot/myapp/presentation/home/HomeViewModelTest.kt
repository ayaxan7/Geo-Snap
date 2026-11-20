package com.patashot.myapp.presentation.home

import android.app.Activity
import com.patashot.myapp.core.review.InAppReviewManager
import com.patashot.myapp.testutils.FakePreferencesRepository
import com.patashot.myapp.testutils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class TestInAppReviewManager : InAppReviewManager {
        var launched = false
        var fallbackCalled = false

        override fun launchReviewFlow(activity: Activity?, onFallback: () -> Unit) {
            launched = true
        }
    }

    @Before
    fun setUp() {
        // Reset session state between tests
        HomeViewModel.hasCheckedReviewInSession = false
    }

    @Test
    fun `first launch does not trigger in-app review even if random is below threshold`() = runTest {
        val prefs = FakePreferencesRepository(initialHomeLaunchCount = 0)
        val reviewManager = TestInAppReviewManager()
        val viewModel = HomeViewModel(prefs, reviewManager).apply {
            randomProvider = { 0.1f } // < 0.2
        }

        viewModel.onHomeScreenOpened(activity = null)
        advanceUntilIdle()

        assertFalse("Review flow must not launch on first launch", reviewManager.launched)
    }

    @Test
    fun `subsequent launch triggers in-app review when random is below threshold`() = runTest {
        val prefs = FakePreferencesRepository(initialHomeLaunchCount = 1)
        val reviewManager = TestInAppReviewManager()
        val viewModel = HomeViewModel(prefs, reviewManager).apply {
            randomProvider = { 0.15f } // < 0.2
        }

        viewModel.onHomeScreenOpened(activity = null)
        advanceUntilIdle()

        assertTrue("Review flow should launch on subsequent launch when random < 0.2", reviewManager.launched)
    }

    @Test
    fun `subsequent launch does not trigger review when random is at or above threshold`() = runTest {
        val prefs = FakePreferencesRepository(initialHomeLaunchCount = 1)
        val reviewManager = TestInAppReviewManager()
        val viewModel = HomeViewModel(prefs, reviewManager).apply {
            randomProvider = { 0.2f } // >= 0.2
        }

        viewModel.onHomeScreenOpened(activity = null)
        advanceUntilIdle()

        assertFalse("Review flow must not launch when random >= 0.2", reviewManager.launched)
    }

    @Test
    fun `does not re-check review flow within the same app session`() = runTest {
        val prefs = FakePreferencesRepository(initialHomeLaunchCount = 1)
        val reviewManager = TestInAppReviewManager()
        val viewModel = HomeViewModel(prefs, reviewManager).apply {
            randomProvider = { 0.05f }
        }

        // First open in session
        viewModel.onHomeScreenOpened(activity = null)
        advanceUntilIdle()
        assertTrue(reviewManager.launched)

        // Reset tracking flag in mock
        reviewManager.launched = false

        // Second open in same session (e.g. returning to Home from Camera)
        viewModel.onHomeScreenOpened(activity = null)
        advanceUntilIdle()

        assertFalse("Should not re-trigger within the same session", reviewManager.launched)
    }
}
