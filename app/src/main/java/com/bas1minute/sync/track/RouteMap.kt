package com.bas1minute.sync.track

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

/*
 * The map. OpenStreetMap tiles through osmdroid: no API key, no billing account, tiles cached in the app's
 * private storage (they keep working in places you've been, even offline). Only tile images are downloaded;
 * your location is never sent anywhere. To switch to Google Maps later, only this file changes.
 */

/** Lets a screen take a picture of the map (for the share card). */
class MapHandle {
    internal var view: MapView? = null

    /** The map as drawn right now, with route and markers. Null before the map has been laid out. */
    fun snapshot(): Bitmap? {
        val v = view ?: return null
        if (v.width <= 0 || v.height <= 0) return null
        return try {
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        } catch (e: Exception) { null }
    }

    /** Frame the whole route again. */
    fun fit(points: List<LatLng>) { view?.let { fitTo(it, points) } }

    fun center(p: LatLng, zoom: Double? = null) {
        val v = view ?: return
        zoom?.let { v.controller.setZoom(it) }
        v.controller.animateTo(GeoPoint(p.lat, p.lng))
    }
}

object MapSetup {
    @Volatile private var done = false

    fun init(ctx: Context) {
        if (done) return
        val c = Configuration.getInstance()
        c.load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        // OpenStreetMap asks every app to identify itself
        c.userAgentValue = ctx.packageName
        // App-private cache: no storage permission, removed on uninstall
        val base = File(ctx.cacheDir, "osmdroid")
        c.osmdroidBasePath = base
        c.osmdroidTileCache = File(base, "tiles")
        c.tileFileSystemCacheMaxBytes = 120L * 1024 * 1024
        c.tileFileSystemCacheTrimBytes = 100L * 1024 * 1024
        done = true
    }
}

/**
 * A map with the route. [segments]: lines to draw (a pause starts a new one). [current]: live blue dot.
 * [follow]: keep the live dot in the middle; the map calls [onUserMoved] when the person drags it.
 */
@Composable
fun RouteMap(
    segments: List<List<LatLng>>,
    modifier: Modifier = Modifier,
    current: LatLng? = null,
    start: LatLng? = segments.firstOrNull()?.firstOrNull(),
    finish: LatLng? = null,
    loop: Boolean = false,
    follow: Boolean = false,
    fitRoute: Boolean = false,
    dark: Boolean = false,
    interactive: Boolean = true,
    handle: MapHandle? = null,
    onUserMoved: () -> Unit = {},
) {
    val state = remember { MapState() }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapSetup.init(ctx.applicationContext)
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(interactive)
                isClickable = interactive
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                setTilesScaledToDpi(true)
                setMinZoomLevel(3.0)
                setMaxZoomLevel(20.0)
                controller.setZoom(17.0)
                setOnTouchListener { v, e ->
                    if (!interactive) return@setOnTouchListener true
                    // Inside a scrolling screen: the map, not the page, gets the drag
                    if (e.action == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                    if (e.action == MotionEvent.ACTION_MOVE) onUserMoved()
                    false
                }
                overlays.add(state.dots)
                overlays.add(CopyrightOverlay(ctx).apply { setTextSize(10) })
                onResume()
                handle?.view = this
            }
        },
        update = { map ->
            if (dark != state.dark) {
                map.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
                state.dark = dark
            }
            state.setLines(map, segments)
            state.dots.start = start
            state.dots.finish = finish
            state.dots.loop = loop
            state.dots.current = current
            val all = segments.flatten()
            when {
                fitRoute && !state.fitted && all.size >= 2 -> { fitTo(map, all); state.fitted = true }
                follow && current != null -> {
                    if (!state.centered) { map.controller.setZoom(17.5); map.controller.setCenter(GeoPoint(current.lat, current.lng)); state.centered = true }
                    else map.controller.animateTo(GeoPoint(current.lat, current.lng))
                }
                !state.centered && current != null -> {
                    map.controller.setCenter(GeoPoint(current.lat, current.lng)); state.centered = true
                }
            }
            map.invalidate()
        },
        onRelease = { map ->
            if (handle?.view === map) handle.view = null
            map.onPause()
            map.onDetach()
        },
    )
}

