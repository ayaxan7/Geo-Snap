package com.patashot.myapp.presentation.camera

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.patashot.myapp.R
import com.patashot.myapp.core.LocaleHelper
import java.text.SimpleDateFormat

/**
 * The location card composable shown over the capture preview.
 *
 * The card's appearance comes entirely from [GeoTagCardContent.template]: the shell
 * is shared by every template via [GeoTagCardShell], the arrangement follows the
 * template's [CardLayout] and its metrics come from that template's
 * [GeoTagCardStyle]. The [CanvasGeoTagOverlayRenderer] follows the same structure so
 * the card burnt into media matches what the user reviewed.
 *
 * When [mapBitmap] is provided it is drawn as the map thumbnail (burned media);
 * otherwise the static-map URL from [GeoTagCardContent.staticMapUrl] is loaded
 * asynchronously (live overlay).
 *
 * [scale] renders a genuine miniature of the card — every metric is scaled together
 * so proportions match the real thing — which is how the template picker previews
 * each design with the same composable the user sees in the camera.
 */
@Composable
fun GeoTagCard(
    content: GeoTagCardContent,
    modifier: Modifier = Modifier,
    mapBitmap: Bitmap? = null,
    onEditClick: (() -> Unit)? = null,
    scale: Float = 1f
) {
    val template = content.template
    val metrics = remember(template, scale) { template.metrics(scale) }

    val context = LocalContext.current
    val englishContext = remember(context) { LocaleHelper.englishContext(context) }
    val dateFormat = remember { SimpleDateFormat(CARD_DATE_PATTERN, java.util.Locale.ENGLISH) }

    val labels = remember(englishContext, content) {
        CardLabels(
            map = englishContext.getString(R.string.geotag_map),
            staticMap = englishContext.getString(R.string.geotag_static_map),
            logo = englishContext.getString(R.string.geotag_logo),
            copy = content.toCardCopy(
                fetchingText = englishContext.getString(R.string.geotag_fetching_location),
                latLabel = englishContext.getString(R.string.geotag_latitude),
                lngLabel = englishContext.getString(R.string.geotag_longitude),
                dateFormatter = dateFormat
            )
        )
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                // The real card fills the width it is given, matching how it has
                // always been framed on the camera. A scaled preview instead takes
                // the shell's own width so it keeps the card's true proportions.
                .then(
                    if (scale == 1f) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.width(metrics.cardWidthDp.dp)
                    }
                )
                .background(
                    Color(metrics.cardBackgroundColor),
                    RoundedCornerShape(metrics.cardCornerDp.dp)
                )
                .border(
                    width = metrics.cardBorderDp.dp,
                    color = Color(metrics.cardBorderColor),
                    shape = RoundedCornerShape(metrics.cardCornerDp.dp)
                )
                .padding(
                    horizontal = metrics.paddingHorizontalDp.dp,
                    vertical = metrics.paddingVerticalDp.dp
                ),
            // Content hugs the card: it is bottom-aligned on one baseline (map, date
            // row and logo share the same bottom line) instead of being padded out to
            // a fixed height.
            verticalAlignment = Alignment.Bottom
        ) {
            when (template.layout) {
                CardLayout.MAP_LEFT -> MapLeftContent(
                    copy = labels.copy,
                    metrics = metrics,
                    labels = labels,
                    mapImage = mapBitmap ?: content.staticMapUrl
                )
                CardLayout.MINIMAL -> MinimalContent(
                    copy = labels.copy,
                    metrics = metrics
                )
            }
        }

        if (onEditClick != null) {
            IconButton(
                onClick = onEditClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit_pencil),
                    contentDescription = stringResource(R.string.edit_location_title),
                    tint = Color.White
                )
            }
        }
    }
}

/** Non-visual strings resolved once for a card, kept out of the layout branches. */
private class CardLabels(
    val map: String,
    val staticMap: String,
    val logo: String,
    val copy: CardCopy
)

/**
 * Map thumbnail on the left, text block in the middle, logo on the right.
 *
 * The three columns are bottom-aligned onto a single baseline so the map, the date
 * row and the logo all end on the same line just above the card's bottom edge.
 */
