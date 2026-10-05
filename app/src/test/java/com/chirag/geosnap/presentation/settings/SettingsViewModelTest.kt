package com.chirag.geosnap.presentation.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @Test
    fun `auto save mirrors the reported permission state`() {
        val vm = SettingsViewModel()

        vm.setAutoSaveToDevice(true)
        assertTrue(vm.uiState.value.autoSaveToDevice)

        vm.setAutoSaveToDevice(false)
        assertFalse(vm.uiState.value.autoSaveToDevice)
    }

    @Test
    fun `requesting turn off shows the revoke dialog only`() {
        val vm = SettingsViewModel()
        vm.setAutoSaveToDevice(true)

        vm.requestTurnOff()

        assertTrue(vm.uiState.value.showRevokeDialog)
        assertFalse(vm.uiState.value.showEnableDialog)
    }

    @Test
    fun `permanent denial shows the enable dialog only`() {
        val vm = SettingsViewModel()

        vm.requestEnableViaSettings()

        assertTrue(vm.uiState.value.showEnableDialog)
        assertFalse(vm.uiState.value.showRevokeDialog)
    }

    @Test
    fun `dismissing the dialog clears it without navigating`() {
        val vm = SettingsViewModel()
        vm.requestTurnOff()

        vm.dismissDialogs()

        assertFalse(vm.uiState.value.showRevokeDialog)
        assertFalse(vm.uiState.value.showEnableDialog)
    }

    @Test
    fun `confirming navigation clears the dialog and emits once`() = runTest {
        val vm = SettingsViewModel()
        vm.requestTurnOff()

        vm.confirmOpenSettings()

        assertFalse(vm.uiState.value.showRevokeDialog)
        assertFalse(vm.uiState.value.showEnableDialog)
        assertEquals(SettingsViewModel.NavigationTarget.AppInfo, vm.navigation.first())
    }

    @Test
    fun `notifications mirror the reported permission state`() {
        val vm = SettingsViewModel()

        vm.setNotificationsEnabled(true)
        assertTrue(vm.uiState.value.notificationsEnabled)

        vm.setNotificationsEnabled(false)
        assertFalse(vm.uiState.value.notificationsEnabled)
    }

    @Test
    fun `notifications default to off`() {
        assertFalse(SettingsViewModel().uiState.value.notificationsEnabled)
    }

    @Test
    fun `requesting notification turn off shows only the notification dialog`() {
        val vm = SettingsViewModel()

        vm.requestTurnOffNotifications()

        assertTrue(vm.uiState.value.showNotificationRevokeDialog)
        assertFalse(vm.uiState.value.showNotificationEnableDialog)
        assertFalse(vm.uiState.value.showRevokeDialog)
        assertFalse(vm.uiState.value.showEnableDialog)
    }

    @Test
    fun `permanent notification denial shows only the notification enable dialog`() {
        val vm = SettingsViewModel()

        vm.requestEnableNotificationsViaSettings()

        assertTrue(vm.uiState.value.showNotificationEnableDialog)
        assertFalse(vm.uiState.value.showNotificationRevokeDialog)
    }

    @Test
    fun `dismissing clears the notification dialogs too`() {
        val vm = SettingsViewModel()
        vm.requestTurnOffNotifications()

        vm.dismissDialogs()

        assertFalse(vm.uiState.value.showNotificationRevokeDialog)
        assertFalse(vm.uiState.value.showNotificationEnableDialog)
    }

    @Test
    fun `confirming a notification dialog emits the notification target`() = runTest {
        val vm = SettingsViewModel()
        vm.requestTurnOffNotifications()

        vm.confirmOpenNotificationSettings()

        assertFalse(vm.uiState.value.showNotificationRevokeDialog)
        assertEquals(SettingsViewModel.NavigationTarget.Notifications, vm.navigation.first())
    }

    @Test
    fun `notification dialogs do not disturb the media dialog state`() {
        val vm = SettingsViewModel()
        vm.setAutoSaveToDevice(true)

        vm.requestTurnOffNotifications()
        vm.requestEnableViaSettings()

        assertTrue(vm.uiState.value.showNotificationRevokeDialog)
        assertTrue(vm.uiState.value.showEnableDialog)
        assertTrue(vm.uiState.value.autoSaveToDevice)
    }
}