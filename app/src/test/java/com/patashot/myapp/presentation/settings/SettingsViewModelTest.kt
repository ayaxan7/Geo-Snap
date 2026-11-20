package com.patashot.myapp.presentation.settings

import android.app.Activity
import com.patashot.myapp.core.review.InAppReviewManager
import com.patashot.myapp.testutils.FakePreferencesRepository
import com.patashot.myapp.testutils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `auto save is off by default even if permission is granted`() = runTest {
        val prefs = FakePreferencesRepository()
        val vm = SettingsViewModel(prefs)

        vm.setAutoSaveToDevice(true)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.autoSaveToDevice)
    }

    @Test
    fun `auto save turns on when both permission and preference are enabled`() = runTest {
        val prefs = FakePreferencesRepository(initialAutoSave = true)
        val vm = SettingsViewModel(prefs)

        vm.setAutoSaveToDevice(true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.autoSaveToDevice)
    }

    @Test
    fun `disabling auto save preference turns switch off`() = runTest {
        val prefs = FakePreferencesRepository(initialAutoSave = true)
        val vm = SettingsViewModel(prefs)
        vm.setAutoSaveToDevice(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.autoSaveToDevice)

        vm.setAutoSaveEnabled(false)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.autoSaveToDevice)
    }

    @Test
    fun `revoking permission turns auto save switch off even if preference is true`() = runTest {
        val prefs = FakePreferencesRepository(initialAutoSave = true)
        val vm = SettingsViewModel(prefs)
        vm.setAutoSaveToDevice(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.autoSaveToDevice)

        vm.setAutoSaveToDevice(false)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.autoSaveToDevice)
    }

    @Test
    fun `requesting turn off shows the revoke dialog only`() = runTest {
        val vm = SettingsViewModel(FakePreferencesRepository(initialAutoSave = true))
        vm.setAutoSaveToDevice(true)
        advanceUntilIdle()

        vm.requestTurnOff()

        assertTrue(vm.uiState.value.showRevokeDialog)
        assertFalse(vm.uiState.value.showEnableDialog)
    }

    @Test
    fun `permanent denial shows the enable dialog only`() = runTest {
        val vm = SettingsViewModel(FakePreferencesRepository())

        vm.requestEnableViaSettings()

        assertTrue(vm.uiState.value.showEnableDialog)
        assertFalse(vm.uiState.value.showRevokeDialog)
    }

    @Test
    fun `dismissing the dialog clears it without navigating`() = runTest {
        val vm = SettingsViewModel(FakePreferencesRepository(initialAutoSave = true))
        vm.requestTurnOff()

        vm.dismissDialogs()

        assertFalse(vm.uiState.value.showRevokeDialog)
        assertFalse(vm.uiState.value.showEnableDialog)
    }

    @Test
    fun `confirming navigation clears the dialog and emits once`() = runTest {
        val vm = SettingsViewModel(FakePreferencesRepository(initialAutoSave = true))
        vm.requestTurnOff()

        vm.confirmOpenSettings()

        assertFalse(vm.uiState.value.showRevokeDialog)
        assertFalse(vm.uiState.value.showEnableDialog)
        assertEquals(SettingsViewModel.NavigationTarget.AppInfo, vm.navigation.first())
    }

    @Test
    fun `notifications mirror the reported permission state`() = runTest {
        val vm = SettingsViewModel(FakePreferencesRepository())

        vm.setNotificationsEnabled(true)
        assertTrue(vm.uiState.value.notificationsEnabled)

        vm.setNotificationsEnabled(false)
        assertFalse(vm.uiState.value.notificationsEnabled)
    }

    @Test
    fun `notifications default to off`() = runTest {
        assertFalse(SettingsViewModel(FakePreferencesRepository()).uiState.value.notificationsEnabled)
    }

    @Test
    fun `requesting notification turn off shows only the notification dialog`() = runTest {
        val vm = SettingsViewModel(FakePreferencesRepository())

        vm.requestTurnOffNotifications()

        assertTrue(vm.uiState.value.showNotificationRevokeDialog)
        assertFalse(vm.uiState.value.showNotificationEnableDialog)
        assertFalse(vm.uiState.value.showRevokeDialog)
        assertFalse(vm.uiState.value.showEnableDialog)
    }

    @Test
    fun `permanent notification denial shows only the notification enable dialog`() = runTest {
        val vm = SettingsViewModel(FakePreferencesRepository())

        vm.requestEnableNotificationsViaSettings()

        assertTrue(vm.uiState.value.showNotificationEnableDialog)
        assertFalse(vm.uiState.value.showNotificationRevokeDialog)
    }

    @Test
    fun `dismissing clears the notification dialogs too`() = runTest {
        val vm = SettingsViewModel(FakePreferencesRepository())
        vm.requestTurnOffNotifications()

        vm.dismissDialogs()

        assertFalse(vm.uiState.value.showNotificationRevokeDialog)
        assertFalse(vm.uiState.value.showNotificationEnableDialog)
    }

    @Test
    fun `confirming a notification dialog emits the notification target`() = runTest {
        val vm = SettingsViewModel(FakePreferencesRepository())
        vm.requestTurnOffNotifications()

        vm.confirmOpenNotificationSettings()

        assertFalse(vm.uiState.value.showNotificationRevokeDialog)
        assertEquals(SettingsViewModel.NavigationTarget.Notifications, vm.navigation.first())
    }

    @Test
    fun `notification dialogs do not disturb the media dialog state`() = runTest {
        val vm = SettingsViewModel(FakePreferencesRepository(initialAutoSave = true))
        vm.setAutoSaveToDevice(true)
        advanceUntilIdle()

        vm.requestTurnOffNotifications()
        vm.requestEnableViaSettings()

        assertTrue(vm.uiState.value.showNotificationRevokeDialog)
        assertTrue(vm.uiState.value.showEnableDialog)
        assertTrue(vm.uiState.value.autoSaveToDevice)
    }

    @Test
    fun `rateApp invokes inAppReviewManager and falls back to PlayStoreListing navigation target`() = runTest {
        val reviewManager = object : InAppReviewManager {
            override fun launchReviewFlow(activity: Activity?, onFallback: () -> Unit) {
                onFallback()
            }
        }
        val vm = SettingsViewModel(FakePreferencesRepository(), reviewManager)

        vm.rateApp(activity = null)

        assertEquals(SettingsViewModel.NavigationTarget.PlayStoreListing, vm.navigation.first())
    }
}
