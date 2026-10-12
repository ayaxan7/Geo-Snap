package com.patashot.myapp.presentation.camera

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [CardCopy] is the single place the card's wording is resolved, and the date/time
 * split is the only part of it that has structure: templates that show the two
 * halves separately depend on the split happening here rather than in each
 * renderer.
 */
class CardCopyTest {

    private val formatter: SimpleDateFormat =
        SimpleDateFormat(CARD_DATE_PATTERN, Locale.ENGLISH)

    /** 15 Jan 2024, 09:30, built in the default zone so the assertion is stable. */
    private val timestamp: Long = Calendar.getInstance().apply {
        clear()
        set(2024, Calendar.JANUARY, 15, 9, 30, 0)
    }.timeInMillis

    private fun content(
        city: String? = "Bengaluru",
        address: String? = "MG Road, Bengaluru, Karnataka 560001",
        lat: Double? = 12.9716,
        lng: Double? = 77.5946
    ) = GeoTagCardContent(
        cityString = city,
        fullAddress = address,
        latitude = lat,
        longitude = lng,
        timestamp = timestamp,
        staticMapUrl = null
    )

    private fun copyFor(
        content: GeoTagCardContent,
        dateFormatter: SimpleDateFormat = formatter
    ) = content.toCardCopy(
        fetchingText = "Fetching location…",
        latLabel = "Lat",
        lngLabel = "Lng",
        dateFormatter = dateFormatter
    )

    @Test
    fun `date stays the full formatted timestamp for single-line layouts`() {
        val copy = copyFor(content())

        assertEquals("Monday, 15/01/2024 - 9:30 AM", copy.date)
    }

    @Test
    fun `date and time halves are split out of the same string`() {
        val copy = copyFor(content())

        assertEquals("Monday, 15/01/2024", copy.dateLabel)
        assertEquals("9:30 AM", copy.timeLabel)
    }

    @Test
    fun `time leads the date when joined by the separator`() {
        val copy = copyFor(content())

        assertEquals(
            "9:30 AM | Monday, 15/01/2024",
            listOf(copy.timeLabel, CARD_DATE_SEPARATOR, copy.dateLabel).joinToString("")
        )
    }

    @Test
    fun `a missing glue degrades to a date-only card instead of losing the date`() {
        val dateOnly = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale.ENGLISH)
        val copy = copyFor(content(), dateOnly)

        assertEquals("Monday, 15/01/2024", copy.dateLabel)
        assertEquals("", copy.timeLabel)
    }

    @Test
    fun `coordinates fall back to placeholders when unknown`() {
        val copy = copyFor(content(lat = null, lng = null))

        assertEquals("Lat: -- / Lng: --", copy.coordinates)
    }

    @Test
    fun `known coordinates are formatted to four decimal places`() {
        val copy = copyFor(content())

        assertEquals("Lat: 12.9716° / Lng: 77.5946°", copy.coordinates)
    }

    @Test
    fun `missing city falls back to the address, then to the fetching text`() {
        val fromAddress = copyFor(content(city = null))
        assertEquals("MG Road, Bengaluru, Karnataka 560001", fromAddress.city)

        val fromFetching = copyFor(content(city = null, address = null))
        assertEquals("Fetching location…", fromFetching.city)
        assertEquals("Fetching location…", fromFetching.address)
    }
}
