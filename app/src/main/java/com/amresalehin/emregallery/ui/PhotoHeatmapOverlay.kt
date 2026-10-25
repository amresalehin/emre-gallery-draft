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
 * Draws a smooth, filled-contour (isopleth-style) density map for geotagged photos.
 *
 * The renderer builds a small screen-space density field from projected photo GPS
 * coordinates, smooths it using a separable Gaussian kernel, and fills nested density
 * bands. It works entirely on-device and adapts naturally to map pan and zoom.
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
        val cell = GRID_CELL
        val gridWidth = (width + cell - 1) / cell
        val gridHeight = (height + cell - 1) / cell
        if (gridWidth <= 0 || gridHeight <= 0) return

        // Include nearby off-screen samples so contours remain continuous at edges.
        val margin = KERNEL_RADIUS.toFloat()
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
        for (point in screenPoints) {
            val minX = max(0, floor((point.x - KERNEL_RADIUS) / cell).toInt())
            val maxX = min(gridWidth - 1, floor((point.x + KERNEL_RADIUS) / cell).toInt())
            val minY = max(0, floor((point.y - KERNEL_RADIUS) / cell).toInt())
            val maxY = min(gridHeight - 1, floor((point.y + KERNEL_RADIUS) / cell).toInt())
            for (y in minY..maxY) {
                val sampleY = y * cell + cell / 2f
                val dy = sampleY - point.y
                for (x in minX..maxX) {
                    val sampleX = x * cell + cell / 2f
                    val dx = sampleX - point.x
                    val distanceSquared = dx * dx + dy * dy
                    if (distanceSquared <= KERNEL_RADIUS * KERNEL_RADIUS) {
                        density[y * gridWidth + x] += kotlin.math.exp(
                            -distanceSquared / (2f * KERNEL_SIGMA * KERNEL_SIGMA)
                        )
                    }
                }
            }
        }

        val smoothed = gaussianBlur(density, gridWidth, gridHeight)
        var peak = 0f
        smoothed.forEach { peak = max(peak, it) }
        if (peak <= 0f) return

        // Restrained, mineral-like tones maintain legibility over both light and
        // satellite tiles. Increasing alpha makes the inner, denser bands stronger.
        val levels = floatArrayOf(0.075f, 0.14f, 0.23f, 0.35f, 0.5f, 0.68f, 0.84f)
        val colors = intArrayOf(
            Color.argb(28, 92, 150, 174),
            Color.argb(38, 83, 164, 181),
            Color.argb(52, 76, 174, 167),
            Color.argb(68, 126, 184, 145),
            Color.argb(86, 193, 190, 120),
            Color.argb(106, 211, 156, 101),
            Color.argb(124, 194, 119, 105)
        )

        // Render from the outermost region inward. A path of adjacent grid cells
        // creates filled density regions rather than individual radial point glows.
        val path = Path()
        val cellFloat = cell.toFloat()
        for (band in levels.indices) {
            val threshold = peak * levels[band]
            path.reset()
            for (y in 0 until gridHeight) {
                val top = y * cellFloat
                for (x in 0 until gridWidth) {
                    if (smoothed[y * gridWidth + x] >= threshold) {
                        val left = x * cellFloat
                        path.addRect(left, top, left + cellFloat + 0.5f, top + cellFloat + 0.5f, Path.Direction.CW)
                    }
                }
            }
            fillPaint.color = colors[band]
            canvas.drawPath(path, fillPaint)
            if (band > 0) {
                contourPaint.color = Color.argb(18 + band * 3, 54, 91, 99)
                canvas.drawPath(path, contourPaint)
            }
        }
    }

    /** Fast separable Gaussian blur over the compact density grid. */
    private fun gaussianBlur(source: FloatArray, width: Int, height: Int): FloatArray {
        val radius = BLUR_RADIUS
        val weights = FloatArray(radius * 2 + 1)
        var total = 0f
        for (offset in -radius..radius) {
            val weight = kotlin.math.exp(
                -(offset * offset).toFloat() / (2f * BLUR_SIGMA * BLUR_SIGMA)
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

    private companion object {
        const val GRID_CELL = 8
        const val KERNEL_RADIUS = 52
        const val KERNEL_SIGMA = 23f
        const val BLUR_RADIUS = 3
        const val BLUR_SIGMA = 1.55f
    }
}
