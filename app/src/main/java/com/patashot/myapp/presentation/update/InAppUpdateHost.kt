package com.patashot.myapp.presentation.update

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.lifecycleScope
import com.patashot.myapp.R
import com.patashot.myapp.core.update.InAppUpdateAction
import com.google.android.play.core.install.model.ActivityResult as PlayActivityResult
import kotlinx.coroutines.launch

/**
 * Hosts Google Play in-app updates for the whole app.
 *
 * Update availability is requested while the app is resumed and the request is
 * dropped when it pauses, so returning to the app re-checks the current state
 * without leaving listeners attached in the background. The update dialogs
 * themselves are always Play's — the only GeoSnap-drawn part is the snackbar
 * offering a restart once a flexible update has downloaded.
 */
@Composable
fun InAppUpdateHost(
    snackbarHostState: SnackbarHostState,
    viewModel: InAppUpdateViewModel = hiltViewModel()
) {
    val updateReadyMessage = stringResource(R.string.update_downloaded_message)
    val restartLabel = stringResource(R.string.update_restart_action)

    val updateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // Nothing to recover from: a cancelled or failed flow simply leaves the
        // installed version in place and the next launch re-checks.
        val outcome = when (result.resultCode) {
            Activity.RESULT_OK -> "Update flow completed"
            Activity.RESULT_CANCELED -> "Update flow cancelled"
            PlayActivityResult.RESULT_IN_APP_UPDATE_FAILED -> "Update flow failed"
            else -> "Update flow ended with result code ${result.resultCode}"
        }
        Log.i(TAG, outcome)
    }

    LifecycleResumeEffect(viewModel) {
        val listening = lifecycleScope.launch {
            viewModel.actions.collect { action ->
                when (action) {
                    is InAppUpdateAction.Start ->
                        viewModel.startFlow(action.type, updateLauncher)

                    InAppUpdateAction.Downloaded -> {
                        val choice = snackbarHostState.showSnackbar(
                            message = updateReadyMessage,
                            actionLabel = restartLabel,
                            duration = SnackbarDuration.Long
                        )
                        if (choice == SnackbarResult.ActionPerformed) {
                            viewModel.completeUpdate()
                        }
                    }
                }
            }
        }
        // Dropping the collection when the app pauses unregisters Play's
        // listener; re-entering resume re-checks the current update state.
        onPauseOrDispose { listening.cancel() }
    }
}

private const val TAG = "InAppUpdate"