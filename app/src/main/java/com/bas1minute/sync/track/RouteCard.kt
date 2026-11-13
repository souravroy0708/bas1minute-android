package com.bas1minute.sync.track

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.bas1minute.sync.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Draws the walk/run picture to share or save. Everything happens on the phone. */
object RouteCard {
    data class Input(
        val row: ActivityRow,
        /** Route to draw (already trimmed when "Hide start & finish" is on). */
        val segments: List<List<LatLng>>,
        val loop: Boolean,
        /** Map picture (from the live map in the share sheet). Null draws the route on the brand background. */
        val map: Bitmap?,
        val name: String?,
        val story: Boolean,
        val hideEnds: Boolean,
    )

    private const val INK = 0xFF0F1235.toInt()
    private const val INK2 = 0xFF181D4C.toInt()
    private const val INK3 = 0xFF232963.toInt()
    private const val LINE = 0xFF323A7C.toInt()
    private const val HALDI = 0xFFFFC21A.toInt()
    private const val VIOLET = 0xFF8F99FF.toInt()
    private const val MUTED = 0xFFBCC1E0.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val GREEN = 0xFF5ED6A0.toInt()
    private const val RED = 0xFFFF6B5B.toInt()

    fun render(context: Context, inp: Input): Bitmap {
        val w = 1080
        val h = if (inp.story) 1920 else 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val display = font(context, R.font.anek_extrabold, Typeface.DEFAULT_BOLD)
        val bold = font(context, R.font.anek_bold, Typeface.DEFAULT_BOLD)
        val body = font(context, R.font.mukta_medium, Typeface.DEFAULT)
        val r = inp.row

        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), INK, INK2, Shader.TileMode.CLAMP)
        })
        fun text(s: String, x: Float, y: Float, size: Float, color: Int, tf: Typeface, align: Paint.Align = Paint.Align.LEFT) =
            c.drawText(s, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = tf; textAlign = align })

        val pad = 64f
        // Header
        var y = if (inp.story) 170f else 110f
        c.drawCircle(pad + 38f, y - 14f, 38f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HALDI })
        text("1", pad + 38f, y + 6f, 52f, INK, display, Paint.Align.CENTER)
        text("Bas 1 Minute", pad + 92f, y + 4f, 44f, WHITE, display)
        val started = Instant.ofEpochMilli(r.startedAt).atZone(ZoneId.systemDefault())
        text(started.format(DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a", Locale.ENGLISH)), w - pad, y + 2f, 32f, MUTED, body, Paint.Align.RIGHT)

        y += 100f
        val who = inp.name?.takeIf { it.isNotBlank() }
        text(if (who != null) "$who · ${r.title}" else r.title, pad, y, 58f, WHITE, display)
        val badge = when (r.source) {
            TrackSource.Gps -> if (inp.loop) "↻ Round trip" else "Start → Finish"
            TrackSource.Steps -> "No GPS · from steps"
            TrackSource.Timer -> "No GPS · time"
        }
        y += 54f
        text("${r.type.label}  ·  $badge", pad, y, 36f, HALDI, bold)

        // Route area
        y += 36f
        val areaH = if (inp.story) 900f else 610f
        val area = RectF(pad, y, w - pad, y + areaH)
        val clip = Path().apply { addRoundRect(area, 44f, 44f, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)
        val map = inp.map
        if (map != null) {
            drawCover(c, map, area)
        } else {
            c.drawRect(area, Paint().apply { color = INK3 })
            // Soft grid so the route reads as a map
            val g = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LINE; strokeWidth = 2f; alpha = 90 }
            var gx = area.left; while (gx < area.right) { c.drawLine(gx, area.top, gx, area.bottom, g); gx += 72f }
            var gy = area.top; while (gy < area.bottom) { c.drawLine(area.left, gy, area.right, gy, g); gy += 72f }
            if (r.source.hasMap) drawRoute(c, inp.segments, RectF(area.left + 70f, area.top + 70f, area.right - 70f, area.bottom - 70f), inp.loop)
            else {
                // No route without GPS: what was counted instead, large in the middle
                val main = r.steps?.takeIf { it > 0 }?.let { String.format(Locale.US, "%,d", it) } ?: Fmt.duration(r.movingMs)
                val sub = if ((r.steps ?: 0) > 0) "steps" else r.type.label.lowercase(Locale.ENGLISH)
                text(main, area.centerX(), area.centerY() + 40f, if (inp.story) 190f else 150f, WHITE, display, Paint.Align.CENTER)
                text(sub, area.centerX(), area.centerY() + 110f, 44f, HALDI, display, Paint.Align.CENTER)
            }
        }
        c.restore()
        c.drawRoundRect(area, 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = LINE })
        if (map != null) text("© OpenStreetMap contributors", area.right - 18f, area.bottom - 18f, 24f, 0xFF333333.toInt(), body, Paint.Align.RIGHT)
        if (inp.hideEnds) text("Start and finish hidden for privacy", area.left + 22f, area.bottom - 18f, 24f, if (map != null) 0xFF333333.toInt() else MUTED, body)

        // Stats: big distance, then a 3 x 2 grid
        y = area.bottom + if (inp.story) 150f else 118f
        // Time only (no distance known): the time is the big number
        val km = if (r.distanceM > 0) Fmt.km(r.distanceM) else Fmt.duration(r.movingMs)
        text(km, pad, y, if (inp.story) 160f else 128f, WHITE, display)
        val kmW = Paint().apply { textSize = if (inp.story) 160f else 128f; typeface = display }.measureText(km)
        text(if (r.distanceM > 0) "km" else "time", pad + kmW + 16f, y, 52f, HALDI, display)

        val paceOrSpeed = if (r.type == ActivityType.Cycle) "Avg speed" to "${Fmt.kmh(r.avgSpeedMs)} km/h" else "Avg pace" to "${Fmt.pace(r.avgSpeedMs)} /km"
        val cells = listOf(
            "Time" to Fmt.duration(r.movingMs),
            paceOrSpeed,
            (if (r.type == ActivityType.Cycle) "Max speed" to "${Fmt.kmh(r.maxSpeedMs)} km/h" else "Avg speed" to "${Fmt.kmh(r.avgSpeedMs)} km/h"),
            if (r.source.hasMap) "Elevation" to "${r.elevGainM.toInt()} m"
            else "Steps" to (r.steps?.takeIf { it > 0 }?.let { String.format(Locale.US, "%,d", it) } ?: "–"),
            "Energy" to (r.kcal?.let { "≈ $it kcal" } ?: "–"),
            "Total time" to Fmt.duration(r.elapsedMs),
        )
        val cols = 3
        val cellW = (w - 2 * pad) / cols
        val rowH = if (inp.story) 150f else 118f
        val gridTop = y + (if (inp.story) 60f else 40f)
        cells.forEachIndexed { i, (label, value) ->
            val cx = pad + (i % cols) * cellW
            val cy = gridTop + (i / cols) * rowH
            text(label, cx, cy, 30f, MUTED, body)
            text(value, cx, cy + 56f, 50f, WHITE, bold)
        }

        val foot = h - (if (inp.story) 150f else 60f)
        text("Fit hone ke liye ghante nahi, minute chahiye.", w / 2f, foot, 36f, MUTED, body, Paint.Align.CENTER)
        return bmp
    }

    /** Fill [area] with the map picture, cropping the edges (like CSS background-size: cover). */
    private fun drawCover(c: Canvas, src: Bitmap, area: RectF) {
        val scale = max(area.width() / src.width, area.height() / src.height)
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate(area.centerX() - src.width * scale / 2f, area.centerY() - src.height * scale / 2f)
        }
        c.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    /** Route scaled to fit [box], keeping its true shape. */
    fun drawRoute(c: Canvas, segments: List<List<LatLng>>, box: RectF, loop: Boolean, thin: Boolean = false) {
        val all = segments.flatten()
        if (all.size < 2) return
        val lat0 = all.sumOf { it.lat } / all.size
        val kx = cos(Math.toRadians(lat0))
        val minX = all.minOf { it.lng * kx }; val maxX = all.maxOf { it.lng * kx }
        val minY = all.minOf { it.lat }; val maxY = all.maxOf { it.lat }
        val spanX = max(maxX - minX, 1e-6); val spanY = max(maxY - minY, 1e-6)
        val s = min(box.width() / spanX, box.height() / spanY)
        val ox = box.centerX() - (spanX * s / 2).toFloat()
        val oy = box.centerY() + (spanY * s / 2).toFloat()
        fun px(p: LatLng) = (ox + (p.lng * kx - minX) * s).toFloat()
        fun py(p: LatLng) = (oy - (p.lat - minY) * s).toFloat()

        val width = if (thin) 6f else 16f
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = width * 2.4f; color = VIOLET; alpha = 70; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = width; color = HALDI; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        for (seg in segments) {
            if (seg.size < 2) continue
            val path = Path()
            seg.forEachIndexed { i, p -> if (i == 0) path.moveTo(px(p), py(p)) else path.lineTo(px(p), py(p)) }
            if (!thin) c.drawPath(path, glow)
            c.drawPath(path, line)
        }
        if (thin) return
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = WHITE }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val a = all.first(); val b = all.last()
        if (loop) {
            fill.color = GREEN; c.drawCircle(px(a), py(a), 22f, fill); c.drawCircle(px(a), py(a), 22f, ring)
        } else {
            fill.color = GREEN; c.drawCircle(px(a), py(a), 20f, fill); c.drawCircle(px(a), py(a), 20f, ring)
            fill.color = RED; c.drawCircle(px(b), py(b), 22f, fill); c.drawCircle(px(b), py(b), 22f, ring)
            fill.color = WHITE; c.drawRect(px(b) - 8f, py(b) - 8f, px(b) + 8f, py(b) + 8f, fill)
        }
    }

    /** Small route picture for the activity list. */
    fun thumbnail(points: List<LatLng>, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), sizePx * 0.22f, sizePx * 0.22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK })
        val inset = sizePx * 0.16f
        drawRoute(c, listOf(points), RectF(inset, inset, sizePx - inset, sizePx - inset), loop = false, thin = true)
        return bmp
    }

    private fun font(context: Context, id: Int, fallback: Typeface): Typeface =
        try { ResourcesCompat.getFont(context, id) ?: fallback } catch (e: Exception) { fallback }

}
