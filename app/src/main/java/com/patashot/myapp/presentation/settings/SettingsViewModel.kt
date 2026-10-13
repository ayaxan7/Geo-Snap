package com.patashot.myapp.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patashot.myapp.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the "Auto Save to Device" and "Notifications" settings.
 *
 * Auto Save combines both system media permissions and a persisted user preference
 * in DataStore (default false). When the user turns Auto Save on, permissions are
 * requested if needed, and DataStore is updated to true upon grant. When turned off,
 * the DataStore preference is set to false directly.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    data class UiState(
        val autoSaveToDevice: Boolean = false,
        val notificationsEnabled: Boolean = false,
        val showRevokeDialog: Boolean = false,
        val showEnableDialog: Boolean = false,
        val showNotificationRevokeDialog: Boolean = false,
        val showNotificationEnableDialog: Boolean = false
    )

    private var hasMediaPermission = false
    private var autoSavePreference = false

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _navigation = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigation: Flow<NavigationTarget> = _navigation.receiveAsFlow()

    /** What the screen should open for a confirmed dialog. */
    sealed interface NavigationTarget {
        data object AppInfo : NavigationTarget
        data object Notifications : NavigationTarget
    }

    /** Where [confirmOpenSettings] should send the user, for the open dialog. */
    private enum class SettingsTarget { APP_INFO, NOTIFICATIONS }

    init {
        viewModelScope.launch {
            preferencesRepository.isAutoSaveEnabled.collect { pref ->
                autoSavePreference = pref
                updateAutoSaveState()
            }
        }
    }

    private fun updateAutoSaveState() {
        _uiState.update { it.copy(autoSaveToDevice = hasMediaPermission && autoSavePreference) }
    }

    /** Re-syncs the switch with the real media permission state. */
    fun setAutoSaveToDevice(granted: Boolean) {
        hasMediaPermission = granted
        updateAutoSaveState()
    }

    /** Updates the auto save preference in DataStore. */
    fun setAutoSaveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoSaveEnabled(enabled)
        }
    }

    /** Re-syncs the switch with the real notification permission state. */
    fun setNotificationsEnabled(granted: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = granted) }
    }

    /** The user tried to switch Auto Save off; explain they must revoke. */
    fun requestTurnOff() {
        _uiState.update { it.copy(showRevokeDialog = true) }
    }

    /** The user can only enable via App Info (permission permanently denied). */
    fun requestEnableViaSettings() {
        _uiState.update { it.copy(showEnableDialog = true) }
    }

    /**
     * The user tried to switch notifications off.
     *
     * Only reached when the platform refuses to let the app do it itself (Android
     * 13+), so the dialog points at system notification settings.
     */
    fun requestTurnOffNotifications() {
        _uiState.update { it.copy(showNotificationRevokeDialog = true) }
    }

    /** Notifications are permanently denied, so only system settings can grant them. */
    fun requestEnableNotificationsViaSettings() {
        _uiState.update { it.copy(showNotificationEnableDialog = true) }
    }

    fun dismissDialogs() {
        _uiState.update {
            it.copy(
                showRevokeDialog = false,
                showEnableDialog = false,
                showNotificationRevokeDialog = false,
                showNotificationEnableDialog = false
            )
        }
    }

    /** Confirms navigation for the media-permission dialog and clears it. */
    fun confirmOpenSettings() {
        confirmNavigation(SettingsTarget.APP_INFO)
    }

    /** Confirms navigation for a notification dialog and clears it. */
    fun confirmOpenNotificationSettings() {
        confirmNavigation(SettingsTarget.NOTIFICATIONS)
    }

    private fun confirmNavigation(target: SettingsTarget) {
        dismissDialogs()
        _navigation.trySend(
            when (target) {
                SettingsTarget.APP_INFO -> NavigationTarget.AppInfo
                SettingsTarget.NOTIFICATIONS -> NavigationTarget.Notifications
            }
        )
    }
}
