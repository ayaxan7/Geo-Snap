package com.chirag.geosnap.data.maps

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeocodeResponseTest {

    private val adapter = Moshi.Builder().build().adapter(GeocodeResponse::class.java)

    @Test
    fun `parses formatted address and ignores extra fields`() {
        val json = """
            {
              "results": [
                {
                  "formatted_address": "1600 Amphitheatre Pkwy, Mountain View, CA",
                  "place_id": "abc",
                  "geometry": { "location": { "lat": 37.4, "lng": -122.0 } }
                }
              ],
              "status": "OK"
            }
        """.trimIndent()

        val response = adapter.fromJson(json)

        assertEquals("OK", response?.status)
        assertEquals(
            "1600 Amphitheatre Pkwy, Mountain View, CA",
            response?.results?.first()?.formattedAddress
        )
    }

    @Test
    fun `empty results keep a null address`() {
        val response = adapter.fromJson("""{"results": [], "status": "ZERO_RESULTS"}""")

        assertEquals("ZERO_RESULTS", response?.status)
        assertNull(response?.results?.firstOrNull()?.formattedAddress)
    }
}