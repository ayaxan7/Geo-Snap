package com.patashot.myapp.presentation.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import androidx.core.graphics.withClip
import com.patashot.myapp.R
import com.patashot.myapp.core.LocaleHelper
import com.patashot.myapp.data.maps.StaticMapFetcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws the location card ([GeoTagCardContent]) into a [Bitmap] on a plain
 * [Canvas], so burning the card into photos and videos never depends on a live
 * Compose scene. The layout mirrors [GeoTagCard]: the shell is shared via
 * [GeoTagCardShell], while the arrangement comes from the snapshot's
 * [CardTemplate] and its metrics from that template's [GeoTagCardStyle].
 *
 * The bitmap is rendered at a fixed [RENDER_DENSITY] x [SUPERSAMPLE] resolution
 * that is independent of the device, so the burned card is deterministic and
 * stays crisp when scaled to any captured media size.
 *
 * [renderDispatcher] is only settable from tests, so that native graphics
 * rendering stays on the thread that owns the graphics runtime. Production always
 * renders on [Dispatchers.Default].
 */
@Singleton
class CanvasGeoTagOverlayRenderer internal constructor(
    private val context: Context,
    private val staticMapFetcher: StaticMapFetcher,
    private val renderDispatcher: CoroutineDispatcher
) : GeoTagOverlayRenderer {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        staticMapFetcher: StaticMapFetcher
    ) : this(context, staticMapFetcher, Dispatchers.Default)

    override suspend fun render(content: GeoTagCardContent): Bitmap? {
        val mapBitmap = staticMapFetcher.fetch(content.staticMapUrl)
        return withContext(renderDispatcher) {
            runCatching { renderCardToBitmap(content, mapBitmap) }.getOrNull()
        }
    }

    private fun renderCardToBitmap(content: GeoTagCardContent, mapBitmap: Bitmap?): Bitmap {
        // The geotag card is intentionally always in English, independent of
        // the app language, so resolve card copy against an English context.
        val englishContext = LocaleHelper.englishContext(context)
        val copy = content.toCardCopy(
            fetchingText = englishContext.getString(R.string.geotag_fetching_location),
            latLabel = englishContext.getString(R.string.geotag_latitude),
            lngLabel = englishContext.getString(R.string.geotag_longitude),
            dateFormatter = dateFormat()
        )
        val metrics = content.template.metrics()

        return when (content.template.layout) {
            CardLayout.MAP_LEFT -> renderMapLeft(
                metrics = metrics,
                copy = copy,
                mapBitmap = mapBitmap,
                mapLabel = englishContext.getString(R.string.geotag_map)
            )
            CardLayout.MINIMAL -> renderMinimal(metrics = metrics, copy = copy)
            CardLayout.QR_CODE -> renderQrCode(metrics = metrics, copy = copy, content = content)
        }
    }

    /** Laid-out text and the resulting geometry for one pass of [renderMapLeft]. */
    private class MapLeftPlan(
        val city: StaticLayout,
        val address: StaticLayout,
        val coordinates: StaticLayout,
        val date: StaticLayout,
        val addressRowH: Int,
        val dateRowH: Int,
        val innerH: Int,
        val baseline: Int,
        val gap: Int,
        /** Bottom edge of city, address, coordinates and date rows, in that order. */
        val rowBottoms: IntArray
    )

    /**
     * Map thumbnail on the left, text block in the middle, logo on the right.
     *
     * Mirrors Compose's `Row(verticalAlignment = Bottom)` with
     * `padding(vertical = paddingVerticalDp)`: the same gap above and below, and the
     * row is as tall as its tallest child (map, details column or logo). All three
     * children end on one baseline just above the bottom gap.
     *
     * The logo is bottom-aligned, so the area above it is free. Each text line that
     * sits entirely above the logo is given the extra width up to the card's right
     * padding, so it only truncates when it genuinely runs out of room. Lines level
     * with the logo keep the narrower width and never run under it.
     */
    private fun renderMapLeft(
        metrics: GeoTagCardMetrics,
        copy: CardCopy,
        mapBitmap: Bitmap?,
        mapLabel: String
    ): Bitmap {
        // Symmetric padding, exactly like Compose's padding(vertical = ...).
        val padTop = dp(metrics.paddingVerticalDp)
        val padBottom = dp(metrics.paddingVerticalDp)
        val padH = dp(metrics.paddingHorizontalDp)
        val cardWidth = dp(metrics.cardWidthDp)
        val mapWidth = dp(metrics.mapWidthDp)
        val mapHeight = dp(metrics.mapHeightDp)
        val mapLeft = padH
        val logoSize = if (metrics.logoVisible) dp(metrics.logoHeightDp) else 0
        val logoLeft = cardWidth - padH - logoSize
        val detailsGap = if (metrics.logoVisible) dp(metrics.spacerDetailsLogoDp) else 0
        val textLeft = mapLeft + mapWidth + dp(metrics.spacerMapDetailsDp)
        // Width beside the logo, and width when the line is clear of the logo.
        val narrowWidth = logoLeft - detailsGap - textLeft
        val wideWidth = cardWidth - padH - textLeft

        val cityPaint = textPaint(metrics.textColor, metrics.cityTextSizeSp, bold = true)
        val addressPaint = textPaint(metrics.textSecondaryColor, metrics.addressTextSizeSp)
        val coordinatesPaint = textPaint(metrics.textColor, metrics.coordinatesTextSizeSp)
        val datePaint = textPaint(metrics.textColor, metrics.dateTextSizeSp)

        // Address and date are drawn after an icon, so they lose the icon's width.
        val iconSize = dp(metrics.iconSizeDp)
        val iconGap = iconSize + dp(metrics.iconTextGapDp)
        val detailsVPad = dp(metrics.detailsPaddingVerticalDp)
        val lineGap = dp(metrics.detailsLineGapDp)
        val clearance = lineGap

        fun plan(wide: BooleanArray): MapLeftPlan {
            fun width(i: Int) = if (wide[i]) wideWidth else narrowWidth
            val city = buildLayout(copy.city, cityPaint, width(0), maxLines = 1)
            val address = buildLayout(
                copy.address, addressPaint, (width(1) - iconGap).coerceAtLeast(1), maxLines = 2
            )
            val coordinates =
                buildLayout(copy.coordinates, coordinatesPaint, width(2), maxLines = 1)
            val date = buildLayout(
                copy.date, datePaint, (width(3) - iconGap).coerceAtLeast(1), maxLines = 1
            )

            val addressRowH = maxOf(iconSize, address.height)
            val dateRowH = maxOf(iconSize, date.height)
            val columnH = detailsVPad * 2 + city.height + lineGap + addressRowH + lineGap +
                    coordinates.height + lineGap + dateRowH

            // The row is as tall as its tallest child, logo included.
            val innerH = maxOf(mapHeight, columnH, logoSize)
            val baseline = padTop + innerH

            // The details column fills the whole row height; space the text doesn't
            // need is shared between the three gaps instead of piling up on top.
            val gap = lineGap + (innerH - columnH).coerceAtLeast(0) / 3

            val top0 = baseline - innerH + detailsVPad
            val top1 = top0 + city.height + gap
            val top2 = top1 + addressRowH + gap
            val top3 = top2 + coordinates.height + gap
            return MapLeftPlan(
                city, address, coordinates, date, addressRowH, dateRowH, innerH, baseline, gap,
                intArrayOf(
                    top0 + city.height,
                    top1 + addressRowH,
                    top2 + coordinates.height,
                    top3 + dateRowH
                )
            )
        }

        fun clearOfLogo(p: MapLeftPlan, i: Int): Boolean =
            !metrics.logoVisible || p.rowBottoms[i] <= p.baseline - logoSize - clearance

        // Widening a line can change line counts and therefore positions, so settle
        // on which lines are wide with a few passes, then drop any that ended up
        // touching the logo.
        var wide = BooleanArray(4)
        var plan = plan(wide)
        for (pass in 0 until 3) {
            val next = BooleanArray(4) { clearOfLogo(plan, it) }
            if (next.contentEquals(wide)) break
            wide = next
            plan = plan(wide)
        }
        val safe = BooleanArray(4) { wide[it] && clearOfLogo(plan, it) }
        if (!safe.contentEquals(wide)) {
            wide = safe
            plan = plan(wide)
        }

        val innerH = plan.innerH
        val baseline = plan.baseline
        val cardHeight = padTop + innerH + padBottom

        val bitmap = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawCardShell(canvas, bitmap, metrics)

        drawMapThumbnail(
            canvas = canvas,
            rect = RectF(
                mapLeft.toFloat(),
                (baseline - mapHeight).toFloat(),
                (mapLeft + mapWidth).toFloat(),
                baseline.toFloat()
            ),
            mapBitmap = mapBitmap,
            mapLabel = mapLabel,
            metrics = metrics
        )

        var y = baseline - innerH + detailsVPad

        canvas.drawLayout(plan.city, textLeft.toFloat(), y.toFloat())
        y += plan.city.height + plan.gap

        // Same icons as the Compose card, drawn untinted.
        drawIcon(
            canvas = canvas,
            drawableId = R.drawable.ic_map,
            rect = Rect(textLeft, y, textLeft + iconSize, y + iconSize)
        )
        canvas.drawLayout(plan.address, (textLeft + iconGap).toFloat(), y.toFloat())
        y += plan.addressRowH + plan.gap

        canvas.drawLayout(plan.coordinates, textLeft.toFloat(), y.toFloat())
        y += plan.coordinates.height + plan.gap

        val dateTextTop = y + (plan.dateRowH - plan.date.height) / 2
        val dateIconTop = y + (plan.dateRowH - iconSize) / 2
        drawIcon(
            canvas = canvas,
            drawableId = R.drawable.ic_calendar,
            rect = Rect(textLeft, dateIconTop, textLeft + iconSize, dateIconTop + iconSize)
        )
        canvas.drawLayout(plan.date, (textLeft + iconGap).toFloat(), dateTextTop.toFloat())

        if (metrics.logoVisible) {
            drawLogo(
                canvas = canvas,
                rect = RectF(
                    logoLeft.toFloat(),
                    (baseline - logoSize).toFloat(),
                    (logoLeft + logoSize).toFloat(),
                    baseline.toFloat()
                ),
                metrics = metrics
            )
        }

        return bitmap
    }

    /**
     * QR code card: dark shell, white QR code box on the left embedding location link,
     * details (city, address, date with calendar icon) on the right.
     */
    private fun renderQrCode(
        metrics: GeoTagCardMetrics,
        copy: CardCopy,
        content: GeoTagCardContent
    ): Bitmap {
        val padTop = dp(metrics.paddingVerticalDp)
        val padBottom = dp(metrics.paddingVerticalDp)
        val padH = dp(metrics.paddingHorizontalDp)
        val cardWidth = dp(metrics.cardWidthDp)
        val qrBoxWidth = dp(metrics.mapWidthDp)
        val qrBoxHeight = dp(metrics.mapHeightDp)
        val qrLeft = padH
        val textLeft = qrLeft + qrBoxWidth + dp(metrics.spacerMapDetailsDp)
        val detailsWidth = cardWidth - padH - textLeft

        val cityPaint = textPaint(metrics.textColor, metrics.cityTextSizeSp, bold = true)
        val addressPaint = textPaint(metrics.textSecondaryColor, metrics.addressTextSizeSp)
        val datePaint = textPaint(metrics.textColor, metrics.dateTextSizeSp)

        val cityLayout = buildLayout(copy.city, cityPaint, detailsWidth, maxLines = 1)
        val addressLayout = buildLayout(copy.address, addressPaint, detailsWidth, maxLines = 2)

        val iconSize = dp(metrics.iconSizeDp)
        val iconGap = iconSize + dp(metrics.iconTextGapDp)
        val dateLayoutWidth = (detailsWidth - iconGap).coerceAtLeast(1)
        val dateLayout = buildLayout(copy.date, datePaint, dateLayoutWidth, maxLines = 1)

        val dateRowH = maxOf(iconSize, dateLayout.height)
        val detailsVPad = dp(metrics.detailsPaddingVerticalDp)
        val lineGap = dp(metrics.detailsLineGapDp)
        val columnH = detailsVPad * 2 + cityLayout.height + lineGap + addressLayout.height + lineGap + dateRowH

        val innerH = maxOf(qrBoxHeight, columnH)
        val cardHeight = padTop + innerH + padBottom
        val baseline = padTop + innerH

        val bitmap = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawCardShell(canvas, bitmap, metrics)

        val corner = dp(metrics.mapCornerDp).toFloat()
        val qrRect = RectF(
            qrLeft.toFloat(),
            (baseline - qrBoxHeight).toFloat(),
            (qrLeft + qrBoxWidth).toFloat(),
            baseline.toFloat()
        )
        canvas.drawRoundRect(
            qrRect,
            corner,
            corner,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        )

        val innerPadding = dp(4f)
        val qrBitmapSize = (qrBoxWidth - innerPadding * 2).coerceAtLeast(1)
        val qrCodeBitmap = QrCodeGenerator.generateQrBitmap(content.locationUrl(), qrBitmapSize)
        if (qrCodeBitmap != null) {
            val qrDestRect = Rect(
                qrLeft + innerPadding,
                (baseline - qrBoxHeight) + innerPadding,
                qrLeft + qrBoxWidth - innerPadding,
                baseline - innerPadding
            )
            canvas.drawBitmap(qrCodeBitmap, null, qrDestRect, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        var y = baseline - columnH + detailsVPad

        canvas.drawLayout(cityLayout, textLeft.toFloat(), y.toFloat())
        y += cityLayout.height + lineGap

        canvas.drawLayout(addressLayout, textLeft.toFloat(), y.toFloat())
        y += addressLayout.height + lineGap

        val dateTextTop = y + (dateRowH - dateLayout.height) / 2
        val dateIconTop = y + (dateRowH - iconSize) / 2
        drawIcon(
            canvas = canvas,
            drawableId = R.drawable.ic_calendar,
            rect = Rect(textLeft, dateIconTop, textLeft + iconSize, dateIconTop + iconSize)
        )
        canvas.drawLayout(dateLayout, (textLeft + iconGap).toFloat(), dateTextTop.toFloat())

        return bitmap
    }

    /**
     * No map, no logo: one centred column of time, date, location and coordinates.
     *
     * The time comes first with the accent separator between the two date halves,
     * mirroring [MinimalContent]. Because the separator is a styled span inside a
     * single layout, it is measured and centred as one run of text rather than as
     * three independently placed blocks.
     *
     * Geometry matches the preview: the layouts are laid out over the shell's
     * content width but drawn at [GeoTagCardShell.PADDING_HORIZONTAL_DP], which is
     * where Compose's padded `Column` starts. Drawing them at x=0 would centre the
     * text one horizontal padding to the left of the card, and the details padding
     * is applied above and below the column exactly as [renderMapLeft] does.
     */
    private fun renderMinimal(metrics: GeoTagCardMetrics, copy: CardCopy): Bitmap {
        val padV = dp(metrics.paddingVerticalDp)
        val padH = dp(metrics.paddingHorizontalDp)
        val cardWidth = dp(metrics.cardWidthDp)
        val contentWidth = cardWidth - padH * 2

        val datePaint = textPaint(metrics.textColor, metrics.dateTextSizeSp, bold = true)
        val addressPaint = textPaint(metrics.textSecondaryColor, metrics.addressTextSizeSp)
        val coordinatesPaint = textPaint(metrics.textColor, metrics.coordinatesTextSizeSp)

        val dateLayout = buildLayout(
            text = timeWithAccentSeparator(copy, metrics),
            paint = datePaint,
            width = contentWidth,
            maxLines = 1,
            alignment = Layout.Alignment.ALIGN_CENTER
        )
        val locationLayout = buildLayout(
            text = "${copy.city}, ${copy.address}",
            paint = addressPaint,
            width = contentWidth,
            maxLines = 2,
            alignment = Layout.Alignment.ALIGN_CENTER
        )
        val coordinatesLayout = buildLayout(
            text = copy.coordinates,
            paint = coordinatesPaint,
            width = contentWidth,
            maxLines = 1,
            alignment = Layout.Alignment.ALIGN_CENTER
        )

        val lineGap = dp(metrics.detailsLineGapDp)
        val detailsVPad = dp(metrics.detailsPaddingVerticalDp)
        val columnH = dateLayout.height + lineGap + locationLayout.height + lineGap +
                coordinatesLayout.height
        val cardHeight = padV + detailsVPad + columnH + detailsVPad + padV

        val bitmap = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawCardShell(canvas, bitmap, metrics)

        var y = (padV + detailsVPad).toFloat()
        canvas.drawLayout(dateLayout, padH.toFloat(), y)
        y += dateLayout.height + lineGap
        canvas.drawLayout(locationLayout, padH.toFloat(), y)
        y += locationLayout.height + lineGap
        canvas.drawLayout(coordinatesLayout, padH.toFloat(), y)

        return bitmap
    }

    /**
     * The time, the accent separator and the date as a single spannable string, so
     * the whole row centres as one unit. Falls back to the plain date when there
     * is no time half to lead with.
     */
    private fun timeWithAccentSeparator(copy: CardCopy, metrics: GeoTagCardMetrics): CharSequence {
        if (copy.timeLabel.isEmpty()) return copy.dateLabel
        return SpannableStringBuilder(copy.timeLabel)
            .append(CARD_DATE_SEPARATOR)
            .append(copy.dateLabel)
            .apply {
                setSpan(
                    ForegroundColorSpan(metrics.accentColor.toArgb()),
                    copy.timeLabel.length,
                    copy.timeLabel.length + CARD_DATE_SEPARATOR.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
    }

    /** The card background and border every layout shares. */
    private fun drawCardShell(canvas: Canvas, bitmap: Bitmap, metrics: GeoTagCardMetrics) {
        val bounds = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        val corner = dp(metrics.cardCornerDp).toFloat()
        canvas.drawRoundRect(
            bounds,
            corner,
            corner,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = metrics.cardBackgroundColor.toArgb()
            }
        )
        canvas.drawRoundRect(
            bounds,
            corner,
            corner,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = metrics.cardBorderColor.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = dp(metrics.cardBorderDp).toFloat()
            }
        )
    }

    private fun textPaint(color: Long, textSizeSp: Float, bold: Boolean = false): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            if (bold) typeface = Typeface.DEFAULT_BOLD
            textSize = sp(textSizeSp).toFloat()
        }

    private fun buildLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        maxLines: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()

    private fun drawMapThumbnail(
        canvas: Canvas,
        rect: RectF,
        mapBitmap: Bitmap?,
        mapLabel: String,
        metrics: GeoTagCardMetrics
    ) {
        val corner = dp(metrics.mapCornerDp).toFloat()
        canvas.drawRoundRect(
            rect,
            corner,
            corner,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = metrics.mapBackgroundColor.toArgb() }
        )

        if (mapBitmap != null) {
            canvas.save()
            canvas.clipPath(Path().apply {
                addRoundRect(rect, corner, corner, Path.Direction.CW)
            })
            val side = minOf(mapBitmap.width, mapBitmap.height)
            val srcLeft = (mapBitmap.width - side) / 2
            val srcTop = (mapBitmap.height - side) / 2
            canvas.drawBitmap(
                mapBitmap,
                Rect(srcLeft, srcTop, srcLeft + side, srcTop + side),
                Rect(
                    rect.left.toInt(),
                    rect.top.toInt(),
                    rect.right.toInt(),
                    rect.bottom.toInt()
                ),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            canvas.restore()
        } else {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = metrics.textColor.toArgb()
                textSize = 10f * density()
            }
            canvas.drawText(
                mapLabel,
                rect.centerX(),
                rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2,
                textPaint
            )
        }
    }

    private fun drawLogo(canvas: Canvas, rect: RectF, metrics: GeoTagCardMetrics) {
        val corner = dp(metrics.logoCornerDp).toFloat()
        val bgColor = metrics.logoBackgroundColor.toArgb()
        if (Color.alpha(bgColor) > 0) {
            canvas.drawRoundRect(
                rect,
                corner,
                corner,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
            )
        }
        val drawable = runCatching { context.getDrawable(R.drawable.geo_tag_app_icon) }.getOrNull()
            ?: return
        canvas.withClip(Path().apply { addRoundRect(rect, corner, corner, Path.Direction.CW) }) {
            drawable.setBounds(
                rect.left.toInt(),
                rect.top.toInt(),
                rect.right.toInt(),
                rect.bottom.toInt()
            )
            drawable.draw(this)
        }
    }

    private fun drawIcon(canvas: Canvas, drawableId: Int, rect: Rect, tint: Int? = null) {
        val drawable = runCatching { context.getDrawable(drawableId) }.getOrNull() ?: return
        drawable.setBounds(rect)
        drawable.colorFilter =
            tint?.let { PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }
        drawable.draw(canvas)
    }

    private fun Canvas.drawLayout(layout: StaticLayout, x: Float, top: Float) {
        save()
        translate(x, top)
        layout.draw(this)
        restore()
    }

    // Rounded rather than truncated so sizes match Compose and don't lose a pixel each.
    private fun dp(value: Float): Int = (value * density()).roundToInt()

    private fun sp(value: Float): Int = (value * density()).roundToInt()

    private fun density(): Float = RENDER_DENSITY * SUPERSAMPLE

    private fun dateFormat(): SimpleDateFormat = DATE_FORMAT.get()!!

    private fun Long.toArgb(): Int = this.toInt()

    private companion object {
        val DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat = createCardDateFormat()
        }

        /** Base resolution in dp density units (device independent). */
        const val RENDER_DENSITY = 3f

        /** Fixed supersample so the card beats every burn target resolution. */
        const val SUPERSAMPLE = 2f
    }
}