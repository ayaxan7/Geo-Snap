package com.patashot.myapp.core.update

import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

/**
 * Decides what GeoSnap should do about Google Play in-app updates.
 *
 * All policy lives here and no Play Core types cross this boundary, so the
 * behaviour is unit testable without a device or a Play Store. The dialogs
 * themselves are always Play's own — this class never builds update UI, it only
 * decides whether to ask Play to show one.
 *
 * Policy:
 * - The preferred type (flexible by default) is offered once per session when
 *   Play reports the update as available and permitted.
 * - An immediate flow is only ever *resumed*, never started on the user's
 *   behalf. Play reports [UpdateSignal.ImmediateInProgress] only for flows it
 *   has already launched, so honouring it cannot interrupt anyone who was not
 *   already mid-update.
 * - Once a flow has been started it is not started again in the same session,
 *   so a user who dismisses the dialog is not repeatedly interrupted.
 */
class InAppUpdateManager(
    private val source: UpdateSignalSource,
    private val preferredType: AppUpdateType = AppUpdateType.FLEXIBLE,
    val automaticChecksEnabled: Boolean = true
) {

    /**
     * Type of the flow started in this session, or null if none has been. Kept
     * on the (singleton) manager so the guard survives configuration changes.
     */
    private var startedType: AppUpdateType? = null

    /**
     * Actions the UI layer should carry out. Cold: collecting this triggers a
     * fresh Play update request, so collect it with lifecycle awareness.
     */
    fun actions(): Flow<InAppUpdateAction> =
        if (!automaticChecksEnabled) emptyFlow() else flow {
            source.signals().collect { signal ->
                actionFor(signal)?.let { emit(it) }
            }
        }

    /**
     * Installs a downloaded flexible update. Play tears the app down and
     * restarts it, so this normally does not return.
     */
    suspend fun completeUpdate() {
        try {
            source.complete()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Log.w(TAG, "Could not complete flexible update", error)
        }
    }

    /** Launches Play's own dialog for [type]. */
    fun startFlow(
        type: AppUpdateType,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        startedType = type
        runCatching { source.start(type, launcher) }
            .onFailure { Log.w(TAG, "Could not start $type update flow", it) }
    }

    private fun actionFor(signal: UpdateSignal): InAppUpdateAction? = when (signal) {
        is UpdateSignal.NotAvailable -> {
            Log.i(TAG, "No update available")
            null
        }

        is UpdateSignal.InProgress -> {
            Log.i(TAG, "Flexible update in progress; app remains usable")
            null
        }

        is UpdateSignal.Downloaded -> {
            Log.i(TAG, "Flexible update downloaded, awaiting restart")
            InAppUpdateAction.Downloaded
        }

        is UpdateSignal.Failed -> {
            Log.w(TAG, "Update check unavailable: ${signal.reason}")
            null
        }

        is UpdateSignal.Available -> startAvailable(signal.type)

        // Resume only: Play reports this solely for immediate flows it has
        // already started, so re-showing it never forces an update.
        is UpdateSignal.ImmediateInProgress -> {
            Log.i(TAG, "Resuming in-progress immediate update")
            InAppUpdateAction.Start(AppUpdateType.IMMEDIATE)
        }
    }

    private fun startAvailable(type: AppUpdateType): InAppUpdateAction? = when {
        type != preferredType -> {
            Log.i(TAG, "Only $type updates are permitted but $preferredType is preferred; not interrupting")
            null
        }

        startedType != null -> {
            Log.i(TAG, "$type flow already started this session; not repeating")
            null
        }

        else -> {
            startedType = type
            Log.i(TAG, "Offering $type update")
            InAppUpdateAction.Start(type)
        }
    }

    private companion object {
        const val TAG = "InAppUpdate"
    }
}