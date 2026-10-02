package com.chirag.geosnap.core.update

import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.AppUpdateResult
import com.google.android.play.core.ktx.requestCompleteUpdate
import com.google.android.play.core.ktx.requestUpdateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import com.google.android.play.core.install.model.AppUpdateType as PlayUpdateType

/**
 * [UpdateSignalSource] backed by the official Play Core in-app update library.
 *
 * This is the only place that knows about Play Core types; everything
 * downstream speaks the plain [UpdateSignal] vocabulary.
 */
class PlayUpdateSignalSource(
    private val appUpdateManager: AppUpdateManager
) : UpdateSignalSource {

    /**
     * Retained from the most recent [AppUpdateResult.Available]. Play requires
     * the matching [AppUpdateInfo] to start a flow, and it is refreshed every
     * time availability is re-checked.
     */
    private var currentUpdateInfo: AppUpdateInfo? = null

    override fun signals(): Flow<UpdateSignal> = appUpdateManager.requestUpdateFlow()
        .map { result -> result.toSignal() }
        .catch { error ->
            // Play throws InstallException when its service is unreachable, but a
            // bind failure can surface anything at all; report it, never crash.
            Log.w(TAG, "Update request failed", error)
            emit(UpdateSignal.Failed(error.message ?: error::class.java.simpleName))
        }

    override fun start(
        type: AppUpdateType,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        val updateInfo = currentUpdateInfo
        if (updateInfo == null) {
            Log.w(TAG, "Cannot start $type update flow: Play reported no update info")
            return
        }
        val playType = when (type) {
            AppUpdateType.FLEXIBLE -> PlayUpdateType.FLEXIBLE
            AppUpdateType.IMMEDIATE -> PlayUpdateType.IMMEDIATE
        }
        val started = appUpdateManager.startUpdateFlowForResult(
            updateInfo,
            launcher,
            AppUpdateOptions.newBuilder(playType).build()
        )
        Log.i(TAG, "startUpdateFlow type=$playType started=$started")
    }

    override suspend fun complete() {
        appUpdateManager.requestCompleteUpdate()
        Log.i(TAG, "Flexible update completion requested; app will restart")
    }

    private fun AppUpdateResult.toSignal(): UpdateSignal = when (this) {
        is AppUpdateResult.Available -> {
            currentUpdateInfo = updateInfo
            availableSignal(updateInfo)
        }

        is AppUpdateResult.InProgress -> UpdateSignal.InProgress
        is AppUpdateResult.Downloaded -> UpdateSignal.Downloaded
        AppUpdateResult.NotAvailable -> UpdateSignal.NotAvailable
    }

    private fun availableSignal(updateInfo: AppUpdateInfo): UpdateSignal =
        when (updateInfo.updateAvailability()) {
            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ->
                UpdateSignal.ImmediateInProgress

            UpdateAvailability.UPDATE_AVAILABLE -> when {
                updateInfo.isUpdateTypeAllowed(PlayUpdateType.FLEXIBLE) ->
                    UpdateSignal.Available(AppUpdateType.FLEXIBLE)

                updateInfo.isUpdateTypeAllowed(PlayUpdateType.IMMEDIATE) ->
                    UpdateSignal.Available(AppUpdateType.IMMEDIATE)

                else -> UpdateSignal.Failed(
                    "Update available but Play permits neither update type. " +
                        "Failed preconditions: ${failedPreconditions(updateInfo)}"
                )
            }

            UpdateAvailability.UPDATE_NOT_AVAILABLE -> UpdateSignal.NotAvailable

            else -> UpdateSignal.Failed("Update availability is unknown")
        }

    private fun failedPreconditions(updateInfo: AppUpdateInfo): Set<Int> = runCatching {
        updateInfo.getFailedUpdatePreconditions(
            AppUpdateOptions.newBuilder(PlayUpdateType.FLEXIBLE).build()
        )
    }.getOrElse { emptySet() }

    private companion object {
        const val TAG = "InAppUpdate"
    }
}