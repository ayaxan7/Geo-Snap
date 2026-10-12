package com.patashot.myapp.presentation.camera

/**
 * Card-level metrics that every geotag card template shares: the shell the card
 * is drawn in and where that shell sits in the frame.
 *
 * Templates only vary what goes *inside* the card, so these stay plain `const val`
 * and remain shared by the live [GeoTagCard] composable and the
 * [CanvasGeoTagOverlayRenderer]. Per-template metrics live in [GeoTagCardStyle].
 */
object GeoTagCardShell {

    // Card shell
    const val CARD_WIDTH_DP = 340f
    const val CARD_CORNER_DP = 18f
    const val CARD_BORDER_DP = 1f

    // Colors (0xAARRGGBB)
    const val CARD_BACKGROUND_COLOR = 0xB3000000L
    const val CARD_BORDER_COLOR = 0xFFFFFFFFL

    // Inner padding. Vertical is half the horizontal so the content sits close to
    // the top: the top gap above the map/text is 8dp, and all three bottom edges
    // (map, date row, logo) align on one baseline just above the card bottom edge.
    const val PADDING_HORIZONTAL_DP = 16f
    const val PADDING_VERTICAL_DP = 8f
}
