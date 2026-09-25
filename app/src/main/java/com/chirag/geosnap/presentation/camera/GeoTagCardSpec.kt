package com.chirag.geosnap.presentation.camera

/**
 * Single source of truth for the geotag card's visual metrics (dp/sp values and
 * 0xAARRGGBB colors). Shared by the live [GeoTagCard] composable and the
 * [CanvasGeoTagOverlayRenderer] so the card burned into captured media matches
 * the on-screen preview exactly.
 */
object GeoTagCardSpec {

    // Card shell
    const val CARD_WIDTH_DP = 340f
    const val CARD_CORNER_DP = 18f
    const val CARD_BORDER_DP = 1f

    // Colors (0xAARRGGBB)
    const val CARD_BACKGROUND_COLOR = 0xB3000000L
    const val CARD_BORDER_COLOR = 0xFFFFFFFFL
    const val TEXT_COLOR = 0xFFFFFFFFL
    const val TEXT_SECONDARY_COLOR = 0xD1FFFFFFL
    const val ICON_RED = 0xFFFF0000L
    const val LOGO_GREEN = 0xFF0BA84AL
    const val MAP_BACKGROUND_COLOR = 0xFF808080L

    // Inner padding. Vertical is half the horizontal so the content sits close to
// the top: the top gap above the map/text is 8dp, and all three bottom edges
// (map, date row, logo) align on one baseline just above the card bottom edge.
    const val PADDING_HORIZONTAL_DP = 16f
    const val PADDING_VERTICAL_DP = 8f

    // Map thumbnail
    const val MAP_WIDTH_DP = 80f
    const val MAP_HEIGHT_DP = 105f
    const val MAP_CORNER_DP = 9f

    // Spacing
    const val SPACER_MAP_DETAILS_DP = 14f
    const val SPACER_DETAILS_LOGO_DP = 10f
    const val DETAILS_PADDING_VERTICAL_DP = 2f
    const val DETAILS_LINE_GAP_DP = 3f
    const val ICON_TEXT_GAP_DP = 5f

    // Typography
    const val CITY_TEXT_SIZE_SP = 16f
    const val ADDRESS_TEXT_SIZE_SP = 11f
    const val ADDRESS_LINE_HEIGHT_SP = 15f
    const val COORDINATES_TEXT_SIZE_SP = 11f
    const val DATE_TEXT_SIZE_SP = 10.5f

    // Icons
    const val ICON_SIZE_DP = 13f

    // Logo
    const val LOGO_SIZE_DP = 44f
    const val LOGO_CORNER_DP = 9f
}