@Composable
private fun RowScope.MapLeftContent(
    copy: CardCopy,
    metrics: GeoTagCardMetrics,
    labels: CardLabels,
    mapImage: Any?
) {
    MapThumbnail(
        image = mapImage,
        metrics = metrics,
        staticMapLabel = labels.staticMap,
        mapLabel = labels.map
    )

    Spacer(modifier = Modifier.width(metrics.spacerMapDetailsDp.dp))

    Column(
        modifier = Modifier
            .weight(1f)
            .padding(vertical = metrics.detailsPaddingVerticalDp.dp),
        verticalArrangement = Arrangement.spacedBy(metrics.detailsLineGapDp.dp)
    ) {
        Text(
            text = copy.city,
            color = Color(metrics.textColor),
            fontWeight = FontWeight.Bold,
            fontSize = metrics.cityTextSizeSp.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(verticalAlignment = Alignment.Top) {
            Image(
                painter = painterResource(R.drawable.ic_map),
                contentDescription = labels.map,
                modifier = Modifier.size(metrics.iconSizeDp.dp)
            )
            Spacer(modifier = Modifier.width(metrics.iconTextGapDp.dp))
            Text(
                text = copy.address,
                color = Color(metrics.textSecondaryColor),
                fontSize = metrics.addressTextSizeSp.sp,
                lineHeight = metrics.addressLineHeightSp.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = copy.coordinates,
            color = Color(metrics.textColor),
            fontSize = metrics.coordinatesTextSizeSp.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_calendar),
                contentDescription = labels.map,
                modifier = Modifier.size(metrics.iconSizeDp.dp)
            )
            Spacer(modifier = Modifier.width(metrics.iconTextGapDp.dp))
            Text(
                text = copy.date,
                color = Color(metrics.textColor),
                fontSize = metrics.dateTextSizeSp.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (metrics.logoVisible) {
        Spacer(modifier = Modifier.width(metrics.spacerDetailsLogoDp.dp))
        LogoBadge(metrics = metrics, logoLabel = labels.logo)
    }
}

@Composable
private fun MinimalContent(
    copy: CardCopy,
    metrics: GeoTagCardMetrics
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = metrics.detailsPaddingVerticalDp.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(metrics.detailsLineGapDp.dp)
    ) {
        val datePart = copy.dateLabel
        val timePart = copy.timeLabel

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (timePart.isNotEmpty()) {
                Text(
                    text = timePart,
                    color = Color(metrics.textColor),
                    fontSize = metrics.dateTextSizeSp.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = CARD_DATE_SEPARATOR,
                    color = Color(metrics.accentColor),
                    fontSize = metrics.dateTextSizeSp.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = datePart,
                color = Color(metrics.textColor),
                fontSize = metrics.dateTextSizeSp.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Text(
            text = "${copy.city}, ${copy.address}",
            color = Color(metrics.textSecondaryColor),
            fontSize = metrics.addressTextSizeSp.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = copy.coordinates,
            color = Color(metrics.textColor),
            fontSize = metrics.coordinatesTextSizeSp.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LogoBadge(metrics: GeoTagCardMetrics, logoLabel: String) {
    Box(
        modifier = Modifier
            .size(metrics.logoSizeDp.dp)
            .clip(RoundedCornerShape(metrics.logoCornerDp.dp))
            .background(Color(metrics.logoBackgroundColor)),
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

@Composable
private fun MapThumbnail(
    image: Any?,
    metrics: GeoTagCardMetrics,
    staticMapLabel: String,
    mapLabel: String
) {
    Box(
        modifier = Modifier
            .width(metrics.mapWidthDp.dp)
            .height(metrics.mapHeightDp.dp)
            .clip(RoundedCornerShape(metrics.mapCornerDp.dp))
            .background(Color(metrics.mapBackgroundColor))
    ) {
        when (image) {
            is Bitmap -> Image(
                bitmap = image.asImageBitmap(),
                contentDescription = staticMapLabel,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            is String -> AsyncImage(
                model = image,
                contentDescription = staticMapLabel,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            else -> Text(
                text = mapLabel,
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
                fontSize = 10.sp
            )
        }
    }
}
