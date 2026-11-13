package com.bas1minute.sync.detox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.bas1minute.sync.R

/** Draws the "N-day focus streak" picture to share. Same brand look as the walk/run share card. */
object DetoxShareCard {
    private const val INK = 0xFF0F1235.toInt()
    private const val INK2 = 0xFF181D4C.toInt()
    private const val HALDI = 0xFFFFC21A.toInt()
    private const val VIOLET = 0xFF8F99FF.toInt()
    private const val MUTED = 0xFFBCC1E0.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    fun render(context: Context, streak: Int, best: Int, total: Int): Bitmap {
        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { color = INK })
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            color = INK2; alpha = 140
            // soft diagonal band for depth, no asset needed
        })
        val display = font(context, R.font.anek_extrabold, Typeface.DEFAULT_BOLD)
        val body = font(context, R.font.mukta_medium, Typeface.DEFAULT)
        val bold = font(context, R.font.mukta_bold, Typeface.DEFAULT_BOLD)
        fun text(s: String, x: Float, y: Float, size: Float, color: Int, tf: Typeface, align: Paint.Align = Paint.Align.LEFT) =
            c.drawText(s, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = tf; textAlign = align })

        val pad = 64f
        var y = 110f
        c.drawCircle(pad + 38f, y - 14f, 38f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HALDI })
        text("1", pad + 38f, y + 6f, 52f, INK, display, Paint.Align.CENTER)
        text("Bas 1 Minute", pad + 92f, y + 4f, 44f, WHITE, display)

        y += 170f
        text("FOCUS STREAK", pad, y, 34f, HALDI, bold)
        y += 150f
        text("$streak", pad, y, 220f, WHITE, display)
        val sw = Paint().apply { textSize = 220f; typeface = display }.measureText("$streak")
        text(if (streak == 1) "day" else "days", pad + sw + 20f, y, 56f, HALDI, display)
        y += 50f
        text("of 3+ hour focus, 10 AM \u2013 10 PM, no social media or browser", pad, y, 32f, MUTED, body)

        y += 90f
        val area = RectF(pad, y, w - pad, y + 220f)
        c.drawRoundRect(area, 32f, 32f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK2 })
        val cellW = area.width() / 2
        text("$best", area.left + 48f, area.top + 110f, 72f, VIOLET, display)
        text("best streak", area.left + 48f, area.top + 160f, 28f, MUTED, body)
        text("$total", area.left + cellW + 48f, area.top + 110f, 72f, HALDI, display)
        text("focus days", area.left + cellW + 48f, area.top + 160f, 28f, MUTED, body)

        val foot = h - 60f
        text("bas1minute.pythonanywhere.com", pad, foot, 28f, MUTED, body)
        text("#Bas1Minute", w - pad, foot, 28f, HALDI, bold, Paint.Align.RIGHT)
        return bmp
    }

    private fun font(context: Context, id: Int, fallback: Typeface): Typeface =
        try { ResourcesCompat.getFont(context, id) ?: fallback } catch (e: Exception) { fallback }
}
