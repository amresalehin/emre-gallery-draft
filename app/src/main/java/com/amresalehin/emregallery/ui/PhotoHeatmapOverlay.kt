package com.amresalehin.emregallery.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
import kotlin.math.abs
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
    private var points: List<GeoPoint> = initialPoints
    @Volatile
    private var pointsVersion: Int = 0
    @Volatile
    private var renderGeneration: Int = 0
    @Volatile
    private var cachedRender: RenderedHeatmap? = null
    @Volatile
    private var activeKey: RenderKey? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingKey: RenderKey? = null
    private var pendingRender: Runnable? = null

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }
    private val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.05f
    }

    private data class ScreenPoint(val x: Float, val y: Float)
    private data class RenderKey(
        val width: Int,
        val height: Int,
        val zoomBucket: Int,
        val cameraXBucket: Long,
        val cameraYBucket: Long,
        val pointsVersion: Int
    )
    private data class ContourPath(val path: Path, val color: Int)
    private data class RenderedHeatmap(
        val key: RenderKey,
        val bitmap: Bitmap,
        val contours: List<ContourPath>
    )
    private data class BucketAccumulator(
        var count: Float = 0f,
        var sumX: Float = 0f,
        var sumY: Float = 0f
    )

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
        cachedRender = null
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        super.draw(canvas, mapView, shadow)
        if (shadow || points.isEmpty() || mapView.width <= 0 || mapView.height <= 0) return

        val key = createRenderKey(mapView) ?: return
        val render = cachedRender
        if (render != null && render.key == key) {
            drawRender(canvas, render, mapView.width, mapView.height)
            return
        }

        requestRender(mapView, key)
    }

    private fun drawRender(canvas: Canvas, render: RenderedHeatmap, width: Int, height: Int) {
        canvas.drawBitmap(
            render.bitmap,
            null,
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            bitmapPaint
        )
        for (contour in render.contours) {
            contourPaint.color = contour.color
            canvas.drawPath(contour.path, contourPaint)
        }
    }

    /**
     * Key camera positions in world-pixel space. Small sub-pixel movements reuse the
     * cached image; a larger pan, zoom change, size change, or coordinate update rerenders.
     */
    private fun createRenderKey(mapView: MapView): RenderKey? {
        val width = mapView.width
        val height = mapView.height
        if (width <= 0 || height <= 0) return null

        val zoom = mapView.zoomLevelDouble.coerceIn(MIN_ZOOM, MAX_ZOOM)
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
            val zoom = map.zoomLevelDouble.coerceIn(MIN_ZOOM, MAX_ZOOM)
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
                    cachedRender = result
                    activeKey = null
                    mapReference.get()?.postInvalidate()
                } else if (activeKey == key) {
                    activeKey = null
                    mapReference.get()?.postInvalidate()
                }
            }
        }
        pendingRender = task
        mainHandler.postDelayed(task, RENDER_DEBOUNCE_MS)
    }

    /** Projection is performed on the UI thread; density accumulation happens off-thread. */
    private fun projectPoints(mapView: MapView, margin: Float): List<ScreenPoint> {
        val snapshot = points
        val width = mapView.width
        val height = mapView.height
        val projection = mapView.projection
        val projected = Point()
        val result = ArrayList<ScreenPoint>(min(snapshot.size, MAX_PROJECTED_POINTS_HINT))
        for (point in snapshot) {
            projection.toPixels(point, projected)
            val x = projected.x.toFloat()
            val y = projected.y.toFloat()
            if (x >= -margin && x <= width + margin &&
                y >= -margin && y <= height + margin
            ) {
                result.add(ScreenPoint(x, y))
            }
        }
        return result
    }

    private fun buildRender(
        key: RenderKey,
        width: Int,
        height: Int,
        gridCell: Int,
        kernelRadius: Float,
        kernelSigma: Float,
        projectedPoints: List<ScreenPoint>,
        generation: Int,
        expectedVersion: Int
    ): RenderedHeatmap? {
        fun isCancelled(): Boolean =
            Thread.currentThread().isInterrupted ||
                renderGeneration != generation ||
                pointsVersion != expectedVersion

        val gridWidth = (width + gridCell - 1) / gridCell
        val gridHeight = (height + gridCell - 1) / gridCell
        if (gridWidth <= 0 || gridHeight <= 0) return null

        val density = FloatArray(gridWidth * gridHeight)
        if (projectedPoints.isNotEmpty()) {
            // Aggregate nearby samples into grid buckets first. At world zoom, thousands
            // of photos can occupy the same few pixels; weighting buckets avoids doing
            // the same kernel accumulation for each one individually.
            val buckets = HashMap<Long, BucketAccumulator>()
            for (point in projectedPoints) {
                val bucketX = floor(point.x / gridCell).toInt()
                val bucketY = floor(point.y / gridCell).toInt()
                val bucketKey = (bucketX.toLong() shl 32) xor (bucketY.toLong() and 0xffffffffL)
                val bucket = buckets.getOrPut(bucketKey) { BucketAccumulator() }
                bucket.count += 1f
                bucket.sumX += point.x
                bucket.sumY += point.y
            }

            val supportRadius = kernelRadius * KERNEL_SUPPORT_MULTIPLIER
            val supportSquared = supportRadius * supportRadius
            val radiusInCells = kotlin.math.ceil(supportRadius / gridCell).toInt()
            var visited = 0
            for (bucket in buckets.values) {
                if (isCancelled()) return null
                val sourceX = bucket.sumX / bucket.count
                val sourceY = bucket.sumY / bucket.count
                val centerCellX = floor(sourceX / gridCell).toInt()
                val centerCellY = floor(sourceY / gridCell).toInt()
                val minX = max(0, centerCellX - radiusInCells)
                val maxX = min(gridWidth - 1, centerCellX + radiusInCells)
                val minY = max(0, centerCellY - radiusInCells)
                val maxY = min(gridHeight - 1, centerCellY + radiusInCells)

                for (y in minY..maxY) {
                    val sampleY = y * gridCell + gridCell / 2f
                    val dy = sampleY - sourceY
                    for (x in minX..maxX) {
                        val sampleX = x * gridCell + gridCell / 2f
                        val dx = sampleX - sourceX
                        val distanceSquared = dx * dx + dy * dy
                        if (distanceSquared <= supportSquared) {
                            density[y * gridWidth + x] += bucket.count * exp(
                                -distanceSquared / (2f * kernelSigma * kernelSigma)
                            )
                        }
                    }
                }
                visited++
                if (visited % CANCEL_CHECK_BUCKETS == 0 && isCancelled()) return null
            }
        }

        if (isCancelled()) return null
        val zoom = key.zoomBucket.toDouble() / ZOOM_KEY_STEPS
        val blurSigma = if (zoom >= FINE_GRID_ZOOM) FINE_BLUR_SIGMA else BLUR_SIGMA
        val smoothed = gaussianBlur(
            density,
            gridWidth,
            gridHeight,
            radius = BLUR_RADIUS,
            sigma = blurSigma,
            shouldCancel = ::isCancelled
        ) ?: return null

        val pixels = IntArray(smoothed.size)
        for (index in smoothed.indices) {
            if (index % PIXEL_CANCEL_CHECK_INTERVAL == 0 && isCancelled()) return null
            pixels[index] = densityColor(smoothed[index])
        }
        val bitmap = Bitmap.createBitmap(gridWidth, gridHeight, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, gridWidth, 0, 0, gridWidth, gridHeight)

        val contours = buildContours(smoothed, gridWidth, gridHeight, gridCell, ::isCancelled)
            ?: return null
        if (isCancelled()) return null
        return RenderedHeatmap(key, bitmap, contours)
    }

    /**
     * Nonlinear but fixed density transfer: the reference is a local sum of five
     * photo-kernel contributions, not the strongest sample in the visible viewport.
     */
    private fun densityColor(density: Float): Int {
        if (density <= MIN_VISIBLE_DENSITY) return Color.TRANSPARENT
        val intensity = (ln(1f + density) / LN_REFERENCE_DENSITY).coerceIn(0f, 1f)
        val position = intensity.coerceIn(0f, 1f)
        val upperIndex = COLOR_STOPS.indexOfFirst { it.first >= position }
        val lowerIndex: Int
        val fraction: Float
        if (upperIndex <= 0) {
            lowerIndex = 0
            fraction = 0f
        } else if (upperIndex < 0) {
            lowerIndex = COLOR_STOPS.lastIndex
            fraction = 0f
        } else {
            lowerIndex = upperIndex - 1
            val lowerStop = COLOR_STOPS[lowerIndex]
            val upperStop = COLOR_STOPS[upperIndex]
            fraction = ((position - lowerStop.first) / (upperStop.first - lowerStop.first))
                .coerceIn(0f, 1f)
        }

        val colorA = COLOR_STOPS[lowerIndex].second
        val colorB = COLOR_STOPS[min(lowerIndex + 1, COLOR_STOPS.lastIndex)].second
        fun channelA(color: Int, shift: Int) = (color shr shift) and 0xff
        fun lerpChannel(shift: Int): Int =
            (channelA(colorA, shift) +
                (channelA(colorB, shift) - channelA(colorA, shift)) * fraction).roundToInt().coerceIn(0, 255)

        val alphaFraction = ((intensity - ALPHA_START) / (ALPHA_FULL - ALPHA_START)).coerceIn(0f, 1f)
        val alpha = (alphaFraction * MAX_HEAT_ALPHA).roundToInt()
        if (alpha <= 0) return Color.TRANSPARENT
        return Color.argb(alpha, lerpChannel(16), lerpChannel(8), lerpChannel(0))
    }

    /** Build interpolated marching-squares isopleths at fixed density thresholds. */
    private fun buildContours(
        values: FloatArray,
        width: Int,
        height: Int,
        cellSize: Int,
        isCancelled: () -> Boolean
    ): List<ContourPath>? {
        if (width < 2 || height < 2) return emptyList()
        val result = ArrayList<ContourPath>(CONTOUR_LEVELS.size)
        val xIntersections = FloatArray(4)
        val yIntersections = FloatArray(4)
        for ((levelIndex, threshold) in CONTOUR_LEVELS.withIndex()) {
            val path = Path()
            for (y in 0 until height - 1) {
                if (y % CONTOUR_CANCEL_CHECK_ROWS == 0 && isCancelled()) return null
                val row = y * width
                val nextRow = row + width
                val centerY = y * cellSize + cellSize / 2f
                val nextCenterY = (y + 1) * cellSize + cellSize / 2f
                for (x in 0 until width - 1) {
                    val topLeft = values[row + x]
                    val topRight = values[row + x + 1]
                    val bottomRight = values[nextRow + x + 1]
                    val bottomLeft = values[nextRow + x]
                    var count = 0

                    if ((topLeft >= threshold) != (topRight >= threshold)) {
                        val t = interpolate(topLeft, topRight, threshold)
                        xIntersections[count] = (x + t) * cellSize + cellSize / 2f
                        yIntersections[count] = centerY
                        count++
                    }
                    if ((topRight >= threshold) != (bottomRight >= threshold)) {
                        val t = interpolate(topRight, bottomRight, threshold)
                        xIntersections[count] = (x + 1) * cellSize + cellSize / 2f
                        yIntersections[count] = centerY + t * cellSize
                        count++
                    }
                    if ((bottomRight >= threshold) != (bottomLeft >= threshold)) {
                        val t = interpolate(bottomRight, bottomLeft, threshold)
                        xIntersections[count] = (x + 1 - t) * cellSize + cellSize / 2f
                        yIntersections[count] = nextCenterY
                        count++
                    }
                    if ((bottomLeft >= threshold) != (topLeft >= threshold)) {
                        val t = interpolate(bottomLeft, topLeft, threshold)
                        xIntersections[count] = x * cellSize + cellSize / 2f
                        yIntersections[count] = nextCenterY - t * cellSize
                        count++
                    }

                    when (count) {
                        2 -> {
                            path.moveTo(xIntersections[0], yIntersections[0])
                            path.lineTo(xIntersections[1], yIntersections[1])
                        }
                        4 -> {
                            path.moveTo(xIntersections[0], yIntersections[0])
                            path.lineTo(xIntersections[1], yIntersections[1])
                            path.moveTo(xIntersections[2], yIntersections[2])
                            path.lineTo(xIntersections[3], yIntersections[3])
                        }
                    }
                }
            }
            result.add(
                ContourPath(
                    path,
                    Color.argb(18 + levelIndex * 3, 55, 55, 100)
                )
            )
        }
        return result
    }

    private fun interpolate(a: Float, b: Float, threshold: Float): Float {
        if (abs(b - a) < FLOAT_EPSILON) return 0.5f
        return ((threshold - a) / (b - a)).coerceIn(0f, 1f)
    }

    private fun gaussianBlur(
        source: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
        sigma: Float,
        shouldCancel: () -> Boolean
    ): FloatArray? {
        val weights = FloatArray(radius * 2 + 1)
        var total = 0f
        for (offset in -radius..radius) {
            val weight = exp(-(offset * offset).toFloat() / (2f * sigma * sigma))
            weights[offset + radius] = weight
            total += weight
        }
        for (index in weights.indices) weights[index] /= total

        val horizontal = FloatArray(source.size)
        val output = FloatArray(source.size)
        for (y in 0 until height) {
            if (y % BLUR_CANCEL_CHECK_ROWS == 0 && shouldCancel()) return null
            val row = y * width
            for (x in 0 until width) {
                var value = 0f
                for (offset in -radius..radius) {
                    val sx = (x + offset).coerceIn(0, width - 1)
                    value += source[row + sx] * weights[offset + radius]
                }
                horizontal[row + x] = value
            }
        }
        for (y in 0 until height) {
            if (y % BLUR_CANCEL_CHECK_ROWS == 0 && shouldCancel()) return null
            for (x in 0 until width) {
                var value = 0f
                for (offset in -radius..radius) {
                    val sy = (y + offset).coerceIn(0, height - 1)
                    value += horizontal[sy * width + x] * weights[offset + radius]
                }
                output[y * width + x] = value
            }
        }
        return output
    }

    private data class Kernel(val radius: Float, val sigma: Float)

    private fun kernelForZoom(zoom: Double): Kernel {
        val fraction = ((zoom - MIN_ZOOM) / (MAX_ZOOM - MIN_ZOOM)).toFloat().coerceIn(0f, 1f)
        val eased = fraction * fraction * (3f - 2f * fraction)
        return Kernel(
            lerp(MAX_KERNEL_RADIUS, MIN_KERNEL_RADIUS, eased),
            lerp(MAX_KERNEL_SIGMA, MIN_KERNEL_SIGMA, eased)
        )
    }

    private fun lerp(from: Float, to: Float, fraction: Float): Float =
        from + (to - from) * fraction

    private companion object {
        const val MIN_ZOOM = 3.0
        const val MAX_ZOOM = 19.0
        const val FINE_GRID_ZOOM = 14.0
        const val TILE_SIZE = 256.0
        const val MAX_MERCATOR_LATITUDE = 85.05112878
        const val ZOOM_KEY_STEPS = 10
        const val CAMERA_BUCKET_PIXELS = 4.0
        const val GRID_CELL = 8
        const val FINE_GRID_CELL = 4
        const val MAX_KERNEL_RADIUS = 74f
        const val MIN_KERNEL_RADIUS = 12f
        const val MAX_KERNEL_SIGMA = 33f
        const val MIN_KERNEL_SIGMA = 5.5f
        const val KERNEL_SUPPORT_MULTIPLIER = 1.35f
        const val BLUR_RADIUS = 2
        const val BLUR_SIGMA = 0.8f
        const val FINE_BLUR_SIGMA = 0.65f
        const val RENDER_DEBOUNCE_MS = 80L
        const val MAX_PROJECTED_POINTS_HINT = 4096
        const val CANCEL_CHECK_BUCKETS = 32
        const val PIXEL_CANCEL_CHECK_INTERVAL = 1024
        const val CONTOUR_CANCEL_CHECK_ROWS = 8
        val CONTOUR_LEVELS = floatArrayOf(0.55f, 1.0f, 1.8f, 3.0f, 5.0f)
        const val BLUR_CANCEL_CHECK_ROWS = 8
        const val FLOAT_EPSILON = 0.00001f
        const val REFERENCE_DENSITY = 5f
        val LN_REFERENCE_DENSITY = ln(1f + REFERENCE_DENSITY)
        const val MIN_VISIBLE_DENSITY = 0.012f
        const val ALPHA_START = 0.035f
        const val ALPHA_FULL = 0.85f
        const val MAX_HEAT_ALPHA = 142f
        val COLOR_STOPS = listOf(
            0.0f to Color.rgb(91, 75, 208),   // blue-violet
            0.16f to Color.rgb(41, 111, 230), // blue
            0.33f to Color.rgb(0, 198, 235),  // cyan
            0.50f to Color.rgb(43, 207, 142), // green
            0.67f to Color.rgb(250, 222, 65), // yellow
            0.83f to Color.rgb(255, 140, 44), // orange
            1.0f to Color.rgb(236, 52, 63)    // red
        )
        val RENDER_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "Emre-PhotoHeatmap").apply { isDaemon = true }
        }
    }
}
