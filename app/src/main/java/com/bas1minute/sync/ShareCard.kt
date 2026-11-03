package com.bas1minute.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Draws a shareable achievement image on the phone. Only what the card shows is shared. */
object ShareCard {
    sealed class Kind {
        data class Steps(val steps: Long, val goal: Int, val km: String) : Kind()
        /** days: 21 entries oldest first; true = goal reached, false = moved, null = no data */
        data class Streak(val streak: Int, val goalDays: Int, val days: List<Boolean?>) : Kind()
        data class Rank(val rank: Int, val size: Int, val circle: String, val points: Int) : Kind()
    }

    private const val INK = 0xFF0F1235.toInt()
    private const val INK2 = 0xFF181D4C.toInt()
    private const val INK3 = 0xFF232963.toInt()
    private const val HALDI = 0xFFFFC21A.toInt()
    private const val VIOLET = 0xFF8F99FF.toInt()
    private const val MUTED = 0xFFBCC1E0.toInt()
    private const val LAVENDER = 0xFFC9CEFF.toInt()

    fun render(context: Context, kind: Kind, name: String?, story: Boolean): Bitmap {
        val w = 1080
        val h = if (story) 1920 else 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val display = font(context, R.font.anek_extrabold, Typeface.DEFAULT_BOLD)
        val body = font(context, R.font.mukta_medium, Typeface.DEFAULT)
        val nf = NumberFormat.getIntegerInstance(Locale("en", "IN"))

        // Background
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), INK, INK2, Shader.TileMode.CLAMP)
        })
        fun text(s: String, x: Float, y: Float, size: Float, color: Int, tf: Typeface, align: Paint.Align = Paint.Align.CENTER) =
            c.drawText(s, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = tf; textAlign = align })

        // Header: logo + app name + date
        val top = if (story) 200f else 120f
        c.drawCircle(120f, top, 46f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HALDI })
        text("1", 120f, top + 22f, 62f, INK, display)
        text("Bas 1 Minute", 190f, top + 18f, 54f, 0xFFFFFFFF.toInt(), display, Paint.Align.LEFT)
        text(LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)), w - 80f, top + 14f, 38f, MUTED, body, Paint.Align.RIGHT)

        val cx = w / 2f
        val mid = h / 2f + (if (story) 0f else 20f)
        val lead = name?.takeIf { it.isNotBlank() }
        when (kind) {
            is Kind.Steps -> {
                val r = if (story) 300f else 245f   // smaller ring on the shorter post format
                text(if (lead != null) "$lead walked" else "Today I walked", cx, mid - r - 80f, 52f, MUTED, body)
                val ring = RectF(cx - r, mid - r + 40f, cx + r, mid + r + 40f)
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 56f; strokeCap = Paint.Cap.ROUND }
                c.drawArc(ring, -90f, 360f, false, stroke.apply { color = INK3 })
                val frac = (kind.steps.toFloat() / kind.goal).coerceIn(0f, 1f)
                if (frac > 0f) c.drawArc(ring, -90f, 360f * frac, false, stroke.apply { color = VIOLET })
                text(nf.format(kind.steps), cx, mid + 70f, 150f, 0xFFFFFFFF.toInt(), display)
                text("steps", cx, mid + 140f, 50f, MUTED, body)
                text(kind.km, cx, mid + r + (if (story) 160f else 125f), 72f, HALDI, display)
                text(if (kind.steps >= kind.goal) "Daily goal of ${nf.format(kind.goal)} done ✓" else "Goal ${nf.format(kind.goal)} steps",
                    cx, mid + r + (if (story) 230f else 185f), 44f, MUTED, body)
            }
            is Kind.Streak -> {
                text(if (lead != null) "$lead is on a" else "I’m on a", cx, mid - 420f, 52f, MUTED, body)
                text("🔥 ${kind.streak}", cx, mid - 220f, 190f, 0xFFFFFFFF.toInt(), display)
                text(if (kind.streak == 1) "day streak" else "day streak", cx, mid - 140f, 56f, HALDI, display)
                // 21-day grid: 3 rows × 7
                val dot = 96f; val gap = 22f
                val gridW = 7 * dot + 6 * gap
                val x0 = cx - gridW / 2f
                val y0 = mid - 60f
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                kind.days.take(21).forEachIndexed { i, d ->
                    val x = x0 + (i % 7) * (dot + gap) + dot / 2f
                    val y = y0 + (i / 7) * (dot + gap) + dot / 2f
                    p.color = when (d) { true -> HALDI; false -> LAVENDER; null -> INK3 }
                    c.drawCircle(x, y, dot / 2f, p)
                }
                text("${kind.goalDays} of the last 21 days at my step goal", cx, y0 + 3 * (dot + gap) + 70f, 44f, MUTED, body)
            }
            is Kind.Rank -> {
                text(if (lead != null) "$lead is" else "I’m", cx, mid - 420f, 52f, MUTED, body)
                text("🏆", cx, mid - 230f, 170f, 0xFFFFFFFF.toInt(), body)
                text("#${kind.rank}", cx, mid - 20f, 230f, HALDI, display)
                text("of ${kind.size} in ${kind.circle}", cx, mid + 70f, 58f, 0xFFFFFFFF.toInt(), display)
                text("${kind.points} point${if (kind.points == 1) "" else "s"} this week", cx, mid + 150f, 46f, MUTED, body)
            }
        }

        // Footer
        val foot = h - (if (story) 240f else 130f)
        text("Fit hone ke liye ghante nahi, minute chahiye.", cx, foot, 42f, 0xFFFFFFFF.toInt(), body)
        text("Bas 1 Minute · 7,000 steps + 4 one-minute bursts", cx, foot + 58f, 34f, MUTED, body)
        return bmp
    }

    private fun font(context: Context, id: Int, fallback: Typeface): Typeface =
        try { ResourcesCompat.getFont(context, id) ?: fallback } catch (e: Exception) { fallback }
}
