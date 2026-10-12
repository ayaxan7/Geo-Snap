package com.patashot.myapp.presentation.camera

import com.patashot.myapp.domain.model.GeoCoordinate
import java.io.File
import java.io.Serializable

/**
 * A capture awaiting review.
 *
 * [cardContent] is a snapshot taken at capture time rather than a view of the live
 * camera state, so the location the user reviews (and the one burnt into the saved
 * media) cannot drift while the preview is open.
 *
 * Serializable so the ViewModel can park it in `SavedStateHandle` and restore the
 * preview after the process is killed while the user is in another app (e.g. the
 * share sheet).
 */
data class PreviewData(
    val file: File,
    val coordinate: GeoCoordinate?,
    val isVideo: Boolean,
    val cardContent: GeoTagCardContent
) : Serializable
