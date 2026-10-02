package com.chirag.geosnap.presentation.update

import com.chirag.geosnap.core.update.AppUpdateType
import com.chirag.geosnap.core.update.InAppUpdateAction
import com.chirag.geosnap.core.update.InAppUpdateManager
import com.chirag.geosnap.core.update.UpdateSignal
import com.chirag.geosnap.testutils.FakeUpdateSignalSource
import com.chirag.geosnap.testutils.MainDispatcherRule
import com.chirag.geosnap.testutils.NoOpUpdateLauncher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InAppUpdateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val launcher = NoOpUpdateLauncher()

    private fun viewModelFor(
        signals: List<UpdateSignal>,
        preferredType: AppUpdateType = AppUpdateType.FLEXIBLE
    ): Pair<InAppUpdateViewModel, FakeUpdateSignalSource> {
        val source = FakeUpdateSignalSource(signals)
        val manager = InAppUpdateManager(source = source, preferredType = preferredType)
        return InAppUpdateViewModel(manager) to source
    }

    @Test
    fun `actions expose the manager's decisions`() = runTest(mainDispatcherRule.scheduler) {
        val (viewModel, _) = viewModelFor(
            listOf(UpdateSignal.Available(AppUpdateType.FLEXIBLE))
        )

        assertEquals(
            InAppUpdateAction.Start(AppUpdateType.FLEXIBLE),
            viewModel.actions.first()
        )
    }

    @Test
    fun `startFlow forwards the requested type to Play`() = runTest {
        val (viewModel, source) = viewModelFor(emptyList())

        viewModel.startFlow(AppUpdateType.IMMEDIATE, launcher)

        assertEquals(listOf(AppUpdateType.IMMEDIATE), source.startedFlows)
    }

    @Test
    fun `completeUpdate runs on the view model scope`() = runTest(mainDispatcherRule.scheduler) {
        val (viewModel, source) = viewModelFor(emptyList())

        viewModel.completeUpdate()
        advanceUntilIdle()

        assertEquals(1, source.completeCalls)
    }
}