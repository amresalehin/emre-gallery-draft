package com.amresalehin.emregallery.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a smooth, filled-contour density map for geotagged photos.
 *
 * The density kernel contracts as map zoom increases so the heat layer reveals
 * smaller geographic clusters at city/street level instead of remaining a large
 * screen-space cloud. All calculations stay on-device.
 */
class PhotoHeatmapOverlay(
    private val points: List<GeoPoint>
) : Overlay() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private data class ScreenPoint(val x: Float, val y: Float)

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        super.draw(canvas, mapView, shadow)
        if (shadow || points.isEmpty() || mapView.width <= 0 || mapView.height <= 0) return

        val width = mapView.width
        val height = mapView.height
        val projection = mapView.projection
        val projected = Point()
        val zoom = mapView.zoomLevelDouble.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val zoomFraction = ((zoom - MIN_ZOOM) / (MAX_ZOOM - MIN_ZOOM)).toFloat()
        // Ease the transition to avoid a sudden heatmap size change while pinching.
        val eased = zoomFraction * zoomFraction * (3f - 2f * zoomFraction)
        val kernelRadius = lerp(MAX_KERNEL_RADIUS, MIN_KERNEL_RADIUS, eased)
        val kernelSigma = lerp(MAX_KERNEL_SIGMA, MIN_KERNEL_SIGMA, eased)
        val gridCell = if (zoom >= 14.0) FINE_GRID_CELL else GRID_CELL
        val gridWidth = (width + gridCell - 1) / gridCell
        val gridHeight = (height + gridCell - 1) / gridCell
        if (gridWidth <= 0 || gridHeight <= 0) return

        // Include nearby off-screen samples so density contours remain continuous at edges.
        val margin = kernelRadius
        val screenPoints = points.mapNotNull { point ->
            projection.toPixels(point, projected)
            val x = projected.x.toFloat()
            val y = projected.y.toFloat()
            if (x < -margin || x > width + margin ||
                y < -margin || y > height + margin
            ) null else ScreenPoint(x, y)
        }
        if (screenPoints.isEmpty()) return

        val density = FloatArray(gridWidth * gridHeight)
        val radiusSquared = kernelRadius * kernelRadius
        val supportRadius = kernelRadius * 1.35f
        val supportSquared = supportRadius * supportRadius
        for (point in screenPoints) {
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
                        density[y * gridWidth + x] += kotlin.math.exp(
                            -distanceSquared / (2f * kernelSigma * kernelSigma)
                        )
                    }
                }
            }
        }

        val blurSigma = if (zoom >= 14.0) FINE_BLUR_SIGMA else BLUR_SIGMA
        val smoothed = gaussianBlur(
            density,
            gridWidth,
            gridHeight,
            radius = BLUR_RADIUS,
            sigma = blurSigma
        )
        var peak = 0f
        smoothed.forEach { peak = max(peak, it) }
        if (peak <= 0f) return

        // Relative isopleths make the local shape readable at every zoom. Google
        // Photos-inspired spectral colours are kept translucent to preserve the map.
        val levels = floatArrayOf(0.075f, 0.14f, 0.23f, 0.35f, 0.5f, 0.68f, 0.84f)
        val colors = intArrayOf(
            Color.argb(24, 78, 78, 210),   // blue-violet
            Color.argb(34, 42, 143, 237),  // blue
            Color.argb(46, 0, 205, 238),   // cyan
            Color.argb(62, 43, 210, 143),  // green
            Color.argb(82, 251, 222, 66),  // yellow
            Color.argb(104, 255, 143, 43), // orange
            Color.argb(124, 238, 58, 65)   // red
        )

        val path = Path()
        val cellFloat = gridCell.toFloat()
        for (band in levels.indices) {
            val threshold = peak * levels[band]
            path.reset()
            for (y in 0 until gridHeight) {
                val top = y * cellFloat
                for (x in 0 until gridWidth) {
                    if (smoothed[y * gridWidth + x] >= threshold) {
                        val left = x * cellFloat
                        path.addRect(
                            left, top,
                            left + cellFloat + 0.5f,
                            top + cellFloat + 0.5f,
                            Path.Direction.CW
                        )
                    }
                }
            }
            fillPaint.color = colors[band]
            canvas.drawPath(path, fillPaint)
            if (band > 0) {
                contourPaint.color = Color.argb(16 + band * 3, 54, 65, 110)
                canvas.drawPath(path, contourPaint)
            }
        }
    }

    /** Fast separable Gaussian blur over the compact density grid. */
    private fun gaussianBlur(
        source: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
        sigma: Float
    ): FloatArray {
        val weights = FloatArray(radius * 2 + 1)
        var total = 0f
        for (offset in -radius..radius) {
            val weight = kotlin.math.exp(
                -(offset * offset).toFloat() / (2f * sigma * sigma)
            )
            weights[offset + radius] = weight
            total += weight
        }
        for (index in weights.indices) weights[index] /= total

        val horizontal = FloatArray(source.size)
        val output = FloatArray(source.size)
        for (y in 0 until height) {
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

    private fun lerp(from: Float, to: Float, fraction: Float): Float =
        from + (to - from) * fraction

    private companion object {
        const val MIN_ZOOM = 3.0
        const val MAX_ZOOM = 19.0
        const val GRID_CELL = 8
        const val FINE_GRID_CELL = 4
        const val MAX_KERNEL_RADIUS = 74f
        const val MIN_KERNEL_RADIUS = 12f
        const val MAX_KERNEL_SIGMA = 33f
        const val MIN_KERNEL_SIGMA = 5.5f
        const val BLUR_RADIUS = 3
        const val BLUR_SIGMA = 1.55f
        const val FINE_BLUR_SIGMA = 0.8f
    }
}
