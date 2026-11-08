package com.amresalehin.emregallery.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Smoothed geographic density overlay for geotagged photos.
 *
 * Rendering uses a cached, filtered density bitmap plus interpolated isopleth lines.
 * The density colour scale is tied to a fixed local-photo-count reference, not the
 * strongest pixel in the current viewport, so one extreme hotspot cannot hide weaker
 * clusters. Expensive grid work runs on a single background worker after the viewport
 * has settled; repeated redraws reuse the cached result.
 */
class PhotoHeatmapOverlay(
    initialPoints: List<GeoPoint> = emptyList()
) : Overlay() {

    @Volatile
    internal var points: List<GeoPoint> = initialPoints
    @Volatile
    internal var pointsVersion: Int = 0
    @Volatile
    internal var renderGeneration: Int = 0
    @Volatile
    internal var cachedRender: RenderedHeatmap? = null
    @Volatile
    internal var activeKey: RenderKey? = null
    internal val renderLock = Any()

    internal val mainHandler = Handler(Looper.getMainLooper())
    internal var pendingKey: RenderKey? = null
    internal var pendingRender: Runnable? = null

    internal val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }

    internal data class ScreenPoint(val x: Float, val y: Float)
    internal data class RenderKey(
        val width: Int,
        val height: Int,
        val zoomBucket: Int,
        val cameraXBucket: Long,
        val cameraYBucket: Long,
        val pointsVersion: Int
    )
    internal data class RenderedHeatmap(val key: RenderKey, val bitmap: Bitmap)
    internal data class BucketKey(val x: Int, val y: Int)

    /**
     * Change photo coordinates without replacing this overlay instance. The caller
     * should pass a stable list (for example, one remembered against locatedMedia).
     */
    fun updatePoints(newPoints: List<GeoPoint>) {
        if (newPoints === points) return
        points = newPoints
        pointsVersion += 1
        renderGeneration += 1
        pendingRender?.let(mainHandler::removeCallbacks)
        pendingRender = null
        pendingKey = null
        activeKey = null
        synchronized(renderLock) {
            cachedRender?.bitmap?.recycle()
            cachedRender = null
        }
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        super.draw(canvas, mapView, shadow)
        if (shadow || points.isEmpty() || mapView.width <= 0 || mapView.height <= 0) return

        val key = createRenderKey(mapView) ?: return
        // Hold the render lock through drawing. updatePoints() recycles the previous bitmap;
        // releasing this lock before Canvas.drawBitmap() allowed a concurrent update to recycle
        // the bitmap after lookup, causing a runtime crash while indexing was updating points.
        val drewCachedBitmap = synchronized(renderLock) {
            val renderedBitmap = cachedRender
                ?.takeIf { it.key == key && !it.bitmap.isRecycled }
                ?.bitmap
            if (renderedBitmap != null) {
                canvas.drawBitmap(
                    renderedBitmap,
                    null,
                    RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat()),
                    bitmapPaint
                )
                true
            } else {
                false
            }
        }
        if (drewCachedBitmap) return

        requestRender(mapView, key)
    }

    /**
     * Key camera positions in world-pixel space. Small sub-pixel movements reuse the
     * cached image; a larger pan, zoom change, size change, or coordinate update rerenders.
     */
    private fun createRenderKey(mapView: MapView): RenderKey? {
        val width = mapView.width
        val height = mapView.height
        if (width <= 0 || height <= 0) return null

        val zoom = mapView.zoomLevelDouble.coerceIn(MIN_MAP_ZOOM, MAX_MAP_ZOOM)
        val zoomBucket = (zoom * ZOOM_KEY_STEPS).roundToInt()
        val keyZoom = zoomBucket.toDouble() / ZOOM_KEY_STEPS
        val center = mapView.projection.fromPixels(width / 2, height / 2)
        val worldSize = TILE_SIZE * 2.0.pow(keyZoom)
        val latitude = center.latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE)
        val sinLatitude = sin(latitude * PI / 180.0)
        val worldX = (center.longitude + 180.0) / 360.0 * worldSize
        val worldY = (0.5 - ln((1.0 + sinLatitude) / (1.0 - sinLatitude)) / (4.0 * PI)) * worldSize

        return RenderKey(
            width = width,
            height = height,
            zoomBucket = zoomBucket,
            cameraXBucket = (worldX / CAMERA_BUCKET_PIXELS).roundToLong(),
            cameraYBucket = (worldY / CAMERA_BUCKET_PIXELS).roundToLong(),
            pointsVersion = pointsVersion
        )
    }

    /**
     * Debounce camera motion before snapshotting projected points. A single worker
     * generates the field, so rapid pan/zoom gestures don't queue expensive stale work.
     */
    private fun requestRender(mapView: MapView, key: RenderKey) {
        if (pendingKey == key || activeKey == key) return

        pendingRender?.let(mainHandler::removeCallbacks)
        pendingKey = key
        renderGeneration += 1
        val generation = renderGeneration
        val mapReference = WeakReference(mapView)
        val task = Runnable {
            val map = mapReference.get() ?: return@Runnable
            val currentKey = createRenderKey(map)
            if (currentKey != key || pointsVersion != key.pointsVersion) {
                if (pendingKey == key) pendingKey = null
                return@Runnable
            }

            pendingKey = null
            activeKey = key
            val zoom = (key.zoomBucket.toDouble() / ZOOM_KEY_STEPS)
                .coerceIn(MIN_MAP_ZOOM, MAX_MAP_ZOOM)
            val kernel = kernelForZoom(zoom)
            val projectedPoints = projectPoints(map, kernel.radius * KERNEL_SUPPORT_MULTIPLIER)
            val snapshotVersion = pointsVersion

            RENDER_EXECUTOR.execute {
                val result = buildRender(
                    key = key,
                    width = key.width,
                    height = key.height,
                    gridCell = if (zoom >= FINE_GRID_ZOOM) FINE_GRID_CELL else GRID_CELL,
                    kernelRadius = kernel.radius,
                    kernelSigma = kernel.sigma,
                    projectedPoints = projectedPoints,
                    generation = generation,
                    expectedVersion = snapshotVersion
                )
                if (result != null &&
                    renderGeneration == generation &&
                    pointsVersion == snapshotVersion
                ) {
                    synchronized(renderLock) {
                        if (renderGeneration == generation && pointsVersion == snapshotVersion) {
                            val previous = cachedRender
                            cachedRender = result
                            if (previous?.bitmap !== result.bitmap) previous?.bitmap?.recycle()
                        } else {
                            result.bitmap.recycle()
                        }
                    }
                    activeKey = null
                    mapReference.get()?.postInvalidate()
                } else {
                    result?.bitmap?.recycle()
                    if (activeKey == key) {
                        activeKey = null
                        mapReference.get()?.postInvalidate()
                    }
                }
            }
        }
        pendingRender = task
        mainHandler.postDelayed(task, RENDER_DEBOUNCE_MS)
    }


    internal companion object {
        const val MIN_MAP_ZOOM = 1.0
        const val MAX_MAP_ZOOM = 19.0
        const val FINE_GRID_ZOOM = 14.0
        const val TILE_SIZE = 256.0
        const val MAX_MERCATOR_LATITUDE = 85.05112878
        const val ZOOM_KEY_STEPS = 10
        const val CAMERA_BUCKET_PIXELS = 4.0
        const val GRID_CELL = 8
        const val FINE_GRID_CELL = 4
        const val MAX_KERNEL_RADIUS = 34f
        const val MIN_KERNEL_RADIUS = 12f
        const val MAX_KERNEL_SIGMA = 12f
        const val MIN_KERNEL_SIGMA = 5.5f
        const val KERNEL_SUPPORT_MULTIPLIER = 2.2f
        const val BLUR_RADIUS = 2
        const val BLUR_SIGMA = 0.8f
        const val FINE_BLUR_SIGMA = 0.65f
        const val RENDER_DEBOUNCE_MS = 80L
        const val MAX_PROJECTED_POINTS_HINT = 4096
        const val CANCEL_CHECK_BUCKETS = 32
        const val PIXEL_CANCEL_CHECK_INTERVAL = 1024
        const val BLUR_CANCEL_CHECK_ROWS = 8
        const val FLOAT_EPSILON = 0.00001f
        const val REFERENCE_DENSITY = 5f
        val LN_REFERENCE_DENSITY = ln(1f + REFERENCE_DENSITY)
        const val MIN_VISIBLE_DENSITY = 0.012f
        const val ALPHA_START = 0.025f
        const val ALPHA_FULL = 0.9f
        const val MAX_HEAT_ALPHA = 154f
        val COLOR_STOPS = listOf(
            0.0f to Color.rgb(116, 87, 217), // violet
            0.18f to Color.rgb(149, 96, 233), // purple
            0.40f to Color.rgb(214, 107, 234), // magenta-pink
            0.62f to Color.rgb(245, 141, 203), // pink
            0.80f to Color.rgb(250, 155, 169), // warm pink
            0.92f to Color.rgb(249, 199, 106), // warm yellow
            1.0f to Color.rgb(255, 231, 123) // pale yellow core
        )
        val RENDER_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "Emre-PhotoHeatmap").apply { isDaemon = true }
        }
    }
}
