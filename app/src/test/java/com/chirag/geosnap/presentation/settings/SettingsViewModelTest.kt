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
        assertEquals(SettingsViewModel.OpenAppSettings, vm.navigation.first())
    }
}