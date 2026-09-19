package com.chirag.geosnap.presentation.camera

import android.graphics.Bitmap

/**
 * Renders a [GeoTagCardContent] snapshot into an overlay [Bitmap] that gets
 * burned into captured photos and videos.
 */
interface GeoTagOverlayRenderer {

    /**
     * Renders [content] to an overlay bitmap, or null when nothing can be
     * produced (e.g. renderer unavailable).
     */
    suspend fun render(content: GeoTagCardContent): Bitmap?
}