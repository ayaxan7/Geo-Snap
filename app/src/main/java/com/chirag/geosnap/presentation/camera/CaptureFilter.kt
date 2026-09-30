package com.chirag.geosnap.presentation.camera

/**
 * The five photo/video filters exposed on the preview screen's edit panel.
 *
 * Each filter is described by a full-strength `4x5` color matrix recipe; the
 * strength slider interpolates between the identity matrix and that recipe, so
 * `strength = 0` always reproduces the untouched capture. The same `FloatArray`
 * recipe powers the Compose preview (`ColorFilter.colorMatrix`), the video
 * pipeline (Media3 [`RgbMatrix`], which converts the offset column to
 * `1/255` space) and the burn-in pass (`ColorMatrixColorFilter`), keeping image
 * and video output visually identical.
 *
 * The math lives in pure Kotlin (mirroring the AOSP color-matrix conventions:
 * `0..255` offsets and BT.601 luminance weights `0.213/0.715/0.072`) so it can
 * be unit-tested on the JVM, where `android.graphics.ColorMatrix` is a no-op.
 */
enum class CaptureFilter {

    ORIGINAL,
    VIBRANT,
    SOFT_TONE,
    SHARP_BLACK,
    GRAY;

    /**
     * The `4x5` color matrix for a given [strength] in `0..1`. [ORIGINAL] (or a
     * non-positive strength) returns the identity matrix.
     */
    fun recipe(strength: Float): FloatArray {
        if (this == ORIGINAL) return IDENTITY_RECIPE.copyOf()
        val s = strength.coerceIn(0f, 1f)
        if (s <= 0f) return IDENTITY_RECIPE.copyOf()
        val full = fullRecipe()
        return FloatArray(20) { i ->
            IDENTITY_RECIPE[i] + (full[i] - IDENTITY_RECIPE[i]) * s
        }
    }

    private fun fullRecipe(): FloatArray = when (this) {
        ORIGINAL -> IDENTITY_RECIPE.copyOf()
        VIBRANT -> saturationMatrix(VIBRANT_SATURATION)
        SOFT_TONE -> {
            val matrix = saturationMatrix(SOFT_TONE_SATURATION)
            // Gentle brightness lift applied on the 0..255 offset column.
            matrix[4] = SOFT_TONE_BRIGHTNESS_OFFSET
            matrix[9] = SOFT_TONE_BRIGHTNESS_OFFSET
            matrix[14] = SOFT_TONE_BRIGHTNESS_OFFSET
            matrix
        }
        SHARP_BLACK -> {
            val matrix = saturationMatrix(0f)
            scaleOutput(
                matrix,
                SHARP_BLACK_CONTRAST,
                SHARP_BLACK_CONTRAST,
                SHARP_BLACK_CONTRAST
            )
            // Anchor the contrast around mid-grey (0..255 space).
            val offset = 128f * (1f - SHARP_BLACK_CONTRAST)
            matrix[4] = offset
            matrix[9] = offset
            matrix[14] = offset
            matrix
        }
        GRAY -> saturationMatrix(0f)
    }

    private companion object {
        val IDENTITY_RECIPE = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )

        // ITU-R BT.601 luminance weights used by android.graphics.ColorMatrix.
        const val LUMA_R = 0.213f
        const val LUMA_G = 0.715f
        const val LUMA_B = 0.072f

        const val VIBRANT_SATURATION = 1.5f
        const val SOFT_TONE_SATURATION = 0.82f
        const val SOFT_TONE_BRIGHTNESS_OFFSET = 6f
        const val SHARP_BLACK_CONTRAST = 1.5f

        /**
         * Replicates `android.graphics.ColorMatrix.setSaturation`: at `1` this is
         * the identity, at `0` every output channel becomes the luminance of the
         * input (grayscale) with whites preserved.
         */
        fun saturationMatrix(sat: Float): FloatArray {
            val inv = 1f - sat
            return floatArrayOf(
                inv * LUMA_R + sat, inv * LUMA_G, inv * LUMA_B, 0f, 0f,
                inv * LUMA_R, inv * LUMA_G + sat, inv * LUMA_B, 0f, 0f,
                inv * LUMA_R, inv * LUMA_G, inv * LUMA_B + sat, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        }

        /**
         * Scales the R/G/B output channels of a `4x5` matrix (the upshot of
         * AOSP `postScale(r, g, b, a)` on the rgb rows).
         */
        fun scaleOutput(matrix: FloatArray, r: Float, g: Float, b: Float) {
            for (column in 0..2) {
                matrix[column] *= r
                matrix[5 + column] *= g
                matrix[10 + column] *= b
            }
        }
    }
}