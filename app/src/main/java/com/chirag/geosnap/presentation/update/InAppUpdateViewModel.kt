package com.chirag.geosnap.presentation.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chirag.geosnap.core.update.AppUpdateType
import com.chirag.geosnap.core.update.InAppUpdateAction
import com.chirag.geosnap.core.update.InAppUpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Adapts [InAppUpdateManager] to Compose.
 *
 * Deliberately thin: all update policy lives in the manager, and collection is
 * driven by the UI's lifecycle so Play listeners only exist while the app is in
 * the foreground.
 */
@HiltViewModel
class InAppUpdateViewModel @Inject constructor(
    private val manager: InAppUpdateManager
) : ViewModel() {

    /**
     * Cold: collecting triggers a fresh update request. Collect it only while
     * resumed, so returning to the app re-checks the current update state.
     */
    val actions: Flow<InAppUpdateAction> = manager.actions()

    /** Shows Play's own dialog for [type]. */
    fun startFlow(
        type: AppUpdateType,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) = manager.startFlow(type, launcher)

    /** Installs a downloaded flexible update; Play restarts the app afterwards. */
    fun completeUpdate() = viewModelScope.launch { manager.completeUpdate() }
}