package com.patashot.myapp.core.update

import com.patashot.myapp.testutils.FakeUpdateSignalSource
import com.patashot.myapp.testutils.NoOpUpdateLauncher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InAppUpdateManagerTest {

    private val launcher = NoOpUpdateLauncher()

    private fun managerFor(
        signals: List<UpdateSignal>,
        preferredType: AppUpdateType = AppUpdateType.FLEXIBLE,
        automaticChecksEnabled: Boolean = true
    ): Pair<InAppUpdateManager, FakeUpdateSignalSource> {
        val source = FakeUpdateSignalSource(signals)
        val manager = InAppUpdateManager(
            source = source,
            preferredType = preferredType,
            automaticChecksEnabled = automaticChecksEnabled
        )
        return manager to source
    }

    @Test
    fun `available flexible update starts the flexible flow`() = runTest {
        val (manager, _) = managerFor(listOf(UpdateSignal.Available(AppUpdateType.FLEXIBLE)))

        assertEquals(
            listOf(InAppUpdateAction.Start(AppUpdateType.FLEXIBLE)),
            manager.actions().toList()
        )
    }

    @Test
    fun `repeated availability signals only start the flow once`() = runTest {
        val (manager, _) = managerFor(
            listOf(
                UpdateSignal.Available(AppUpdateType.FLEXIBLE),
                UpdateSignal.Available(AppUpdateType.FLEXIBLE),
                UpdateSignal.Available(AppUpdateType.FLEXIBLE)
            )
        )

        assertEquals(
            listOf(InAppUpdateAction.Start(AppUpdateType.FLEXIBLE)),
            manager.actions().toList()
        )
    }

    @Test
    fun `immediate update is not offered when flexible is preferred`() = runTest {
        val (manager, _) = managerFor(listOf(UpdateSignal.Available(AppUpdateType.IMMEDIATE)))

        assertTrue(manager.actions().toList().isEmpty())
    }

    @Test
    fun `immediate update is offered when it is the preferred type`() = runTest {
        val (manager, _) = managerFor(
            signals = listOf(UpdateSignal.Available(AppUpdateType.IMMEDIATE)),
            preferredType = AppUpdateType.IMMEDIATE
        )

        assertEquals(
            listOf(InAppUpdateAction.Start(AppUpdateType.IMMEDIATE)),
            manager.actions().toList()
        )
    }

    @Test
    fun `in-progress immediate flow is resumed even when flexible is preferred`() = runTest {
        val (manager, _) = managerFor(listOf(UpdateSignal.ImmediateInProgress))

        assertEquals(
            listOf(InAppUpdateAction.Start(AppUpdateType.IMMEDIATE)),
            manager.actions().toList()
        )
    }

    @Test
    fun `returning to the app resumes an in-progress immediate flow again`() = runTest {
        val (manager, _) = managerFor(listOf(UpdateSignal.ImmediateInProgress))

        // Re-collecting models the app being resumed a second time; the resume
        // must not be swallowed by the already-started guard.
        assertEquals(
            listOf(InAppUpdateAction.Start(AppUpdateType.IMMEDIATE)),
            manager.actions().toList()
        )
        assertEquals(
            listOf(InAppUpdateAction.Start(AppUpdateType.IMMEDIATE)),
            manager.actions().toList()
        )
    }

    @Test
    fun `downloaded update reports the restart action without starting a flow`() = runTest {
        val (manager, _) = managerFor(listOf(UpdateSignal.Downloaded))

        assertEquals(listOf(InAppUpdateAction.Downloaded), manager.actions().toList())
    }

    @Test
    fun `downloaded update is reported even after the flow was started`() = runTest {
        val (manager, _) = managerFor(
            listOf(
                UpdateSignal.Available(AppUpdateType.FLEXIBLE),
                UpdateSignal.InProgress,
                UpdateSignal.Downloaded
            )
        )

        assertEquals(
            listOf(
                InAppUpdateAction.Start(AppUpdateType.FLEXIBLE),
                InAppUpdateAction.Downloaded
            ),
            manager.actions().toList()
        )
    }

    @Test
    fun `states that need no user input produce no actions`() = runTest {
        val (manager, _) = managerFor(
            listOf(
                UpdateSignal.NotAvailable,
                UpdateSignal.InProgress,
                UpdateSignal.Failed("Play Store unavailable")
            )
        )

        assertTrue(manager.actions().toList().isEmpty())
    }

    @Test
    fun `automatic checks can be disabled`() = runTest {
        val (manager, _) = managerFor(
            signals = listOf(UpdateSignal.Available(AppUpdateType.FLEXIBLE)),
            automaticChecksEnabled = false
        )

        assertTrue(manager.actions().toList().isEmpty())
    }

    @Test
    fun `startFlow asks Play to show the requested type`() = runTest {
        val (manager, source) = managerFor(emptyList())

        manager.startFlow(AppUpdateType.FLEXIBLE, launcher)

        assertEquals(listOf(AppUpdateType.FLEXIBLE), source.startedFlows)
    }

    @Test
    fun `a failure to show the dialog is swallowed`() = runTest {
        val (manager, source) = managerFor(emptyList())
        source.startError = IllegalStateException("Play is not available")

        manager.startFlow(AppUpdateType.FLEXIBLE, launcher)
    }

    @Test
    fun `completeUpdate installs the downloaded update`() = runTest {
        val (manager, source) = managerFor(emptyList())

        manager.completeUpdate()

        assertEquals(1, source.completeCalls)
    }

    @Test
    fun `a failure to complete the update is swallowed`() = runTest {
        val (manager, source) = managerFor(emptyList())
        source.completeError = IllegalStateException("nothing downloaded")

        manager.completeUpdate()

        assertEquals(1, source.completeCalls)
    }
}