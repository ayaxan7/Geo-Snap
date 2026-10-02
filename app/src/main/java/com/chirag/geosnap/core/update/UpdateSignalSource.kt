package com.chirag.geosnap.core.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.Flow

/**
 * Source of Google Play in-app update signals.
 *
 * Kept behind an interface so [InAppUpdateManager] can be exercised on the JVM
 * with a fake, with no Play Store or device involved.
 */
interface UpdateSignalSource {

    /**
     * Long-lived, conflating stream of availability and install progress.
     *
     * The flow should never complete on its own; it stays quiet when Play has
     * nothing to say. Play's own `requestUpdateFlow()` already has these
     * semantics.
     */
    fun signals(): Flow<UpdateSignal>

    /**
     * Shows Play's own dialog for [type]. Requires a launcher registered with
     * the `StartIntentSenderForResult` contract.
     */
    fun start(type: AppUpdateType, launcher: ActivityResultLauncher<IntentSenderRequest>)

    /** Installs a previously downloaded flexible update. */
    suspend fun complete()
}

/**
 * Used when Play's in-app update service is unavailable — for example a
 * sideloaded debug build, or a device with no Play Store. It reports nothing so
 * callers behave exactly as they would when no update exists.
 */
class NoOpUpdateSignalSource : UpdateSignalSource {

    override fun signals(): Flow<UpdateSignal> = kotlinx.coroutines.flow.emptyFlow()

    override fun start(
        type: AppUpdateType,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) = Unit

    override suspend fun complete() = Unit
}