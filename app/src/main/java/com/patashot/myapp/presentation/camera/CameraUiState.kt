package com.patashot.myapp.presentation.camera

import androidx.compose.runtime.Immutable
import com.patashot.myapp.domain.model.GeoCoordinate

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
    val filterStrength: Float = 1f,
    // The geotag card design applied to captures taken from now on.
    val selectedTemplate: CardTemplate = CardTemplates.DEFAULT,
    val timerSeconds: Int = 0,
    val countdownSeconds: Int? = null,
    val isCountdownActive: Boolean = false
)