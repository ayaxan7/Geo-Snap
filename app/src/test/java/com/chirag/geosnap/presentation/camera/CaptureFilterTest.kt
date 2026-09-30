package com.chirag.geosnap.presentation.camera

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureFilterTest {

    private val identity = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    @Test
    fun `original is identity at every strength`() {
        for (strength in floatArrayOf(0f, 0.25f, 0.5f, 1f)) {
            assertArrayEquals(
                "strength=$strength",
                identity,
                CaptureFilter.ORIGINAL.recipe(strength),
                1e-6f
            )
        }
    }

    @Test
    fun `zero strength is identity for every filter`() {
        for (filter in CaptureFilter.entries) {
            assertArrayEquals(filter.name, identity, filter.recipe(0f), 1e-6f)
        }
    }

    @Test
    fun `negative strengths clamp to identity and overflow clamps to the boost`() {
        for (filter in CaptureFilter.entries) {
            assertArrayEquals(filter.name, identity, filter.recipe(-1f), 1e-6f)
            assertArrayEquals(
                filter.name,
                filter.recipe(2f),
                filter.recipe(3f),
                1e-6f
            )
        }
    }

    @Test
    fun `full strength recipes differ from identity`() {
        for (filter in listOf(
            CaptureFilter.VIBRANT,
            CaptureFilter.SOFT_TONE,
            CaptureFilter.SHARP_BLACK,
            CaptureFilter.GRAY
        )) {
            assertFalse("$filter should not equal identity", filter.recipe(1f).contentEquals(identity))
        }
    }

    @Test
    fun `gray mirrors the android luminance weights`() {
        // AOSP setSaturation(0): every output channel is the luminance of the
        // input (0.213 R / 0.715 G / 0.072 B) with whites preserved.
        val expectedRow = floatArrayOf(0.213f, 0.715f, 0.072f, 0f, 0f)
        val gray = CaptureFilter.GRAY.recipe(1f)
        assertArrayEquals("R row", expectedRow, gray.sliceArray(0..4), 1e-3f)
        assertArrayEquals("G row", expectedRow, gray.sliceArray(5..9), 1e-3f)
        assertArrayEquals("B row", expectedRow, gray.sliceArray(10..14), 1e-3f)
        assertEquals("alpha preserved", 1f, gray[18], 1e-6f)
    }

    @Test
    fun `vibrant boosts saturation beyond identity`() {
        val v = CaptureFilter.VIBRANT.recipe(1f)
        assertTrue("red should exceed 1", v[0] > 1f)
        assertTrue("green should exceed 1", v[6] > 1f)
        assertTrue("blue should exceed 1", v[12] > 1f)
        assertTrue("cross terms should be negative", v[1] < 0f)
    }

    @Test
    fun `soft tone desaturates and lifts brightness`() {
        val s = CaptureFilter.SOFT_TONE.recipe(1f)
        assertTrue("green should dip below 1", s[6] < 1f)
        assertTrue("cross luminance should be positive", s[1] > 0f)
        assertEquals(6f, s[4], 1e-3f)
        assertEquals(6f, s[9], 1e-3f)
        assertEquals(6f, s[14], 1e-3f)
    }

    @Test
    fun `sharp black is grayscale with contrast anchored at mid grey`() {
        val expectedRow = floatArrayOf(
            0.213f * 1.5f,
            0.715f * 1.5f,
            0.072f * 1.5f
        )
        val black = CaptureFilter.SHARP_BLACK.recipe(1f)
        assertArrayEquals("R row", expectedRow, black.sliceArray(0..2), 1e-3f)
        assertArrayEquals("G row", expectedRow, black.sliceArray(5..7), 1e-3f)
        assertArrayEquals("B row", expectedRow, black.sliceArray(10..12), 1e-3f)
        val expectedOffset = 128f * (1f - 1.5f)
        assertEquals(expectedOffset, black[4], 1e-3f)
        assertEquals(expectedOffset, black[9], 1e-3f)
        assertEquals(expectedOffset, black[14], 1e-3f)
    }

    @Test
    fun `mid strength is a linear blend between identity and the full recipe`() {
        val full = CaptureFilter.SHARP_BLACK.recipe(1f)
        for (strength in floatArrayOf(0.25f, 0.5f, 0.75f)) {
            val blended = CaptureFilter.SHARP_BLACK.recipe(strength)
            for (i in 0 until 20) {
                assertEquals(
                    "index=$i strength=$strength",
                    identity[i] + (full[i] - identity[i]) * strength,
                    blended[i],
                    1e-3f
                )
            }
        }
        // At 50% the sharp-black offset is exactly half of -64, i.e. mid grey.
        assertEquals(-32f, CaptureFilter.SHARP_BLACK.recipe(0.5f)[4], 1e-3f)
    }

    @Test
    fun `boost midpoint is the lerp of normal and intensified recipes`() {
        val normal = CaptureFilter.SOFT_TONE.recipe(1f)
        val boosted = CaptureFilter.SOFT_TONE.recipe(2f)
        val midpoint = CaptureFilter.SOFT_TONE.recipe(1.5f)
        for (i in 0 until 20) {
            assertEquals(
                "index=$i",
                (normal[i] + boosted[i]) / 2f,
                midpoint[i],
                1e-3f
            )
        }
        assertEquals(9f, midpoint[4], 1e-3f) // offset 6 -> 12
    }

    @Test
    fun `boosted sharp black and gray stay grayscale`() {
        for (filter in listOf(CaptureFilter.SHARP_BLACK, CaptureFilter.GRAY)) {
            val boosted = filter.recipe(2f)
            assertArrayEquals("R row", boosted.sliceArray(0..2), boosted.sliceArray(5..7), 1e-4f)
            assertArrayEquals("G row", boosted.sliceArray(5..7), boosted.sliceArray(10..12), 1e-4f)
        }
        assertEquals(128f * (1f - 1.25f), CaptureFilter.GRAY.recipe(2f)[4], 1e-3f)
    }

    @Test
    fun `boosted vibrant and soft tone intensify further`() {
        val vibrant = CaptureFilter.VIBRANT.recipe(2f)
        assertTrue("green diag should beat normal", vibrant[6] > CaptureFilter.VIBRANT.recipe(1f)[6])
        assertEquals(12f, CaptureFilter.SOFT_TONE.recipe(2f)[4], 1e-3f)
    }
}