package com.patashot.myapp.presentation.camera

/**
 * A [CardTemplate] resolved into the concrete dp/sp/color values a renderer needs,
 * with the shared [GeoTagCardShell] already folded in.
 *
 * Both renderers read from this type rather than from the shell and style
 * separately, so there is exactly one place where a template becomes numbers.
 *
 * [of] takes a [scale] because the template picker renders genuine miniature cards:
 * every dp and sp value is multiplied down together, which keeps the card's
 * proportions identical to the real thing instead of squashing it into a smaller
 * box. Colors are unaffected by scale.
 */
data class GeoTagCardMetrics(
    val cardWidthDp: Float,
    val cardCornerDp: Float,
    val cardBorderDp: Float,
    val cardBackgroundColor: Long,
    val cardBorderColor: Long,
    val paddingHorizontalDp: Float,
    val paddingVerticalDp: Float,
    val textColor: Long,
    val textSecondaryColor: Long,
    val iconAccentColor: Long,
    val accentColor: Long,
    val logoBackgroundColor: Long,
    val logoVisible: Boolean,
    val mapBackgroundColor: Long,
    val mapWidthDp: Float,
    val mapHeightDp: Float,
    val mapCornerDp: Float,
    val spacerMapDetailsDp: Float,
    val spacerDetailsLogoDp: Float,
    val detailsPaddingVerticalDp: Float,
    val detailsLineGapDp: Float,
    val iconTextGapDp: Float,
    val cityTextSizeSp: Float,
    val addressTextSizeSp: Float,
    val addressLineHeightSp: Float,
    val coordinatesTextSizeSp: Float,
    val dateTextSizeSp: Float,
    val iconSizeDp: Float,
    val logoHeightDp: Float,
    val logoCornerDp: Float
) {
    companion object {
        fun of(template: CardTemplate, scale: Float = 1f): GeoTagCardMetrics {
            val s = template.style
            return GeoTagCardMetrics(
                cardWidthDp = GeoTagCardShell.CARD_WIDTH_DP * scale,
                cardCornerDp = GeoTagCardShell.CARD_CORNER_DP * scale,
                cardBorderDp = GeoTagCardShell.CARD_BORDER_DP * scale,
                cardBackgroundColor = GeoTagCardShell.CARD_BACKGROUND_COLOR,
                cardBorderColor = GeoTagCardShell.CARD_BORDER_COLOR,
                paddingHorizontalDp = GeoTagCardShell.PADDING_HORIZONTAL_DP * scale,
                paddingVerticalDp = GeoTagCardShell.PADDING_VERTICAL_DP * scale,
                textColor = s.textColor,
                textSecondaryColor = s.textSecondaryColor,
                iconAccentColor = s.iconAccentColor,
                accentColor = s.accentColor,
                logoBackgroundColor = s.logoBackgroundColor,
                logoVisible = s.logoVisible,
                mapBackgroundColor = s.mapBackgroundColor,
                mapWidthDp = s.mapWidthDp * scale,
                mapHeightDp = s.mapHeightDp * scale,
                mapCornerDp = s.mapCornerDp * scale,
                spacerMapDetailsDp = s.spacerMapDetailsDp * scale,
                spacerDetailsLogoDp = s.spacerDetailsLogoDp * scale,
                detailsPaddingVerticalDp = s.detailsPaddingVerticalDp * scale,
                detailsLineGapDp = s.detailsLineGapDp * scale,
                iconTextGapDp = s.iconTextGapDp * scale,
                cityTextSizeSp = s.cityTextSizeSp * scale,
                addressTextSizeSp = s.addressTextSizeSp * scale,
                addressLineHeightSp = s.addressLineHeightSp * scale,
                coordinatesTextSizeSp = s.coordinatesTextSizeSp * scale,
                dateTextSizeSp = s.dateTextSizeSp * scale,
                iconSizeDp = s.iconSizeDp * scale,
                logoHeightDp = s.logoHeightDp * scale,
                logoCornerDp = s.logoCornerDp * scale
            )
        }
    }
}

/** Resolves this template into renderable metrics, optionally scaled for a preview. */
fun CardTemplate.metrics(scale: Float = 1f): GeoTagCardMetrics = GeoTagCardMetrics.of(this, scale)
