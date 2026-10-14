package com.patashot.myapp.presentation.camera

import androidx.compose.ui.graphics.toArgb
import com.patashot.myapp.ui.theme.PrimaryGreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The registry is the seam that lets templates be added without touching the
 * renderers, so these tests pin down the two things the rest of the app relies on:
 * a stable persisted id per template, and a total fallback for unknown ids.
 */
class CardTemplatesTest {

    @Test
    fun `default template uses the classic id`() {
        assertEquals("classic", CardTemplates.DEFAULT.id)
        assertEquals(CardTemplates.DEFAULT_ID, CardTemplates.DEFAULT.id)
    }

    @Test
    fun `registry contains the default template`() {
        assertSame(CardTemplates.DEFAULT, CardTemplates.ALL.first())
    }

    @Test
    fun `fromId resolves a known template`() {
        CardTemplates.ALL.forEach { template ->
            assertSame(template, CardTemplates.fromId(template.id))
        }
    }

    @Test
    fun `fromId falls back to default for an unknown id`() {
        assertSame(CardTemplates.DEFAULT, CardTemplates.fromId("does-not-exist"))
    }

    @Test
    fun `fromId falls back to default when nothing is persisted`() {
        assertSame(CardTemplates.DEFAULT, CardTemplates.fromId(null))
        assertSame(CardTemplates.DEFAULT, CardTemplates.fromId(""))
    }

    @Test
    fun `template ids are unique so one preference resolves one design`() {
        assertEquals(CardTemplates.ALL.size, CardTemplates.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun `metrics at full scale preserve the template's own values`() {
        val metrics = CardTemplates.DEFAULT.metrics()

        assertEquals(CardTemplates.DEFAULT.style.cityTextSizeSp, metrics.cityTextSizeSp, 0f)
        assertEquals(CardTemplates.DEFAULT.style.mapWidthDp, metrics.mapWidthDp, 0f)
        assertEquals(CardTemplates.DEFAULT.style.textColor, metrics.textColor)
    }

    @Test
    fun `metrics fold in the shared shell`() {
        val metrics = CardTemplates.DEFAULT.metrics()

        assertEquals(GeoTagCardShell.CARD_WIDTH_DP, metrics.cardWidthDp, 0f)
        assertEquals(GeoTagCardShell.CARD_CORNER_DP, metrics.cardCornerDp, 0f)
        assertEquals(GeoTagCardShell.CARD_BACKGROUND_COLOR, metrics.cardBackgroundColor)
    }

    @Test
    fun `scaled metrics scale every dimension but not colors`() {
        val full = CardTemplates.DEFAULT.metrics()
        val half = CardTemplates.DEFAULT.metrics(scale = 0.5f)

        assertEquals(full.cardWidthDp * 0.5f, half.cardWidthDp, 0.001f)
        assertEquals(full.mapWidthDp * 0.5f, half.mapWidthDp, 0.001f)
        assertEquals(full.cityTextSizeSp * 0.5f, half.cityTextSizeSp, 0.001f)
        assertEquals(full.logoCornerDp * 0.5f, half.logoCornerDp, 0.001f)
        assertEquals(full.cardBackgroundColor, half.cardBackgroundColor)
        assertEquals(full.textColor, half.textColor)
    }

    @Test
    fun `minimal template is registered under a stable id`() {
        assertSame(CardTemplates.MINIMAL, CardTemplates.fromId("minimal"))
        assertEquals(CardLayout.MINIMAL, CardTemplates.MINIMAL.layout)
    }

    @Test
    fun `every template resolves an accent colour that is opaque`() {
        CardTemplates.ALL.forEach { template ->
            val accent = template.style.accentColor
            assertTrue("${template.id} has a transparent accent", accent != 0L)
            assertEquals("${template.id} accent alpha", 0xFFL, accent ushr 24 and 0xFFL)
        }
    }

    @Test
    fun `minimal accent is the theme green the preview draws`() {
        val expected = PrimaryGreen.toArgb().toLong() and 0xFFFFFFFFL

        assertEquals(expected, CardTemplates.MINIMAL.style.accentColor)
        assertEquals(expected, CardTemplates.MINIMAL.metrics().accentColor)
    }

    @Test
    fun `metrics carry the accent colour through unscaled`() {
        val full = CardTemplates.MINIMAL.metrics()
        val half = CardTemplates.MINIMAL.metrics(scale = 0.5f)

        assertEquals(full.accentColor, half.accentColor)
    }

    @Test
    fun `qrcode template is registered under a stable id`() {
        assertSame(CardTemplates.QR_CODE, CardTemplates.fromId("qrcode"))
        assertEquals(CardLayout.QR_CODE, CardTemplates.QR_CODE.layout)
    }
}

