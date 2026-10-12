package com.patashot.myapp.presentation.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
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
        }
    }

    /**
     * Map thumbnail on the left, text block in the middle, logo on the right.
     *
     * The card hugs its content: a top gap of the shell's vertical padding, then the
     * map, details column and logo bottom-aligned onto a single baseline, then the
     * same gap underneath.
     */
    private fun renderMapLeft(
        metrics: GeoTagCardMetrics,
        copy: CardCopy,
        mapBitmap: Bitmap?,
        mapLabel: String
    ): Bitmap {
        val padTop = dp(metrics.paddingVerticalDp)
        val padBottom = dp(metrics.paddingVerticalDp)
        val padH = dp(metrics.paddingHorizontalDp)
        val cardWidth = dp(metrics.cardWidthDp)
        val mapWidth = dp(metrics.mapWidthDp)
        val mapHeight = dp(metrics.mapHeightDp)
        val mapLeft = padH
        val logoSize = if (metrics.logoVisible) dp(metrics.logoSizeDp) else 0
        val logoLeft = cardWidth - padH - logoSize
        val detailsGap = if (metrics.logoVisible) dp(metrics.spacerDetailsLogoDp) else 0
        val textLeft = mapLeft + mapWidth + dp(metrics.spacerMapDetailsDp)
        val detailsWidth = logoLeft - detailsGap - textLeft

        val cityPaint = textPaint(metrics.textColor, metrics.cityTextSizeSp, bold = true)
        val addressPaint = textPaint(metrics.textSecondaryColor, metrics.addressTextSizeSp)
        val coordinatesPaint = textPaint(metrics.textColor, metrics.coordinatesTextSizeSp)
        val datePaint = textPaint(metrics.textColor, metrics.dateTextSizeSp)

        val cityLayout = buildLayout(copy.city, cityPaint, detailsWidth, maxLines = 1)
        val addressLayout = buildLayout(copy.address, addressPaint, detailsWidth, maxLines = 2)
        val coordinatesLayout =
            buildLayout(copy.coordinates, coordinatesPaint, detailsWidth, maxLines = 1)
        val dateLayout = buildLayout(copy.date, datePaint, detailsWidth, maxLines = 1)

        val iconSize = dp(metrics.iconSizeDp)
        val addressRowH = maxOf(iconSize, addressLayout.height)
        val dateRowH = maxOf(iconSize, dateLayout.height)
        val detailsVPad = dp(metrics.detailsPaddingVerticalDp)
        val lineGap = dp(metrics.detailsLineGapDp)
        val columnH = detailsVPad * 2 + cityLayout.height + lineGap + addressRowH + lineGap +
            coordinatesLayout.height + lineGap + dateRowH

        val innerH = maxOf(mapHeight, columnH)
        val cardHeight = padTop + innerH + padBottom
        val baseline = padTop + innerH

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

        var y = baseline - columnH + detailsVPad

        canvas.drawLayout(cityLayout, textLeft.toFloat(), y.toFloat())
        y += cityLayout.height + lineGap

        val iconGap = iconSize + dp(metrics.iconTextGapDp)
        drawIcon(
            canvas = canvas,
            drawableId = android.R.drawable.ic_menu_mylocation,
            rect = Rect(textLeft, y, textLeft + iconSize, y + iconSize),
            tint = metrics.iconAccentColor.toArgb()
        )
        canvas.drawLayout(addressLayout, (textLeft + iconGap).toFloat(), y.toFloat())
        y += addressRowH + lineGap

        canvas.drawLayout(coordinatesLayout, textLeft.toFloat(), y.toFloat())
        y += coordinatesLayout.height + lineGap

        val dateTextTop = y + (dateRowH - dateLayout.height) / 2
        val dateIconTop = y + (dateRowH - iconSize) / 2
        drawIcon(
            canvas = canvas,
            drawableId = android.R.drawable.ic_menu_today,
            rect = Rect(textLeft, dateIconTop, textLeft + iconSize, dateIconTop + iconSize),
            tint = metrics.textColor.toArgb()
        )
        canvas.drawLayout(dateLayout, (textLeft + iconGap).toFloat(), dateTextTop.toFloat())

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
        canvas.drawRoundRect(
            rect,
            corner,
            corner,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = metrics.logoBackgroundColor.toArgb() }
        )
        val drawable = runCatching { context.getDrawable(R.drawable.geo_snap_icon) }.getOrNull()
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

    private fun drawIcon(canvas: Canvas, drawableId: Int, rect: Rect, tint: Int) {
        val drawable = runCatching { context.getDrawable(drawableId) }.getOrNull() ?: return
        drawable.setBounds(rect)
        drawable.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
        drawable.draw(canvas)
    }

    private fun Canvas.drawLayout(layout: StaticLayout, x: Float, top: Float) {
        save()
        translate(x, top)
        layout.draw(this)
        restore()
    }

    private fun dp(value: Float): Int = (value * density()).toInt()

    private fun sp(value: Float): Int = (value * density()).toInt()

    private fun density(): Float = RENDER_DENSITY * SUPERSAMPLE

    private fun dateFormat(): SimpleDateFormat = DATE_FORMAT.get()!!

    private fun Long.toArgb(): Int = this.toInt()

    private companion object {
        val DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat(CARD_DATE_PATTERN, Locale.ENGLISH)
        }

        /** Base resolution in dp density units (device independent). */
        const val RENDER_DENSITY = 3f

        /** Fixed supersample so the card beats every burn target resolution. */
        const val SUPERSAMPLE = 2f
    }
}
