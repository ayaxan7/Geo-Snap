package com.chirag.geosnap.presentation.camera

import android.graphics.Bitmap
import com.chirag.geosnap.data.maps.StaticMapFetcher
import com.chirag.geosnap.testutils.FakeMapsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
            ((metrics.mapHeightDp + 2 * metrics.paddingVerticalDp) * renderDensity).toInt()

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
}
