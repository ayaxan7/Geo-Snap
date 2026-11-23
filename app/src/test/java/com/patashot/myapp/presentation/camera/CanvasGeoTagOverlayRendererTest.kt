package com.patashot.myapp.presentation.camera

import android.graphics.Bitmap
import com.patashot.myapp.data.maps.StaticMapFetcher
import com.patashot.myapp.testutils.FakeMapsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * The burnt-in card is produced by a plain [android.graphics.Canvas], so these
 * tests run under Robolectric's native graphics mode and read real pixels back
 * out of the bitmap. That is the only layer that can prove the burned card is
 * actually drawn rather than merely non-null: the preview composable and the
 * burner have never shared an implementation, which is exactly how the MINIMAL
 * layout shipped as a 1x1 placeholder while the preview still looked correct.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CanvasGeoTagOverlayRendererTest {

    /** RENDER_DENSITY (3) x SUPERSAMPLE (2), as used by the renderer. */
    private val renderDensity = 3f * 2f

    private val renderer = CanvasGeoTagOverlayRenderer(
        context = RuntimeEnvironment.getApplication(),
        staticMapFetcher = StaticMapFetcher(FakeMapsApi()),
        renderDispatcher = Dispatchers.Unconfined
    )

    private fun content(
        template: CardTemplate,
        city: String? = "Bengaluru",
        address: String? = "MG Road, Bengaluru, Karnataka 560001",
        lat: Double? = 12.9716,
        lng: Double? = 77.5946
    ) = GeoTagCardContent(
        cityString = city,
        fullAddress = address,
        latitude = lat,
        longitude = lng,
        timestamp = 1705297800000L,
        staticMapUrl = null,
        template = template
    )

    private fun render(content: GeoTagCardContent): Bitmap? = runBlocking {
        renderer.render(content)
    }

    private fun renderOrFail(content: GeoTagCardContent): Bitmap {
        val bitmap = render(content)
        assertNotNull("${content.template.id} did not render", bitmap)
        return bitmap!!
    }

    private fun card(template: CardTemplate): Bitmap =
        renderOrFail(content(template = template))

    /** Keeps sampling clear of the opaque white border drawn at the card edge. */
    private fun Bitmap.interiorInset(): Int =
        (2 * GeoTagCardShell.CARD_BORDER_DP * renderDensity).toInt() + 24

    private fun Bitmap.interiorPixels(): IntArray {
        val inset = interiorInset()
        val innerWidth = width - inset * 2
        val innerHeight = height - inset * 2
        return IntArray(innerWidth * innerHeight).also {
            getPixels(it, 0, innerWidth, inset, inset, innerWidth, innerHeight)
        }
    }

    private fun isAccentLike(color: Int): Boolean {
        val alpha = color ushr 24 and 0xFF
        val red = color shr 16 and 0xFF
        val green = color shr 8 and 0xFF
        val blue = color and 0xFF
        return alpha > 100 && green > red + 60 && green > blue + 60
    }

    /** The bounding box of everything actually drawn inside the card. */
    private data class InkBounds(
        val left: Int,
        val right: Int,
        val top: Int,
        val bottom: Int
    ) {
        val centreX: Float get() = (left + right) / 2f
        val height: Int get() = bottom - top + 1
    }

    /**
     * Measures the drawn content, sampling a region that is provably free of the
     * card's opaque white border: horizontally that is inside the shell's padding
     * (past the stroke at the very edge), and vertically just inside the stroke,
     * which is where the rounded corners have already curved away from the sides.
     */
    private fun Bitmap.inkBounds(metrics: GeoTagCardMetrics): InkBounds {
        val x0 = (metrics.paddingHorizontalDp * renderDensity).toInt()
        val x1 = width - x0
        val stroke = (GeoTagCardShell.CARD_BORDER_DP * renderDensity).toInt()
        val row = IntArray(x1 - x0)
        var left = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var top = Int.MAX_VALUE
        var bottom = Int.MIN_VALUE
        for (y in stroke + 2 until height - stroke - 2) {
            getPixels(row, 0, row.size, x0, y, row.size, 1)
            row.forEachIndexed { i, color ->
                if (color ushr 24 and 0xFF > INK_ALPHA_THRESHOLD) {
                    val x = x0 + i
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        assertTrue("no drawn content found", left <= right)
        return InkBounds(left, right, top, bottom)
    }

    private fun isTextLike(color: Int): Boolean {
        val alpha = color ushr 24 and 0xFF
        val red = color shr 16 and 0xFF
        val green = color shr 8 and 0xFF
        val blue = color and 0xFF
        return alpha > 150 && red > 180 && green > 180 && blue > 180
    }

    /** Horizontal extent of accent-coloured pixels, or null when there are none. */
    private fun Bitmap.accentColumnSpan(): IntRange? {
        val inset = interiorInset()
        val innerWidth = width - inset * 2
        val innerHeight = height - inset * 2
        val column = IntArray(innerHeight)
        var minX = -1
        var maxX = -1
        for (x in 0 until innerWidth) {
            getPixels(column, 0, 1, inset + x, inset, 1, innerHeight)
            if (column.any { isAccentLike(it) }) {
                if (minX < 0) minX = x
                maxX = x
            }
        }
        return if (minX < 0) null else minX..maxX
    }

    @Test
    fun `minimal burns in a real card instead of a placeholder pixel`() {
        val bitmap = card(CardTemplates.MINIMAL)

        assertTrue(
            "expected a rendered card, got ${bitmap.width}x${bitmap.height}",
            bitmap.width > 1 && bitmap.height > 1
        )
    }

    @Test
    fun `minimal card is the full shell width`() {
        val bitmap = card(CardTemplates.MINIMAL)

        assertEquals((GeoTagCardShell.CARD_WIDTH_DP * renderDensity).toInt(), bitmap.width)
    }

    @Test
    fun `minimal card is shorter than the map layout because it has no thumbnail`() {
        val minimal = card(CardTemplates.MINIMAL)
        val mapLeft = card(CardTemplates.DEFAULT)

        assertTrue(
            "minimal ${minimal.height} should be shorter than map-left ${mapLeft.height}",
            minimal.height < mapLeft.height
        )
        assertEquals(minimal.width, mapLeft.width)
    }

    @Test
    fun `minimal content is horizontally centred in the card`() {
        val bitmap = card(CardTemplates.MINIMAL)
        val ink = bitmap.inkBounds(CardTemplates.MINIMAL.metrics())
        val cardCentre = bitmap.width / 2f

        assertTrue(
            "content centred at ${ink.centreX}, card centre at $cardCentre",
            abs(ink.centreX - cardCentre) <= CENTRE_TOLERANCE_PX
        )
    }

    @Test
    fun `minimal reserves the shell and details padding above and below the text`() {
        val metrics = CardTemplates.MINIMAL.metrics()
        val bitmap = card(CardTemplates.MINIMAL)
        val ink = bitmap.inkBounds(metrics)
        val expectedGap =
            (metrics.paddingVerticalDp + metrics.detailsPaddingVerticalDp) * renderDensity

        assertTrue(
            "expected at least ${expectedGap.toInt() - ASCENT_TOLERANCE_PX}px above the text, got ${ink.top}px",
            ink.top >= expectedGap - ASCENT_TOLERANCE_PX
        )
        assertTrue(
            "card is ${bitmap.height}px, too short for ${expectedGap.toInt()}px padding around " +
                "${ink.height}px of content",
            bitmap.height >= 2 * expectedGap + ink.height
        )
    }

    @Test
    fun `minimal draws the accent separator between time and date`() {
        assertTrue(
            "expected accent-coloured pixels, found none",
            card(CardTemplates.MINIMAL).interiorPixels().any { isAccentLike(it) }
        )
    }

    @Test
    fun `accent separator is a narrow bar rather than a filled region`() {
        val bitmap = card(CardTemplates.MINIMAL)
        val span = requireNotNull(bitmap.accentColumnSpan()) {
            "no accent pixels found"
        }
        val innerWidth = bitmap.width - bitmap.interiorInset() * 2

        assertTrue(
            "accent spanned ${span.last - span.first}px of a ${innerWidth}px content area",
            (span.last - span.first) < innerWidth / 8
        )
    }

    @Test
    fun `accent separator sits between the two text runs rather than at either edge`() {
        val bitmap = card(CardTemplates.MINIMAL)
        val span = requireNotNull(bitmap.accentColumnSpan()) { "no accent pixels found" }
        val innerWidth = bitmap.width - bitmap.interiorInset() * 2
        val centre = (span.first + span.last) / 2f

        assertTrue(
            "separator at ${centre / innerWidth} of the content width",
            centre > innerWidth * 0.2f && centre < innerWidth * 0.8f
        )
    }

    @Test
    fun `minimal draws its text`() {
        assertTrue(
            "expected drawn text, found none",
            card(CardTemplates.MINIMAL).interiorPixels().any { isTextLike(it) }
        )
    }

    @Test
    fun `minimal still renders when the location is unknown`() {
        val bitmap = renderOrFail(
            content(
                template = CardTemplates.MINIMAL,
                city = null,
                address = null,
                lat = null,
                lng = null
            )
        )

        assertTrue(bitmap.width > 1 && bitmap.height > 1)
    }

    @Test
    fun `map layout is at least as tall as its thumbnail`() {
        val bitmap = card(CardTemplates.DEFAULT)
        val metrics = CardTemplates.DEFAULT.metrics()
        val minimumHeight =
            ((metrics.mapHeightDp + 1.8f * metrics.paddingVerticalDp) * renderDensity).toInt()

        assertEquals((GeoTagCardShell.CARD_WIDTH_DP * renderDensity).toInt(), bitmap.width)
        assertTrue(
            "expected at least ${minimumHeight}px, got ${bitmap.height}px",
            bitmap.height >= minimumHeight
        )
    }

    @Test
    fun `every registered template burns in a visible card`() {
        CardTemplates.ALL.forEach { template ->
            val bitmap = card(template)
            assertTrue(
                "${template.id} rendered ${bitmap.width}x${bitmap.height}",
                bitmap.width > 1 && bitmap.height > 1
            )
        }
    }

    @Test
    fun `qrcode layout renders successfully with location`() {
        val bitmap = renderOrFail(
            content(
                template = CardTemplates.QR_CODE,
                city = "Vijayawada, Andhra Pradesh",
                address = "Flat No. 302, Sai Heights, Eluru",
                lat = 16.5062,
                lng = 80.6480
            )
        )

        assertTrue(bitmap.width > 1 && bitmap.height > 1)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("QR code card should contain black pixels", pixels.any { it == android.graphics.Color.BLACK })
        assertTrue("QR code card should contain white pixels", pixels.any { it == android.graphics.Color.WHITE })
    }

    @Test
    fun `accent bar layout renders successfully with location`() {
        val bitmap = renderOrFail(
            content(
                template = CardTemplates.ACCENT_BAR,
                city = "Vijayawada, Andhra Pradesh",
                address = "Flat No. 302, Sai Heights, Eluru",
                lat = 16.5062,
                lng = 80.6480
            )
        )

        assertTrue(bitmap.width > 1 && bitmap.height > 1)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("Accent bar card should contain white pixels", pixels.any { it == android.graphics.Color.WHITE })
    }

    @Test
    fun `accent bar layout renders when location is unknown`() {
        val bitmap = renderOrFail(
            content(
                template = CardTemplates.ACCENT_BAR,
                city = null,
                address = null,
                lat = null,
                lng = null
            )
        )

        assertTrue(bitmap.width > 1 && bitmap.height > 1)
    }

    private companion object {
        /** Anything drawn over the card background (alpha 0xB3) is content. */
        const val INK_ALPHA_THRESHOLD = 185

        /**
         * Centring tolerance: text rasterisation is not perfectly symmetric, so
         * glyph ink can sit a few px off centre. Measured drift is under 5px,
         * while the bug this guards against is a full horizontal padding.
         */
        const val CENTRE_TOLERANCE_PX = 12f

        /** Ink starts below the layout top by roughly the glyph ascent. */
        const val ASCENT_TOLERANCE_PX = 12f
    }
}
