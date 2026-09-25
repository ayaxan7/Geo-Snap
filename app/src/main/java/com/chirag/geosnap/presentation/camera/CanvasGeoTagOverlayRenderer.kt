package com.chirag.geosnap.presentation.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.chirag.geosnap.R
import com.chirag.geosnap.core.LocaleHelper
import com.chirag.geosnap.data.maps.StaticMapFetcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws the location card ([GeoTagCardContent]) into a [Bitmap] on a plain
 * [Canvas], so burning the card into photos and videos never depends on a live
 * Compose scene. The layout mirrors [GeoTagCard] using the shared
 * [GeoTagCardSpec] (map thumbnail, city, address, coordinates, date and logo).
 *
 * The bitmap is rendered at a fixed [RENDER_DENSITY] x [SUPERSAMPLE] resolution
 * that is independent of the device, so the burned card is deterministic and
 * stays crisp when scaled to any captured media size.
 */
@Singleton
class CanvasGeoTagOverlayRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val staticMapFetcher: StaticMapFetcher
) : GeoTagOverlayRenderer {

    override suspend fun render(content: GeoTagCardContent): Bitmap? {
        val mapBitmap = staticMapFetcher.fetch(content.staticMapUrl)
        return withContext(Dispatchers.Default) {
            runCatching { renderCardToBitmap(content, mapBitmap) }.getOrNull()
        }
    }

    private fun renderCardToBitmap(content: GeoTagCardContent, mapBitmap: Bitmap?): Bitmap {
        // The geotag card is intentionally always in English, independent of
        // the app language, so resolve card copy against an English context.
        val englishContext = LocaleHelper.englishContext(context)

        val padTop = dp(PADDING_VERTICAL_DP)
        val padBottom = dp(PADDING_VERTICAL_DP)
        val padH = dp(PADDING_HORIZONTAL_DP)
        val cardWidth = dp(CARD_WIDTH_DP)
        val mapWidth = dp(MAP_WIDTH_DP)
        val mapHeight = dp(MAP_HEIGHT_DP)
        val mapLeft = padH
        val logoSize = dp(LOGO_SIZE_DP)
        val logoLeft = cardWidth - padH - logoSize
        val textLeft = mapLeft + mapWidth + dp(SPACER_MAP_DETAILS_DP)
        val detailsWidth = logoLeft - dp(SPACER_DETAILS_LOGO_DP) - textLeft

        val latLabel = englishContext.getString(R.string.geotag_latitude)
        val lngLabel = englishContext.getString(R.string.geotag_longitude)
        val fetchingLocation = englishContext.getString(R.string.geotag_fetching_location)
        val coordinateText =
            "$latLabel: ${content.latitude?.let { "%.4f\u00B0".format(it) } ?: "--"} / " +
                "$lngLabel: ${content.longitude?.let { "%.4f\u00B0".format(it) } ?: "--"}"
        val dateText = dateFormat().format(Date(content.timestamp))

        val cityPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_COLOR.toArgb()
            typeface = Typeface.DEFAULT_BOLD
            textSize = sp(CITY_TEXT_SIZE_SP).toFloat()
        }
        val addressPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY_COLOR.toArgb()
            textSize = sp(ADDRESS_TEXT_SIZE_SP).toFloat()
        }
        val coordinatesPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_COLOR.toArgb()
            textSize = sp(COORDINATES_TEXT_SIZE_SP).toFloat()
        }
        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_COLOR.toArgb()
            textSize = sp(DATE_TEXT_SIZE_SP).toFloat()
        }

        val cityLayout = buildLayout(
            content.cityString ?: fetchingLocation,
            cityPaint,
            detailsWidth,
            maxLines = 1
        )
        val addressLayout = buildLayout(
            content.fullAddress ?: content.cityString ?: fetchingLocation,
            addressPaint,
            detailsWidth,
            maxLines = 2
        )
        val coordinatesLayout =
            buildLayout(coordinateText, coordinatesPaint, detailsWidth, maxLines = 1)
        val dateLayout = buildLayout(dateText, datePaint, detailsWidth, maxLines = 1)

        val iconSize = dp(ICON_SIZE_DP)
        val addressRowH = maxOf(iconSize, addressLayout.height)
        val dateRowH = maxOf(iconSize, dateLayout.height)
        val detailsVPad = dp(DETAILS_PADDING_VERTICAL_DP)
        val lineGap = dp(DETAILS_LINE_GAP_DP)
        val columnH = detailsVPad * 2 + cityLayout.height + lineGap + addressRowH + lineGap +
            coordinatesLayout.height + lineGap + dateRowH

        // The card hugs its content: an 8dp gap on top, then the map, details
        // column and logo are all bottom-aligned onto a single baseline, and an
        // 8dp gap underneath (the logo therefore sits just above the card edge).
        val innerH = maxOf(mapHeight, columnH)
        val cardHeight = padTop + innerH + padBottom
        val baseline = padTop + innerH

        val bitmap = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())

        val corner = dp(CARD_CORNER_DP).toFloat()
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CARD_BACKGROUND_COLOR.toArgb()
        }
        canvas.drawRoundRect(bounds, corner, corner, cardPaint)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CARD_BORDER_COLOR.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = dp(CARD_BORDER_DP).toFloat()
            isAntiAlias = true
        }
        canvas.drawRoundRect(bounds, corner, corner, borderPaint)

        drawMapThumbnail(
            canvas,
            RectF(
                mapLeft.toFloat(),
                (baseline - mapHeight).toFloat(),
                (mapLeft + mapWidth).toFloat(),
                baseline.toFloat()
            ),
            mapBitmap,
            englishContext.getString(R.string.geotag_map)
        )

        var y = baseline - columnH + detailsVPad

        canvas.drawLayout(cityLayout, textLeft.toFloat(), y.toFloat())
        y += cityLayout.height + lineGap

        val iconGap = iconSize + dp(ICON_TEXT_GAP_DP)
        drawIcon(
            canvas,
            android.R.drawable.ic_menu_mylocation,
            Rect(textLeft, y, textLeft + iconSize, y + iconSize),
            ICON_RED.toArgb()
        )
        canvas.drawLayout(addressLayout, (textLeft + iconGap).toFloat(), y.toFloat())
        y += addressRowH + lineGap

        canvas.drawLayout(coordinatesLayout, textLeft.toFloat(), y.toFloat())
        y += coordinatesLayout.height + lineGap

        val dateTextTop = y + (dateRowH - dateLayout.height) / 2
        val dateIconTop = y + (dateRowH - iconSize) / 2
        drawIcon(
            canvas,
            android.R.drawable.ic_menu_today,
            Rect(textLeft, dateIconTop, textLeft + iconSize, dateIconTop + iconSize),
            TEXT_COLOR.toArgb()
        )
        canvas.drawLayout(dateLayout, (textLeft + iconGap).toFloat(), dateTextTop.toFloat())

        // Logo: 44dp green box, bottom-aligned onto the same baseline as the map and
        // the date row, sitting just above the card's bottom edge.
        drawLogo(
            canvas,
            RectF(
                logoLeft.toFloat(),
                (baseline - logoSize).toFloat(),
                (logoLeft + logoSize).toFloat(),
                baseline.toFloat()
            )
        )

        return bitmap
    }

    private fun buildLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        maxLines: Int
    ): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()

    private fun drawMapThumbnail(
        canvas: Canvas,
        rect: RectF,
        mapBitmap: Bitmap?,
        mapLabel: String
    ) {
        val corner = dp(MAP_CORNER_DP).toFloat()
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MAP_BACKGROUND_COLOR.toArgb()
        }
        canvas.drawRoundRect(rect, corner, corner, bgPaint)

        if (mapBitmap != null) {
            canvas.save()
            val path = android.graphics.Path().apply {
                addRoundRect(rect, corner, corner, android.graphics.Path.Direction.CW)
            }
            canvas.clipPath(path)
            val side = minOf(mapBitmap.width, mapBitmap.height)
            val srcLeft = (mapBitmap.width - side) / 2
            val srcTop = (mapBitmap.height - side) / 2
            val src = Rect(srcLeft, srcTop, srcLeft + side, srcTop + side)
            val dst = Rect(
                rect.left.toInt(),
                rect.top.toInt(),
                rect.right.toInt(),
                rect.bottom.toInt()
            )
            canvas.drawBitmap(mapBitmap, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        } else {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TEXT_COLOR.toArgb()
                textSize = 10f * density()
            }
            val baseline =
                rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(mapLabel, rect.centerX(), baseline, textPaint)
        }
    }

    private fun drawLogo(canvas: Canvas, rect: RectF) {
        val corner = dp(LOGO_CORNER_DP).toFloat()
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LOGO_GREEN.toArgb()
        }
        canvas.drawRoundRect(rect, corner, corner, background)
        val drawable = runCatching { context.getDrawable(R.drawable.geo_snap_icon) }
            .getOrNull()
        if (drawable != null) {
            canvas.save()
            canvas.clipPath(
                android.graphics.Path().apply {
                    addRoundRect(rect, corner, corner, android.graphics.Path.Direction.CW)
                }
            )
            drawable.setBounds(
                rect.left.toInt(),
                rect.top.toInt(),
                rect.right.toInt(),
                rect.bottom.toInt()
            )
            drawable.draw(canvas)
            canvas.restore()
        }
    }

    private fun drawIcon(canvas: Canvas, drawableId: Int, rect: Rect, tint: Int) {
        val drawable = runCatching { context.getDrawable(drawableId) }.getOrNull()
            ?: return
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

    private fun dateFormat(): SimpleDateFormat =
        DATE_FORMAT.get()!!

    private fun Long.toArgb(): Int = this.toInt()

    private companion object {
        val DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat(DATE_PATTERN, Locale.ENGLISH)
        }

        const val CARD_WIDTH_DP = GeoTagCardSpec.CARD_WIDTH_DP
        const val CARD_CORNER_DP = GeoTagCardSpec.CARD_CORNER_DP
        const val CARD_BORDER_DP = GeoTagCardSpec.CARD_BORDER_DP
        const val CARD_BACKGROUND_COLOR = GeoTagCardSpec.CARD_BACKGROUND_COLOR
        const val CARD_BORDER_COLOR = GeoTagCardSpec.CARD_BORDER_COLOR
        const val TEXT_COLOR = GeoTagCardSpec.TEXT_COLOR
        const val TEXT_SECONDARY_COLOR = GeoTagCardSpec.TEXT_SECONDARY_COLOR
        const val ICON_RED = GeoTagCardSpec.ICON_RED
        const val LOGO_GREEN = GeoTagCardSpec.LOGO_GREEN
        const val MAP_BACKGROUND_COLOR = GeoTagCardSpec.MAP_BACKGROUND_COLOR
        const val PADDING_HORIZONTAL_DP = GeoTagCardSpec.PADDING_HORIZONTAL_DP
        const val PADDING_VERTICAL_DP = GeoTagCardSpec.PADDING_VERTICAL_DP
        const val MAP_WIDTH_DP = GeoTagCardSpec.MAP_WIDTH_DP
        const val MAP_HEIGHT_DP = GeoTagCardSpec.MAP_HEIGHT_DP
        const val MAP_CORNER_DP = GeoTagCardSpec.MAP_CORNER_DP
        const val SPACER_MAP_DETAILS_DP = GeoTagCardSpec.SPACER_MAP_DETAILS_DP
        const val SPACER_DETAILS_LOGO_DP = GeoTagCardSpec.SPACER_DETAILS_LOGO_DP
        const val DETAILS_PADDING_VERTICAL_DP = GeoTagCardSpec.DETAILS_PADDING_VERTICAL_DP
        const val DETAILS_LINE_GAP_DP = GeoTagCardSpec.DETAILS_LINE_GAP_DP
        const val ICON_TEXT_GAP_DP = GeoTagCardSpec.ICON_TEXT_GAP_DP
        const val CITY_TEXT_SIZE_SP = GeoTagCardSpec.CITY_TEXT_SIZE_SP
        const val ADDRESS_TEXT_SIZE_SP = GeoTagCardSpec.ADDRESS_TEXT_SIZE_SP
        const val COORDINATES_TEXT_SIZE_SP = GeoTagCardSpec.COORDINATES_TEXT_SIZE_SP
        const val DATE_TEXT_SIZE_SP = GeoTagCardSpec.DATE_TEXT_SIZE_SP
        const val ICON_SIZE_DP = GeoTagCardSpec.ICON_SIZE_DP
        const val LOGO_SIZE_DP = GeoTagCardSpec.LOGO_SIZE_DP
        const val LOGO_CORNER_DP = GeoTagCardSpec.LOGO_CORNER_DP
        // Map thumbnail

        const val DATE_PATTERN = "EEEE, dd/MM/yyyy - h:mm a"

        /** Base resolution in dp density units (device independent). */
        const val RENDER_DENSITY = 3f

        /** Fixed supersample so the card beats every burn target resolution. */
        const val SUPERSAMPLE = 2f
    }
}