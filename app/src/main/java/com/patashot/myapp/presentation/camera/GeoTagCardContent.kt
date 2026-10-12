package com.patashot.myapp.presentation.camera

import com.patashot.myapp.BuildConfig
import com.patashot.myapp.domain.model.GeoCoordinate

/**
 * Capture-time snapshot of the location card contents. This pure data holder is
 * what gets rendered into a [android.graphics.Bitmap] and burned into photos and
 * videos, so burning it never depends on live UI state.
 *
 * [template] is part of the snapshot for the same reason: the card the user picked
 * before capturing is the card that gets burnt in, so switching templates
 * afterwards cannot retroactively change an in-flight capture.
 */
data class GeoTagCardContent(
    val cityString: String?,
    val fullAddress: String?,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: Long,
    val staticMapUrl: String?,
    val template: CardTemplate = CardTemplates.DEFAULT
)

/** Static map thumbnail size for the on-screen card, as opposed to the burnt one. */
const val PREVIEW_MAP_SIZE_PX = 200

/**
 * Builds the Google Static Maps URL shown in the card thumbnail.
 * Returns null when there are no coordinates or no API key configured.
 */
fun staticMapUrl(latitude: Double?, longitude: Double?, sizePx: Int = PREVIEW_MAP_SIZE_PX): String? {
    val apiKey = BuildConfig.MAPS_API_KEY
    if (latitude == null || longitude == null || apiKey.isBlank()) return null
    return "https://maps.googleapis.com/maps/api/staticmap" +
        "?center=$latitude,$longitude&zoom=15&size=${sizePx}x${sizePx}" +
        "&markers=color:red%7C$latitude,$longitude&key=$apiKey"
}

/**
 * Snapshots the current UI state into a burnable card. The preferred
 * coordinates come from the [coordinate] captured at capture time, falling back
 * to the most recent fix held on the UI state.
 */
fun CameraUiState.toGeoTagCardContent(
    coordinate: GeoCoordinate? = null,
    mapSizePx: Int = PREVIEW_MAP_SIZE_PX
): GeoTagCardContent {
    val lat = coordinate?.latitude ?: currentLocation?.latitude
    val lng = coordinate?.longitude ?: currentLocation?.longitude
    return GeoTagCardContent(
        cityString = cityString,
        fullAddress = fullAddress,
        latitude = lat,
        longitude = lng,
        timestamp = coordinate?.timestamp ?: System.currentTimeMillis(),
        staticMapUrl = staticMapUrl(lat, lng, mapSizePx),
        template = selectedTemplate
    )
}

/** Date format used by the card, in both the preview and the burnt overlay. */
internal const val CARD_DATE_PATTERN = "EEEE, dd/MM/yyyy - h:mm a"

/** Glue between the date and time halves of [CARD_DATE_PATTERN]. */
private const val CARD_DATE_TIME_GLUE = " - "

/** Rendered between the time and date halves by templates that show both. */
internal const val CARD_DATE_SEPARATOR = " | "

/**
 * The card copy, resolved from a single place so the live [GeoTagCard] and the
 * [CanvasGeoTagOverlayRenderer] cannot drift apart.
 *
 * The title degrades through progressively coarser location data so that a known
 * address is never reported as "still fetching".
 *
 * [date] is the raw formatted timestamp for layouts that render it as one line.
 * [dateLabel] and [timeLabel] are the same string already split into its two
 * halves, so templates that show time and date separately do not each re-parse it.
 */
internal class CardCopy(
    val city: String,
    val address: String,
    val coordinates: String,
    val date: String,
    val dateLabel: String,
    val timeLabel: String
)

internal fun GeoTagCardContent.toCardCopy(
    fetchingText: String,
    latLabel: String,
    lngLabel: String,
    dateFormatter: java.text.DateFormat
): CardCopy {
    val date = dateFormatter.format(java.util.Date(timestamp))
    return CardCopy(
        city = cityString ?: fullAddress ?: fetchingText,
        address = fullAddress ?: cityString ?: fetchingText,
        coordinates = "$latLabel: ${latitude?.let { "%.4f\u00B0".format(it) } ?: "--"} / " +
            "$lngLabel: ${longitude?.let { "%.4f\u00B0".format(it) } ?: "--"}",
        date = date,
        dateLabel = date.substringBefore(CARD_DATE_TIME_GLUE, date),
        timeLabel = date.substringAfter(CARD_DATE_TIME_GLUE, "")
    )
}