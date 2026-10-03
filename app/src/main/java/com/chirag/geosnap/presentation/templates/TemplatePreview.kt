package com.chirag.geosnap.presentation.templates

import com.chirag.geosnap.presentation.camera.CardTemplate
import com.chirag.geosnap.presentation.camera.GeoTagCardContent
import com.chirag.geosnap.presentation.camera.GeoTagCardShell

/**
 * Fixed sample used to preview a template in the picker.
 *
 * Deliberately not the user's live location: previews should be identical on every
 * row and every launch so the list reads as a catalogue of designs, and the picker
 * is reachable from Settings where showing a real address would be out of place.
 */
private const val PREVIEW_CITY = "Bengaluru"
private const val PREVIEW_ADDRESS = "MG Road, Bengaluru, Karnataka 560001"
private const val PREVIEW_LATITUDE = 12.9716
private const val PREVIEW_LONGITUDE = 77.5946

/**
 * A fixed epoch so the preview's date line is stable rather than tracking the clock.
 *
 * Renders as "Monday, 15/01/2024 - 9:30 AM".
 */
private const val PREVIEW_TIMESTAMP = 1_705_314_600_000L

/**
 * Scale the preview card is drawn at.
 *
 * Small enough to fit a phone list row, large enough that the arrangement of the
 * map, text and logo is still readable. Every metric is scaled together, so the
 * preview is a true reduction of the real card rather than a squashed one.
 */
internal const val TEMPLATE_PREVIEW_SCALE = 0.44f

/**
 * Width reserved for a preview row's card.
 *
 * Only the width is fixed: the height is left to the card's own content so a
 * template taller than today's still previews correctly once it is added.
 */
internal val TEMPLATE_PREVIEW_WIDTH_DP =
    GeoTagCardShell.CARD_WIDTH_DP * TEMPLATE_PREVIEW_SCALE

/** Sample card content for previewing [template]. */
internal fun previewContent(template: CardTemplate): GeoTagCardContent = GeoTagCardContent(
    cityString = PREVIEW_CITY,
    fullAddress = PREVIEW_ADDRESS,
    latitude = PREVIEW_LATITUDE,
    longitude = PREVIEW_LONGITUDE,
    timestamp = PREVIEW_TIMESTAMP,
    staticMapUrl = null,
    template = template
)
