package com.chirag.geosnap.presentation.camera

import androidx.compose.runtime.Immutable
import com.chirag.geosnap.domain.model.GeoCoordinate

@Immutable
data class CameraUiState(
    val isReady: Boolean = false,
    val isRecording: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val lensFacing: CameraEngine.LensFacing = CameraEngine.LensFacing.BACK,
    val flashEnabled: Boolean = false,
    val hasFlash: Boolean = false,
    val isProcessing: Boolean = false,
    val error: CameraError? = null,
    val currentLocation: GeoCoordinate? = null,
    val cityString: String? = null,
    val fullAddress: String? = null,
    val isLocationEnabled: Boolean = false,
    val showLocationDialog: Boolean = false,
    val pendingPreview: PreviewData? = null,
    val filter: CaptureFilter = CaptureFilter.ORIGINAL,
    // 1 = the filter at its normal (slider-center) strength.
    val filterStrength: Float = 1f
)