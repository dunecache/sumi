package com.sumi.platform.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/**
 * Canvas bitmaps for the widget's drawn elements. Glance `Text` cannot carry bundled
 * fonts (system fonts only, documented platform limitation), and Glance `Row` cannot
 * express fractional weights, so the editorial serif total and the segmented proportional
 * bar are drawn here and shown as images. Everything is drawn at snapshot time, off the
 * render path, from values the state machine already resolved.
 */
internal object WidgetBrushes {

    /**
     * The proportional segmented bar: one segment per listed app (list order, descending),
     * then the remainder. Rounded outer ends (clipped), hairline surface-colored gaps at
     * the seams — consistent with Today's distribution bar. Drawn oversized and stretched
     * by the consumer (`ContentScale.FillBounds`) so it is crisp at any tile width; flat
     * colors keep the stretch invisible.
     *
     * @param segments `(fraction, argb)` in list order; fractions sum to 1 (the remainder
     * is included by the caller and covers the rest of the total).
     * @param gapArgb surface color of the tile, used for the seam lines.
     */
    fun segmentedBar(
        widthPx: Int = BAR_WIDTH_PX,
        heightPx: Int = BAR_HEIGHT_PX,
        segments: List<Pair<Float, Int>>,
        gapPx: Float = 3f,
        gapArgb: Int,
    ): Bitmap {
        require(segments.isNotEmpty()) { "a bar needs at least one segment" }
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = heightPx / 2f
        val clip = Path().apply {
            addRoundRect(
                RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()),
                radius,
                radius,
                Path.Direction.CW,
            )
        }
        canvas.clipPath(clip)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        var x = 0f
        for ((fraction, argb) in segments) {
            val segWidth = fraction * widthPx
            fill.color = argb
            canvas.drawRect(x, 0f, x + segWidth, heightPx.toFloat(), fill)
            x += segWidth
        }

        if (gapPx > 0f) {
            val gap = Paint().apply { color = gapArgb }
            var seam = 0f
            for (i in 0 until segments.lastIndex) {
                seam += segments[i].first * widthPx
                canvas.drawRect(seam - gapPx / 2f, 0f, seam + gapPx / 2f, heightPx.toFloat(), gap)
            }
        }
        return bitmap
    }

    /**
     * The editorial serif total as a bitmap (bundled Literata, drawn in the app process —
     * required for the serif to render in the launcher-hosted surface). [fontScale] and
     * [density] come from the resources at snapshot time so 1.3× font scale still renders.
     */
    fun serifTextBitmap(
        text: String,
        colorArgb: Int,
        fontScale: Float,
        density: Float,
        baseSp: Float = TOTAL_TEXT_SP,
        bold: Boolean = true,
        typeface: Typeface? = null,
    ): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = colorArgb
            textSize = baseSp * fontScale * density
            isFakeBoldText = bold
            typeface?.let { this.typeface = it }
        }
        // Drop any emoji/vertical glyph oddities: measure by ascent/descent, never by font metrics of a null font.
        val metrics = paint.fontMetrics
        val width = paint.measureText(text)
        val w = (width + PAINT_PADDING_PX * 2f).toInt().coerceAtLeast(1)
        val h = kotlin.math.ceil(
            (metrics.descent - metrics.ascent) + PAINT_PADDING_PX * 2f,
        ).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawText(text, PAINT_PADDING_PX, -metrics.ascent + PAINT_PADDING_PX, paint)
        return bitmap
    }

    /** Convert a launcher icon Drawable to a square bitmap; null-safe for unresolvable packages. */
    fun iconBitmap(drawable: Drawable, sizePx: Int = ICON_BITMAP_PX): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Character width budgeted for one app-name column (≈110 dp at name text size), so
     * rows reserve their share of the layout on any tile ≥ 250 dp wide. Names longer
     * than the budget are ellipsized by the loader with this paint-measured helper.
     */
    fun ellipsize(
        text: String,
        maxWidthPx: Float,
        paint: Paint,
    ): String {
        if (paint.measureText(text) <= maxWidthPx) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + ellipsis) > maxWidthPx) {
            end--
        }
        return text.substring(0, end).trimEnd() + ellipsis
    }

    private const val BAR_WIDTH_PX = 1200
    private const val BAR_HEIGHT_PX = 36
    private const val TOTAL_TEXT_SP = 30f
    private const val ICON_BITMAP_PX = 96
    private const val PAINT_PADDING_PX = 4f
}
