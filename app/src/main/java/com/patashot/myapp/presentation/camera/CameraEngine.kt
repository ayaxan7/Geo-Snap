package com.patashot.myapp.presentation.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.io.File
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the camera implementation (CameraX) so the ViewModel and the
 * UI can be unit tested with a fake engine.
 *
 * The engine owns the hardware binding (PreviewView surface, lens selection,
 * capture / recording). All suspend functions run on the main thread and can
 * throw on failure; the ViewModel maps throws to user-facing errors.
 */
interface CameraEngine {

    enum class LensFacing { BACK, FRONT }

    val hasFlash: StateFlow<Boolean>
    val flashEnabled: StateFlow<Boolean>
    val isRecording: StateFlow<Boolean>

    /**
     * Unbinds any previous use cases and binds hardware for [mode] using the
     * given [lensFacing]. The caller must re-invoke this after [previewView] or
     * [lifecycleOwner] becomes available.
     */
    suspend fun bind(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        lensFacing: LensFacing,
        mode: CameraMode
    )

    /** Re-binds the camera to the requested lens; returns true on success. */
    suspend fun setLensFacing(lensFacing: LensFacing): Boolean

    /** Toggles flash/torch and returns the new state. */
    suspend fun toggleFlash(): Boolean

    /** Zooms in (`factor > 1`) or out (`factor < 1`), clamped to the lens limits. */
    suspend fun zoomBy(factor: Float)

    /** Captures a single photo into a unique temp file and returns it. */
    suspend fun capturePhoto(): File

    /** Starts a video recording into a unique temp file and returns it. */
    suspend fun startRecording(): File

    /** Stops the active recording and returns its temp file. */
    suspend fun stopRecording(): File

    /** Unbinds all use cases and releases camera resources. */
    fun release()
}