package com.chirag.geosnap.presentation.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.chirag.geosnap.R
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
 * Compose scene. The layout mirrors [GeoTagCard] (map thumbnail, city, address,
 * coordinates, date and logo) and is rendered at 2x density so it stays crisp
 * when scaled to the captured media size.
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
        val density = context.resources.displayMetrics.density * DENSITY_SCALE
        fun dp(value: Float) = (value * density).toInt()
        fun sp(value: Float) = (value * density).toInt()

        val padding = dp(12f)
        val mapSize = dp(80f)
        val rowGap = dp(12f)
        val colGap = dp(8f)
        val logoSize = dp(40f)
        val smallGap = dp(4f)
        val corner = dp(16f)

        val cardWidth = dp(CARD_DP.toFloat())
        val columnWidth =
            cardWidth - padding * 2 - mapSize - rowGap - logoSize - colGap

        val cardText = "EEEE , dd/MM/yyyy - h:mm a"
        val dateText = SafeDateFormat.get(cardText).format(Date(content.timestamp))

        val coordinateText = "LAT: ${content.latitude?.let { "%.4f\u00B0".format(it) } ?: "--"} / " +
            "LOG: ${content.longitude?.let { "%.4f\u00B0".format(it) } ?: "--"}"

        val cityPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textSize = sp(14f).toFloat()
        }
        val addressPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(204, 255, 255, 255)
            textSize = sp(11f).toFloat()
        }
        val smallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sp(11f).toFloat()
        }

        fun measure(text: String, paint: TextPaint, maxLines: Int): StaticLayout =
            StaticLayout.Builder
                .obtain(text, 0, text.length, paint, columnWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()

        val cityLayout = measure(
            content.cityString ?: "Fetching location...",
            cityPaint,
            maxLines = 1
        )
        val addressLayout = measure(
            content.fullAddress ?: content.cityString ?: "Fetching location...",
            addressPaint,
            maxLines = 2
        )
        val coordinateLayout = measure(coordinateText, smallPaint, maxLines = 1)
        val dateLayout = measure(dateText, smallPaint, maxLines = 1)

        val columnHeight =
            cityLayout.height + smallGap + addressLayout.height + smallGap +
                coordinateLayout.height + smallGap + dateLayout.height
        val innerHeight = maxOf(columnHeight, mapSize)
        val cardHeight = innerHeight + padding * 2

        val bitmap = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(0f, 0f, cardWidth.toFloat(), cardHeight.toFloat())

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(128, 0, 0, 0)
        }
        canvas.drawRoundRect(bounds, corner.toFloat(), corner.toFloat(), cardPaint)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(1f).toFloat()
            isAntiAlias = true
        }
        canvas.drawRoundRect(bounds, corner.toFloat(), corner.toFloat(), borderPaint)

        val top = padding
        val mapRect = RectF(
            padding.toFloat(),
            top.toFloat(),
            (padding + mapSize).toFloat(),
            (top + mapSize).toFloat()
        )
        drawMapThumbnail(canvas, mapRect, mapBitmap, density)

        val textLeft = (padding + mapSize + rowGap).toFloat()
        var y = top.toFloat() - addressPaint.fontMetrics.ascent // align top of first line
        canvas.drawLayout(cityLayout, textLeft, y)
        y += cityLayout.height + smallGap
        canvas.drawLayout(addressLayout, textLeft, y)
        y += addressLayout.height + smallGap
        canvas.drawLayout(coordinateLayout, textLeft, y)
        y += coordinateLayout.height + smallGap
        canvas.drawLayout(dateLayout, textLeft, y)

        val logoRect = RectF(
            (cardWidth - padding - logoSize).toFloat(),
            (top + (innerHeight - logoSize) / 2).toFloat(),
            (cardWidth - padding).toFloat(),
            (top + (innerHeight - logoSize) / 2 + logoSize).toFloat()
        )
        drawLogo(canvas, logoRect, dp(8f).toFloat())

        return bitmap
    }

    private fun drawMapThumbnail(
        canvas: Canvas,
        rect: RectF,
        mapBitmap: Bitmap?,
        density: Float
    ) {
        val corner = 8 * density
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
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
            val src = android.graphics.Rect(
                srcLeft,
                srcTop,
                srcLeft + side,
                srcTop + side
            )
            val dst = android.graphics.Rect(
                rect.left.toInt(),
                rect.top.toInt(),
                rect.right.toInt(),
                rect.bottom.toInt()
            )
            canvas.drawBitmap(mapBitmap, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        } else {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 12 * density
            }
            val baseline =
                (rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2)
            canvas.drawText("Map", rect.centerX(), baseline, textPaint)
        }
    }

    private fun drawLogo(canvas: Canvas, rect: RectF, corner: Float) {
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
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

    private fun Canvas.drawLayout(layout: StaticLayout, x: Float, top: Float) {
        save()
        translate(x, top)
        layout.draw(this)
        restore()
    }

    private object SafeDateFormat {
        fun get(pattern: String): SimpleDateFormat =
            SimpleDateFormat(pattern, Locale.getDefault())
    }

    private companion object {
        /** Card design width in dp (matches the ~340dp card on screen). */
        const val CARD_DP = 340
        const val DENSITY_SCALE = 2f
    }
}