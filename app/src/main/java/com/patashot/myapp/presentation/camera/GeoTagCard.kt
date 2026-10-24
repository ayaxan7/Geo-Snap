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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.patashot.myapp.R
import com.patashot.myapp.core.LocaleHelper

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
    val dateFormat = remember { createCardDateFormat() }

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
                CardLayout.QR_CODE -> QrCodeContent(
                    copy = labels.copy,
                    metrics = metrics,
                    locationUrl = content.locationUrl(),
                    scale = scale
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

/** Vertical geometry of the map layout's details column, relative to the row's top. */
private class MapLeftGeometry(
    val innerH: Int,
    val addressRowH: Int,
    val dateRowH: Int,
    val gap: Int,
    /** Top edge of the city, address, coordinates and date rows, in that order. */
    val tops: IntArray,
    /** Bottom edge of the same four rows. */
    val bottoms: IntArray
)

/**
 * Map thumbnail on the left, text block in the middle, logo on the right.
 *
 * The map, the details column and the logo are bottom-aligned onto a single baseline,
 * and the row is as tall as its tallest child. The details column fills the row's
 * height: space the text doesn't need is shared between the gaps.
 *
 * The logo is bottom-aligned, so the area above it is free. Every text line that sits
 * entirely above the logo gets the extra width up to the card's right padding, so it
 * only truncates when it genuinely runs out of room. Lines level with the logo keep
 * the narrower width and never run under it. [CanvasGeoTagOverlayRenderer] uses the
 * same rules, so the burned card matches this one.
 *
 * It is a custom [Layout] because each line's width depends on where it ends up
 * vertically, which a plain `Row` / `Column` cannot express. Text heights for a
 * candidate width come from intrinsics (a child may only be measured once), and the
 * final placement uses the real measured heights.
 */
@Composable
private fun RowScope.MapLeftContent(
    copy: CardCopy,
    metrics: GeoTagCardMetrics,
    labels: CardLabels,
    mapImage: Any?
) {
    Layout(
        modifier = Modifier.weight(1f),
        content = {
            // 0: map
            MapThumbnail(
                image = mapImage,
                metrics = metrics,
                staticMapLabel = labels.staticMap,
                mapLabel = labels.map
            )
            // 1: city
            Text(
                text = copy.city,
                color = Color(metrics.textColor),
                fontWeight = FontWeight.Bold,
                fontSize = metrics.cityTextSizeSp.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 2, 3: address icon + text
            Image(
                painter = painterResource(R.drawable.ic_map),
                contentDescription = labels.map,
                modifier = Modifier.size(metrics.iconSizeDp.dp)
            )
            Text(
                text = copy.address,
                color = Color(metrics.textSecondaryColor),
                fontSize = metrics.addressTextSizeSp.sp,
                lineHeight = metrics.addressLineHeightSp.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // 4: coordinates
            Text(
                text = copy.coordinates,
                color = Color(metrics.textColor),
                fontSize = metrics.coordinatesTextSizeSp.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 5, 6: date icon + text
            Image(
                painter = painterResource(R.drawable.ic_calendar),
                contentDescription = labels.map,
                modifier = Modifier.size(metrics.iconSizeDp.dp)
            )
            Text(
                text = copy.date,
                color = Color(metrics.textColor),
                fontSize = metrics.dateTextSizeSp.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 7: logo
            if (metrics.logoVisible) {
                LogoBadge(metrics = metrics, logoLabel = labels.logo)
            }
        }
    ) { measurables, constraints ->
        val totalW = constraints.maxWidth
        val mapW = metrics.mapWidthDp.dp.roundToPx()
        val mapH = metrics.mapHeightDp.dp.roundToPx()
        val logoSize = if (metrics.logoVisible) metrics.logoHeightDp.dp.roundToPx() else 0
        val detailsGap = if (metrics.logoVisible) metrics.spacerDetailsLogoDp.dp.roundToPx() else 0
        val iconSize = metrics.iconSizeDp.dp.roundToPx()
        val iconGap = iconSize + metrics.iconTextGapDp.dp.roundToPx()
        val detailsVPad = metrics.detailsPaddingVerticalDp.dp.roundToPx()
        val lineGap = metrics.detailsLineGapDp.dp.roundToPx()
        val clearance = lineGap

        val textLeft = mapW + metrics.spacerMapDetailsDp.dp.roundToPx()
        val logoLeft = totalW - logoSize
        // Width beside the logo, and width when the line is clear of the logo.
        val narrowW = logoLeft - detailsGap - textLeft
        val wideW = totalW - textLeft

        val mapM = measurables[0]
        val cityM = measurables[1]
        val addressIconM = measurables[2]
        val addressM = measurables[3]
        val coordinatesM = measurables[4]
        val dateIconM = measurables[5]
        val dateM = measurables[6]
        val logoM = if (metrics.logoVisible) measurables[7] else null

        // Max widths for city, address text, coordinates, date text.
        fun widths(wide: BooleanArray): IntArray = IntArray(4) { i ->
            val base = if (wide[i]) wideW else narrowW
            val w = if (i == 1 || i == 3) base - iconGap else base
            w.coerceAtLeast(0)
        }

        fun geometry(cityH: Int, addressH: Int, coordinatesH: Int, dateH: Int): MapLeftGeometry {
            val addressRowH = maxOf(iconSize, addressH)
            val dateRowH = maxOf(iconSize, dateH)
            val columnH = detailsVPad * 2 + cityH + lineGap + addressRowH + lineGap +
                    coordinatesH + lineGap + dateRowH
            // The row is as tall as its tallest child, logo included.
            val innerH = maxOf(mapH, columnH, logoSize)
            // The column fills the row; unused space is shared between the 3 gaps.
            val gap = lineGap + (innerH - columnH).coerceAtLeast(0) / 3
            val top0 = detailsVPad
            val top1 = top0 + cityH + gap
            val top2 = top1 + addressRowH + gap
            val top3 = top2 + coordinatesH + gap
            return MapLeftGeometry(
                innerH = innerH,
                addressRowH = addressRowH,
                dateRowH = dateRowH,
                gap = gap,
                tops = intArrayOf(top0, top1, top2, top3),
                bottoms = intArrayOf(
                    top0 + cityH,
                    top1 + addressRowH,
                    top2 + coordinatesH,
                    top3 + dateRowH
                )
            )
        }

        fun plan(wide: BooleanArray): MapLeftGeometry {
            val w = widths(wide)
            return geometry(
                cityH = cityM.minIntrinsicHeight(w[0]),
                addressH = addressM.minIntrinsicHeight(w[1]),
                coordinatesH = coordinatesM.minIntrinsicHeight(w[2]),
                dateH = dateM.minIntrinsicHeight(w[3])
            )
        }

        fun clearOfLogo(g: MapLeftGeometry, i: Int): Boolean =
            !metrics.logoVisible || g.bottoms[i] <= g.innerH - logoSize - clearance

        // Widening a line can change line counts and therefore positions, so settle
        // on which lines are wide with a few passes, then drop any that ended up
        // touching the logo.
        var wide = BooleanArray(4)
        var planned = plan(wide)
        for (pass in 0 until 3) {
            val next = BooleanArray(4) { clearOfLogo(planned, it) }
            if (next.contentEquals(wide)) break
            wide = next
            planned = plan(wide)
        }
        val safe = BooleanArray(4) { wide[it] && clearOfLogo(planned, it) }
        if (!safe.contentEquals(wide)) {
            wide = safe
            planned = plan(wide)
        }

        // Measure every child exactly once, with the settled widths.
        val w = widths(wide)
        val mapP = mapM.measure(Constraints())
        val cityP = cityM.measure(Constraints(maxWidth = w[0]))
        val addressIconP = addressIconM.measure(Constraints())
        val addressP = addressM.measure(Constraints(maxWidth = w[1]))
        val coordinatesP = coordinatesM.measure(Constraints(maxWidth = w[2]))
        val dateIconP = dateIconM.measure(Constraints())
        val dateP = dateM.measure(Constraints(maxWidth = w[3]))
        val logoP = logoM?.measure(Constraints())

        val g = geometry(cityP.height, addressP.height, coordinatesP.height, dateP.height)

        layout(totalW, g.innerH) {
            mapP.placeRelative(0, g.innerH - mapP.height)

            cityP.placeRelative(textLeft, g.tops[0])

            // Address: icon pinned to the top of its row, like Row(Alignment.Top).
            addressIconP.placeRelative(textLeft, g.tops[1])
            addressP.placeRelative(textLeft + iconGap, g.tops[1])

            coordinatesP.placeRelative(textLeft, g.tops[2])

            // Date: icon and text centred in their row.
            dateIconP.placeRelative(textLeft, g.tops[3] + (g.dateRowH - dateIconP.height) / 2)
            dateP.placeRelative(
                textLeft + iconGap,
                g.tops[3] + (g.dateRowH - dateP.height) / 2
            )

            logoP?.placeRelative(logoLeft, g.innerH - logoP.height)
        }
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
private fun RowScope.QrCodeContent(
    copy: CardCopy,
    metrics: GeoTagCardMetrics,
    locationUrl: String,
    scale: Float
) {
    QrCodeBadge(
        locationUrl = locationUrl,
        metrics = metrics,
        scale = scale
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

        Text(
            text = copy.address,
            color = Color(metrics.textSecondaryColor),
            fontSize = metrics.addressTextSizeSp.sp,
            lineHeight = metrics.addressLineHeightSp.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_calendar),
                contentDescription = null,
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
}

@Composable
private fun QrCodeBadge(
    locationUrl: String,
    metrics: GeoTagCardMetrics,
    scale: Float
) {
    val qrSizePx = remember(metrics.mapWidthDp, scale) {
        ((metrics.mapWidthDp * scale) * 3f).toInt().coerceAtLeast(120)
    }
    val qrBitmap = remember(locationUrl, qrSizePx) {
        QrCodeGenerator.generateQrBitmap(locationUrl, qrSizePx)
    }

    Box(
        modifier = Modifier
            .size(metrics.mapWidthDp.dp, metrics.mapHeightDp.dp)
            .clip(RoundedCornerShape(metrics.mapCornerDp.dp))
            .background(Color.White)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.template_qrcode),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun LogoBadge(metrics: GeoTagCardMetrics, logoLabel: String) {
    Box(
        modifier = Modifier
            .size(metrics.logoHeightDp.dp)
            .clip(RoundedCornerShape(metrics.logoCornerDp.dp))
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.geo_tag_app_icon),
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