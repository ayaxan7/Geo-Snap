package com.chirag.geosnap.presentation.camera

import com.chirag.geosnap.domain.model.GeoCoordinate
import java.io.File

/**
 * A capture awaiting review.
 *
 * [cardContent] is a snapshot taken at capture time rather than a view of the live
 * camera state, so the location the user reviews (and the one burnt into the saved
 * media) cannot drift while the preview is open.
 */
data class PreviewData(
    val file: File,
    val coordinate: GeoCoordinate?,
    val isVideo: Boolean,
    val cardContent: GeoTagCardContent
)
