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


/** Projection is performed on the UI thread; density accumulation happens off-thread. */
internal fun PhotoHeatmapOverlay.projectPoints(mapView: MapView, margin: Float): List<ScreenPoint> {
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

internal fun PhotoHeatmapOverlay.buildRender(
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
        // Bucket points only for bookkeeping while retaining each point's original
        // projected offset. Do not average nearby points into one artificial center.
        val buckets = HashMap<BucketKey, MutableList<ScreenPoint>>()
        for (point in projectedPoints) {
            val cellX = floor(point.x / gridCell).toInt()
            val cellY = floor(point.y / gridCell).toInt()
            buckets.getOrPut(BucketKey(cellX, cellY)) { ArrayList() }.add(point)
        }

        val supportRadius = kernelRadius * KERNEL_SUPPORT_MULTIPLIER
        val supportSquared = supportRadius * supportRadius
        var visited = 0
        for (bucketPoints in buckets.values) {
            if (isCancelled()) return null
            for (point in bucketPoints) {
                val minX = max(0, floor((point.x - supportRadius) / gridCell).toInt())
                val maxX = min(gridWidth - 1, floor((point.x + supportRadius) / gridCell).toInt())
                val minY = max(0, floor((point.y - supportRadius) / gridCell).toInt())
                val maxY = min(gridHeight - 1, floor((point.y + supportRadius) / gridCell).toInt())
                for (y in minY..maxY) {
                    val sampleY = y * gridCell + gridCell / 2f
                    val dy = sampleY - point.y
                    for (x in minX..maxX) {
                        val sampleX = x * gridCell + gridCell / 2f
                        val dx = sampleX - point.x
                        val distanceSquared = dx * dx + dy * dy
                        if (distanceSquared <= supportSquared) {
                            density[y * gridWidth + x] += exp(
                                -distanceSquared / (2f * kernelSigma * kernelSigma)
                            )
                        }
                    }
                }
                visited++
                if (visited % CANCEL_CHECK_BUCKETS == 0 && isCancelled()) return null
            }
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

    if (isCancelled()) {
        bitmap.recycle()
        return null
    }
    return RenderedHeatmap(key, bitmap)
}

/**
 * Nonlinear but fixed density transfer: the reference is a local sum of five
 * photo-kernel contributions, not the strongest sample in the visible viewport.
 */
internal fun PhotoHeatmapOverlay.densityColor(density: Float): Int {
    if (density <= MIN_VISIBLE_DENSITY) return Color.TRANSPARENT
    val intensity = (ln(1f + density) / LN_REFERENCE_DENSITY).coerceIn(0f, 1f)
    val upperIndex = COLOR_STOPS.indexOfFirst { it.first >= intensity }
    val lowerIndex = when {
        upperIndex < 0 -> COLOR_STOPS.lastIndex - 1
        upperIndex <= 0 -> 0
        else -> upperIndex - 1
    }
    val fraction = when {
        upperIndex < 0 -> 1f
        upperIndex <= 0 -> 0f
        else -> {
            val lowerStop = COLOR_STOPS[lowerIndex]
            val upperStop = COLOR_STOPS[upperIndex]
            ((intensity - lowerStop.first) / (upperStop.first - lowerStop.first))
                .coerceIn(0f, 1f)
        }
    }

    val colorA = COLOR_STOPS[lowerIndex].second
    val colorB = COLOR_STOPS[min(lowerIndex + 1, COLOR_STOPS.lastIndex)].second
    fun channel(color: Int, shift: Int) = (color shr shift) and 0xff
    fun lerpChannel(shift: Int): Int =
        (channel(colorA, shift) +
            (channel(colorB, shift) - channel(colorA, shift)) * fraction)
            .roundToInt().coerceIn(0, 255)

    val alphaFraction = ((intensity - ALPHA_START) / (ALPHA_FULL - ALPHA_START)).coerceIn(0f, 1f)
    val alpha = (alphaFraction * MAX_HEAT_ALPHA).roundToInt()
    if (alpha <= 0) return Color.TRANSPARENT
    return Color.argb(alpha, lerpChannel(16), lerpChannel(8), lerpChannel(0))
}

internal fun PhotoHeatmapOverlay.gaussianBlur(
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

internal data class Kernel(val radius: Float, val sigma: Float)

internal fun PhotoHeatmapOverlay.kernelForZoom(zoom: Double): Kernel {
    val fraction = ((zoom - MIN_MAP_ZOOM) / (MAX_MAP_ZOOM - MIN_MAP_ZOOM))
        .toFloat().coerceIn(0f, 1f)
    val eased = fraction * fraction * (3f - 2f * fraction)
    return Kernel(
        lerp(MAX_KERNEL_RADIUS, MIN_KERNEL_RADIUS, eased),
        lerp(MAX_KERNEL_SIGMA, MIN_KERNEL_SIGMA, eased)
    )
}

internal fun PhotoHeatmapOverlay.lerp(from: Float, to: Float, fraction: Float): Float =
    from + (to - from) * fraction

