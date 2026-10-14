package com.patashot.myapp.presentation.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QrCodeGeneratorTest {

    @Test
    fun `generates valid bitmap for location url`() {
        val bitmap = QrCodeGenerator.generateQrBitmap("https://maps.google.com/?q=16.5062,80.6480", 200)

        assertNotNull(bitmap)
        assertEquals(200, bitmap!!.width)
        assertEquals(200, bitmap.height)
    }

    @Test
    fun `returns null for blank content or non-positive size`() {
        assertNull(QrCodeGenerator.generateQrBitmap("", 200))
        assertNull(QrCodeGenerator.generateQrBitmap("   ", 200))
        assertNull(QrCodeGenerator.generateQrBitmap("https://maps.google.com", 0))
        assertNull(QrCodeGenerator.generateQrBitmap("https://maps.google.com", -10))
    }

    @Test
    fun `contains both black and white pixels`() {
        val bitmap = QrCodeGenerator.generateQrBitmap("https://maps.google.com/?q=16.5062,80.6480", 100)!!
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val hasBlack = pixels.any { it == android.graphics.Color.BLACK }
        val hasWhite = pixels.any { it == android.graphics.Color.WHITE }

        assertTrue("QR code should contain black pixels", hasBlack)
        assertTrue("QR code should contain white pixels", hasWhite)
    }
}
