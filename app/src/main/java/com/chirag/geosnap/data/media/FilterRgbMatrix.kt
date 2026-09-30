package com.chirag.geosnap.data.media

import android.graphics.ColorMatrix
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbMatrix

/**
 * A Media3 [RgbMatrix] that reuses the same `4x5` color recipe as the image
 * pipeline, so a single filter drives the video preview ([`ExoPlayer`]
 * `setVideoEffects`) and the disc burn ([`Transformer`] `Effects`).
 *
 * The GLSL multiply is `gl_FragColor = uRgbMatrix * vec4(inputColor.rgb, 1)`,
 * i.e. a column-major 4x4 whose fourth column is a bias applied to the
 * `0..1`-encoded color. The recipe keeps its offsets in `0..255` space, so that
 * column is divided by `255` here. `getMatrix` runs once per frame, which lets
 * the preview slider scrub through strengths without rebuilding the pipeline.
 */
@OptIn(UnstableApi::class)
class FilterRgbMatrix(initialRecipe: FloatArray = IDENTITY_RECIPE) : RgbMatrix {

    @Volatile
    private var matrixSnapshot: FloatArray = toGlMatrix(initialRecipe)

    /** Swaps in a new recipe; the next rendered frame picks it up. */
    fun set(recipe: FloatArray) {
        matrixSnapshot = toGlMatrix(recipe)
    }

    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray =
        matrixSnapshot.copyOf()

    private companion object {
        val IDENTITY_RECIPE = ColorMatrix().array

        fun toGlMatrix(recipe: FloatArray): FloatArray {
            val a = recipe
            val m = FloatArray(16)
            m[0] = a[0]
            m[4] = a[1]
            m[8] = a[2]
            m[12] = a[4] / 255f
            m[1] = a[5]
            m[5] = a[6]
            m[9] = a[7]
            m[13] = a[9] / 255f
            m[2] = a[10]
            m[6] = a[11]
            m[10] = a[12]
            m[14] = a[14] / 255f
            m[3] = 0f
            m[7] = 0f
            m[11] = 0f
            m[15] = 1f
            return m
        }
    }
}