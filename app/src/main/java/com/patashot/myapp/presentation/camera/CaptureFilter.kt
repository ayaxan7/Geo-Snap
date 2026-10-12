package com.patashot.myapp.presentation.camera

/**
 * The five photo/video filters exposed on the preview screen's edit panel.
 *
 * Each filter has a "normal" recap (its designed full look) and a boosted
 * "intensified" recipe. The bipolar strength slider keeps `0` at the *center*:
 *
 *  - `strength = 0`  the untouched capture (identity matrix)
 *  - `strength = 1`  the filter applied at its normal design strength
 *  - `strength = 2`  the filter boosted past normal
 *
 * Values in `0..1` interpolate identity → normal; values in `1..2` interpolate
 * normal → intensified. Interpolating two well-formed recipes (rather than
 * extrapolating past one) is what keeps e.g. the grayscale filters from
 * inverting or leaving grayscale space on the boost side.
 *
 * The same `4x5` `FloatArray` recipe powers the Compose preview
 * (`ColorFilter.colorMatrix`), the video pipeline (Media3 [`RgbMatrix`], which
 * converts the offset column to `1/255` space) and the burn-in pass
 * (`ColorMatrixColorFilter`), keeping image and video output visually
 * identical.
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
     * The `4x5` color matrix for a given [strength] in `0..2`. [ORIGINAL] (or a
     * non-positive strength) returns the identity matrix; `1` is the filter's
     * normal look.
     */
    fun recipe(strength: Float): FloatArray {
        if (this == ORIGINAL) return IDENTITY_RECIPE.copyOf()
        val s = strength.coerceIn(0f, MAX_STRENGTH)
        if (s <= 0f) return IDENTITY_RECIPE.copyOf()
        val normal = normalRecipe()
        if (s <= 1f) return lerp(IDENTITY_RECIPE, normal, s)
        return lerp(normal, intensifiedRecipe(), s - 1f)
    }

    private fun normalRecipe(): FloatArray = when (this) {
        ORIGINAL -> IDENTITY_RECIPE.copyOf()
        VIBRANT -> saturationMatrix(VIBRANT_SATURATION)
        SOFT_TONE -> softTone(SOFT_TONE_SATURATION, SOFT_TONE_BRIGHTNESS_OFFSET)
        SHARP_BLACK -> grayscale(SHARP_BLACK_CONTRAST)
        GRAY -> grayscale(1f)
    }

    private fun intensifiedRecipe(): FloatArray = when (this) {
        ORIGINAL -> IDENTITY_RECIPE.copyOf()
        VIBRANT -> saturationMatrix(MAX_SATURATION)
        SOFT_TONE -> softTone(SOFT_TONE_SATURATION_MIN, SOFT_TONE_BRIGHTNESS_OFFSET * 2f)
        SHARP_BLACK -> grayscale(MAX_SHARP_BLACK_CONTRAST)
        GRAY -> grayscale(MAX_GRAY_CONTRAST)
    }

    private companion object {
        const val MAX_STRENGTH = 2f

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

        const val MAX_SATURATION = 2f
        const val SOFT_TONE_SATURATION_MIN = 0.65f
        const val MAX_SHARP_BLACK_CONTRAST = 1.75f
        const val MAX_GRAY_CONTRAST = 1.25f

        fun lerp(a: FloatArray, b: FloatArray, t: Float): FloatArray =
            FloatArray(20) { i -> a[i] + (b[i] - a[i]) * t }

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
         * A grayscale recipe (all rows = the luminance weights) optionally scaled
         * by a [contrast] factor anchored at mid-grey (0..255 space).
         */
        fun grayscale(contrast: Float): FloatArray {
            val matrix = saturationMatrix(0f)
            scaleOutput(matrix, contrast, contrast, contrast)
            val offset = 128f * (1f - contrast)
            matrix[4] = offset
            matrix[9] = offset
            matrix[14] = offset
            return matrix
        }

        /** A slightly desaturated, gently brightened recipe. */
        fun softTone(sat: Float, brightnessOffset: Float): FloatArray {
            val matrix = saturationMatrix(sat)
            matrix[4] = brightnessOffset
            matrix[9] = brightnessOffset
            matrix[14] = brightnessOffset
            return matrix
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