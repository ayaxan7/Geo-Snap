package com.patashot.myapp.presentation.camera

/** One-shot events emitted by [CameraViewModel] for the screen to consume. */
sealed interface CameraEvent {
    /** A capture/recording was saved successfully to MediaStore. */
    data class MediaSaved(val uri: String, val isVideo: Boolean) : CameraEvent

    /** A photo was saved and should be shared immediately (Quick Share). */
    data class QuickShareRequested(val uri: String) : CameraEvent
}