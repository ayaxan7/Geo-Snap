package com.chirag.geosnap.presentation.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Drives the "Auto Save to Device" setting.
 *
 * The switch is strictly permission-driven (there is intentionally no app-side
 * "off" preference): it is on exactly when the Android media permission is
 * granted. Turning it off can only succeed by the user revoking the permission
 * in App Info → Permissions; the screen re-checks on resume and flips the
 * switch to match whatever they actually did.
 *
 * Permission checks happen in the UI layer (they need a Context), which then
 * feeds the boolean result via [setAutoSaveToDevice].
 */
@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    data class UiState(
        val autoSaveToDevice: Boolean = false,
        val showRevokeDialog: Boolean = false,
        val showEnableDialog: Boolean = false
    )

    /** Emitted when the user confirms navigation to the system App Info screen. */
    data object OpenAppSettings

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _navigation = Channel<OpenAppSettings>(Channel.BUFFERED)
    val navigation: Flow<OpenAppSettings> = _navigation.receiveAsFlow()

    /** Re-syncs the switch with the real media permission state. */
    fun setAutoSaveToDevice(granted: Boolean) {
        _uiState.update { it.copy(autoSaveToDevice = granted) }
    }

    /** The user tried to switch Auto Save off; explain they must revoke. */
    fun requestTurnOff() {
        _uiState.update { it.copy(showRevokeDialog = true) }
    }

    /** The user can only enable via App Info (permission permanently denied). */
    fun requestEnableViaSettings() {
        _uiState.update { it.copy(showEnableDialog = true) }
    }

    fun dismissDialogs() {
        _uiState.update { it.copy(showRevokeDialog = false, showEnableDialog = false) }
    }

    /** Confirms navigation to App Info and clears any open dialog. */
    fun confirmOpenSettings() {
        dismissDialogs()
        _navigation.trySend(OpenAppSettings)
    }
}