private class MapState {
    var dark = false
    var fitted = false
    var centered = false
    val dots = DotsOverlay()
    private val lines = ArrayList<Polyline>()
    private var drawnSizes: List<Int> = emptyList()

    fun setLines(map: MapView, segments: List<List<LatLng>>) {
        val sizes = segments.map { it.size }
        if (sizes == drawnSizes) return
        while (lines.size < segments.size) {
            val glow = Polyline(map).apply {
                setInfoWindow(null)
                outlinePaint.apply { color = 0x553446C8; strokeWidth = 22f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true }
            }
            val line = Polyline(map).apply {
                setInfoWindow(null)
                outlinePaint.apply { color = 0xFF3446C8.toInt(); strokeWidth = 11f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true }
            }
            // Lines go under the start/finish dots
            map.overlays.add(0, line); map.overlays.add(0, glow)
            lines.add(glow); lines.add(line)
        }
        segments.forEachIndexed { i, seg ->
            val pts = seg.map { GeoPoint(it.lat, it.lng) }
            lines[i * 2].setPoints(pts); lines[i * 2 + 1].setPoints(pts)
        }
        for (i in segments.size * 2 until lines.size) lines[i].setPoints(emptyList())
        drawnSizes = sizes
    }
}

/** Start (green), finish (red with white square, or one "loop" marker for a round trip) and the live dot. */
private class DotsOverlay : Overlay() {
    var start: LatLng? = null
    var finish: LatLng? = null
    var loop = false
    var current: LatLng? = null
    private val pt = Point()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFFFFFFF.toInt(); strokeWidth = 6f }

    override fun draw(c: Canvas, map: MapView, shadow: Boolean) {
        if (shadow) return
        val d = map.resources.displayMetrics.density
        fun at(p: LatLng): Point { map.projection.toPixels(GeoPoint(p.lat, p.lng), pt); return pt }
        val s = start
        val f = finish
        if (loop && s != null) {
            val q = at(s)
            dot(c, q, 11 * d, 0xFF0B7A55.toInt())
            fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(q.x.toFloat(), q.y.toFloat(), 4 * d, fill)
        } else {
            if (s != null) dot(c, at(s), 9 * d, 0xFF0B7A55.toInt())
            if (f != null) {
                val q = at(f)
                dot(c, q, 10 * d, 0xFFB42318.toInt())
                fill.color = 0xFFFFFFFF.toInt()
                c.drawRect(q.x - 3.5f * d, q.y - 3.5f * d, q.x + 3.5f * d, q.y + 3.5f * d, fill)
            }
        }
        current?.let { p ->
            val q = at(p)
            fill.color = 0x333446C8; c.drawCircle(q.x.toFloat(), q.y.toFloat(), 20 * d, fill)
            dot(c, q, 8 * d, 0xFF1A73E8.toInt())
        }
    }

    private fun dot(c: Canvas, p: Point, r: Float, color: Int) {
        fill.color = color
        c.drawCircle(p.x.toFloat(), p.y.toFloat(), r, fill)
        c.drawCircle(p.x.toFloat(), p.y.toFloat(), r, ring)
    }
}

private fun fitTo(map: MapView, points: List<LatLng>) {
    if (points.isEmpty()) return
    if (points.size == 1 || points.all { it == points[0] }) {
        map.controller.setZoom(17.0); map.controller.setCenter(GeoPoint(points[0].lat, points[0].lng)); return
    }
    val box = BoundingBox.fromGeoPoints(points.map { GeoPoint(it.lat, it.lng) })
    val pad = (48 * map.resources.displayMetrics.density).toInt()
    if (map.width > 0 && map.height > 0) map.zoomToBoundingBox(box, false, pad)
    else map.addOnFirstLayoutListener { _, _, _, _, _ -> map.zoomToBoundingBox(box, false, pad) }
}
