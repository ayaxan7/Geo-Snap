package com.chirag.geosnap.presentation.onboarding

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class PermissionsViewModel @Inject constructor() : ViewModel() {

    /** A permission the app needs, with user-facing copy and a placeholder icon. */
    data class PermissionRequirement(
        val permission: String,
        @StringRes val label: Int,
        @StringRes val description: Int,
        val iconRes: Int
    )

    data class PermissionItemState(
        val permission: String,
        @StringRes val label: Int,
        @StringRes val description: Int,
        val iconRes: Int,
        val granted: Boolean = false,
        val permanentlyDenied: Boolean = false
    )

    data class UiState(
        val items: List<PermissionItemState> = emptyList(),
        val allGranted: Boolean = false,
        val canOpenSettings: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Seeds the list of required permissions together with their current grant
     * status (checked by the caller since permission checks need a Context).
     */
    fun initialize(requirements: List<PermissionRequirement>, currentGrants: Map<String, Boolean>) {
        val items = requirements.map { requirement ->
            PermissionItemState(
                permission = requirement.permission,
                label = requirement.label,
                description = requirement.description,
                iconRes = requirement.iconRes,
                granted = currentGrants[requirement.permission] == true
            )
        }
        _uiState.update { it.copy(items = items) }
        recompute()
    }

    /** Called when a permission request returns. */
    fun onPermissionResults(results: Map<String, Boolean>, permanentlyDenied: Set<String>) {
        _uiState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    val result = results[item.permission]
                    if (result != null) {
                        item.copy(
                            granted = result,
                            permanentlyDenied = !result && item.permission in permanentlyDenied
                        )
                    } else {
                        item
                    }
                }
            )
        }
        recompute()
    }

    /**
     * Re-checks grant status without wiping user-facing state, typically after
     * returning from the system settings screen.
     */
    fun refreshGrants(currentGrants: Map<String, Boolean>) {
        _uiState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    val granted = currentGrants[item.permission] == true
                    item.copy(granted = granted, permanentlyDenied = !granted && item.permanentlyDenied)
                }
            )
        }
        recompute()
    }

    private fun recompute() {
        _uiState.update { state ->
            val allGranted = state.items.isNotEmpty() && state.items.all { it.granted }
            val canOpenSettings = state.items.any { it.permanentlyDenied }
            state.copy(allGranted = allGranted, canOpenSettings = canOpenSettings)
        }
    }

    val requestablePermissions: List<String>
        get() = _uiState.value.items
            .filter { !it.granted && !it.permanentlyDenied }
            .map { it.permission }
}