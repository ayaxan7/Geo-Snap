package com.chirag.geosnap.presentation.camera

import androidx.annotation.StringRes
import com.chirag.geosnap.R

/**
 * How the pieces inside a geotag card are arranged relative to each other.
 *
 * The card shell (size, background, corner, on-screen position) is identical for
 * every template and lives in [GeoTagCardShell]; a layout only describes the
 * arrangement of the map thumbnail, the text block and the logo.
 *
 * Adding a variant means adding a value here and a matching branch in both
 * [GeoTagCard] and [CanvasGeoTagOverlayRenderer] so the preview and the burned
 * overlay stay in parity.
 */
enum class CardLayout {
    /**
     * Map thumbnail on the left, text block in the middle, logo on the right, all
     * bottom-aligned onto one baseline.
     */
    MAP_LEFT
}

/**
 * Per-template visual metrics (dp/sp values and 0xAARRGGBB colors).
 *
 * One instance describes a single template's appearance; [GeoTagCardShell] still
 * owns everything the templates have in common.
 */
data class GeoTagCardStyle(
    val textColor: Long,
    val textSecondaryColor: Long,
    val iconAccentColor: Long,
    val logoBackgroundColor: Long,
    val logoVisible: Boolean,
    val mapBackgroundColor: Long,

    // Map thumbnail
    val mapWidthDp: Float,
    val mapHeightDp: Float,
    val mapCornerDp: Float,

    // Spacing
    val spacerMapDetailsDp: Float,
    val spacerDetailsLogoDp: Float,
    val detailsPaddingVerticalDp: Float,
    val detailsLineGapDp: Float,
    val iconTextGapDp: Float,

    // Typography
    val cityTextSizeSp: Float,
    val addressTextSizeSp: Float,
    val addressLineHeightSp: Float,
    val coordinatesTextSizeSp: Float,
    val dateTextSizeSp: Float,

    // Icons
    val iconSizeDp: Float,

    // Logo
    val logoSizeDp: Float,
    val logoCornerDp: Float
)

/**
 * A selectable geotag card design.
 *
 * [id] is the value persisted in preferences, so it must stay stable: renaming a
 * template should not orphan the preference of anyone who already picked it.
 */
data class CardTemplate(
    val id: String,
    @StringRes val labelRes: Int,
    val layout: CardLayout,
    val style: GeoTagCardStyle
)

/**
 * Registry of every geotag card template, and the lookup used to resolve a
 * persisted id back into a template.
 *
 * [fromId] falls back to [DEFAULT] so a preference written by a build that had a
 * template this build no longer ships still resolves to a usable card.
 */
object CardTemplates {

    const val DEFAULT_ID = "classic"

    /** The original card: dark shell, map on the left, logo on the right. */
    val DEFAULT = CardTemplate(
        id = DEFAULT_ID,
        labelRes = R.string.template_classic,
        layout = CardLayout.MAP_LEFT,
        style = GeoTagCardStyle(
            textColor = 0xFFFFFFFFL,
            textSecondaryColor = 0xD1FFFFFFL,
            iconAccentColor = 0xFFFF0000L,
            logoBackgroundColor = 0xFF0BA84AL,
            logoVisible = true,
            mapBackgroundColor = 0xFF808080L,
            mapWidthDp = 80f,
            mapHeightDp = 105f,
            mapCornerDp = 9f,
            spacerMapDetailsDp = 14f,
            spacerDetailsLogoDp = 10f,
            detailsPaddingVerticalDp = 2f,
            detailsLineGapDp = 3f,
            iconTextGapDp = 5f,
            cityTextSizeSp = 16f,
            addressTextSizeSp = 11f,
            addressLineHeightSp = 15f,
            coordinatesTextSizeSp = 11f,
            dateTextSizeSp = 10.5f,
            iconSizeDp = 13f,
            logoSizeDp = 44f,
            logoCornerDp = 9f
        )
    )

    /** Every selectable template, in the order the picker lists them. */
    val ALL: List<CardTemplate> = listOf(DEFAULT)

    /** Resolves a persisted [id], falling back to [DEFAULT] when it is unknown or absent. */
    fun fromId(id: String?): CardTemplate = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
