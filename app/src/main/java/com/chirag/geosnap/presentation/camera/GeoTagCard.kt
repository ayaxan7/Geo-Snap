package com.chirag.geosnap.presentation.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.chirag.geosnap.BuildConfig
import com.chirag.geosnap.R
import com.chirag.geosnap.core.LocaleHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The location card composable shared by both the live camera overlay and the
 * off-screen overlay renderer. The same composable is used in both places so the
 * card burned into photos/videos matches what the user sees on screen.
 *
 * When [mapBitmap] is provided it is drawn as the map thumbnail (burned media);
 * otherwise the static-map URL from [GeoTagCardContent.staticMapUrl] is loaded
 * asynchronously (live overlay).
 */
@Composable
fun GeoTagCard(
    content: GeoTagCardContent,
    modifier: Modifier = Modifier,
    mapBitmap: Bitmap? = null
) {
    Log.d(
        "LocationCard",
        "API Key: ${BuildConfig.MAPS_API_KEY}, Lat: ${content.latitude}, Lng: ${content.longitude}," +
                " city: ${content.cityString}, Address: ${content.fullAddress}"
    )

    val context = LocalContext.current
    val englishContext = remember(context) {
        LocaleHelper.englishContext(context)
    }

    val fetchingText =
        englishContext.getString(R.string.geotag_fetching_location)
    val latLabel =
        englishContext.getString(R.string.geotag_latitude)
    val lngLabel =
        englishContext.getString(R.string.geotag_longitude)
    val dateLabel =
        englishContext.getString(R.string.geotag_date)
    val staticMapLabel =
        englishContext.getString(R.string.geotag_static_map)
    val mapLabel =
        englishContext.getString(R.string.geotag_map)
    val logoLabel =
        englishContext.getString(R.string.geotag_logo)

    val cityText = content.cityString ?: fetchingText
    val fullAddressText =
        content.fullAddress ?: content.cityString ?: fetchingText

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Color(GeoTagCardSpec.CARD_BACKGROUND_COLOR),
                RoundedCornerShape(GeoTagCardSpec.CARD_CORNER_DP.dp)
            )
            .border(
                width = GeoTagCardSpec.CARD_BORDER_DP.dp,
                color = Color(GeoTagCardSpec.CARD_BORDER_COLOR),
                shape = RoundedCornerShape(GeoTagCardSpec.CARD_CORNER_DP.dp)
            )
            .padding(
                horizontal = GeoTagCardSpec.PADDING_HORIZONTAL_DP.dp,
                vertical = GeoTagCardSpec.PADDING_VERTICAL_DP.dp
            ),
        // Content hugs the card: it is bottom-aligned on one baseline (map, date
        // row and logo share the same bottom line) instead of being padded out to
        // a fixed height.
        verticalAlignment = Alignment.Bottom
    ) {


        MapThumbnail(
            image = mapBitmap ?: content.staticMapUrl,
            staticMapLabel = staticMapLabel,
            mapLabel = mapLabel
        )

        Spacer(modifier = Modifier.width(GeoTagCardSpec.SPACER_MAP_DETAILS_DP.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = GeoTagCardSpec.DETAILS_PADDING_VERTICAL_DP.dp),
            verticalArrangement = Arrangement.spacedBy(GeoTagCardSpec.DETAILS_LINE_GAP_DP.dp)
        ) {

            // City
            Text(
                text = cityText,
                color = Color(GeoTagCardSpec.TEXT_COLOR),
                fontWeight = FontWeight.Bold,
                fontSize = GeoTagCardSpec.CITY_TEXT_SIZE_SP.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Address
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    painter = painterResource(
                        android.R.drawable.ic_menu_mylocation
                    ),
                    contentDescription = null,
                    tint = Color(GeoTagCardSpec.ICON_RED),
                    modifier = Modifier.size(GeoTagCardSpec.ICON_SIZE_DP.dp)
                )

                Spacer(modifier = Modifier.width(GeoTagCardSpec.ICON_TEXT_GAP_DP.dp))

                Text(
                    text = fullAddressText,
                    color = Color(GeoTagCardSpec.TEXT_SECONDARY_COLOR),
                    fontSize = GeoTagCardSpec.ADDRESS_TEXT_SIZE_SP.sp,
                    lineHeight = GeoTagCardSpec.ADDRESS_LINE_HEIGHT_SP.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Coordinates
            Text(
                text = buildString {
                    append("$latLabel: ")
                    append(
                        content.latitude?.let {
                            "%.4f°".format(it)
                        } ?: "--"
                    )

                    append(" / ")

                    append("$lngLabel: ")
                    append(
                        content.longitude?.let {
                            "%.4f°".format(it)
                        } ?: "--"
                    )
                },
                color = Color(GeoTagCardSpec.TEXT_COLOR),
                fontSize = GeoTagCardSpec.COORDINATES_TEXT_SIZE_SP.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Date
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(
                        android.R.drawable.ic_menu_today
                    ),
                    contentDescription = dateLabel,
                    tint = Color(GeoTagCardSpec.TEXT_COLOR),
                    modifier = Modifier.size(GeoTagCardSpec.ICON_SIZE_DP.dp)
                )

                Spacer(modifier = Modifier.width(GeoTagCardSpec.ICON_TEXT_GAP_DP.dp))

                val dateFormat = SimpleDateFormat(
                    "EEEE, dd/MM/yyyy - h:mm a",
                    Locale.ENGLISH
                )

                val currentDateTime =
                    dateFormat.format(Date(content.timestamp))

                Text(
                    text = currentDateTime,
                    color = Color(GeoTagCardSpec.TEXT_COLOR),
                    fontSize = GeoTagCardSpec.DATE_TEXT_SIZE_SP.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(GeoTagCardSpec.SPACER_DETAILS_LOGO_DP.dp))

        Box(
            modifier = Modifier
                .size(GeoTagCardSpec.LOGO_SIZE_DP.dp)
                .clip(RoundedCornerShape(GeoTagCardSpec.LOGO_CORNER_DP.dp))
                .background(Color(GeoTagCardSpec.LOGO_GREEN)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.geo_snap_icon),
                contentDescription = logoLabel,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }
    }
}

@Composable
private fun MapThumbnail(
    image: Any?,
    staticMapLabel: String,
    mapLabel: String
) {
    Box(
        modifier = Modifier
            .width(GeoTagCardSpec.MAP_WIDTH_DP.dp)
            .height(GeoTagCardSpec.MAP_HEIGHT_DP.dp)
            .clip(
                RoundedCornerShape(
                    GeoTagCardSpec.MAP_CORNER_DP.dp
                )
            )
            .background(
                Color(GeoTagCardSpec.MAP_BACKGROUND_COLOR)
            )
    ) {
        when (image) {
            is Bitmap -> {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = staticMapLabel,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            is String -> {
                AsyncImage(
                    model = image,
                    contentDescription = staticMapLabel,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            else -> {
                Text(
                    text = mapLabel,
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }
    }
}