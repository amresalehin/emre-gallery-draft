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
 * A compact screen-space density grid is built from projected photo coordinates and
 * smoothed with a separable Gaussian kernel. Contour bands are then filled from low
 * to high density. Screen-space rendering makes the result naturally adapt to the
 * current map zoom, pan, and viewport without network calls or extra map libraries.
 */
class PhotoHeatmapOverlay(
    private val points: List<GeoPoint>
) : Overlay() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.15f
    }

    private data class ScreenPoint(val x: Float, val y: Float)

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        super.draw(canvas, mapView, shadow)
        if (shadow || points.isEmpty() || mapView.width <= 0 || mapView.height <= 0) return

        val width = mapView.width
        val height = mapView.height
        val projection = mapView.projection
        val projected = Point()
        val padding = KERNEL_RADIUS.toFloat()

        // Keep off-screen points in the calculation when their kernel can still
        // influence the viewport; this prevents visible density from popping at edges.
        val screenPoints = points.mapNotNull { point ->
            projection.toPixels(point, projected)
            val x = projected.x.toFloat()
            val y = projected.y.toFloat()
            if (x < -padding || x > width + padding ||
                y < -padding || y > height + padding
            ) null else ScreenPoint(x, y)
        }
        if (screenPoints.isEmpty()) return

        // An 8 px grid is detailed enough for phone screens while bounding work
        // independently of the number of photos or the device's pixel resolution.
        val cell = GRID_CELL
        val gridWidth = (width + cell - 1) / cell
        val gridHeight = (height + cell - 1) / cell
        val grid = FloatArray(gridWidth * gridHeight)
        screenPoints.forEach { point ->
            val gx = floor(point.x / cell).toInt()
            val gy = floor(point.y / cell).toInt()
            val minX = max(0, gx - KERNEL_RADIUS / cell)
            val maxX = min(gridWidth - 1, gx + KERNEL_RADIUS / cell)
            val minY = max(0, gy - KERNEL_RADIUS / cell)
            val maxY = min(gridHeight - 1, gy + KERNEL_RADIUS / cell)
            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    val dx = (x * cell + cell / 2f) - point.x
                    val dy = (y * cell + cell / 2f) - point.y
                    val distanceSquared = dx * dx + dy * dy
                    if (distanceSquared <= KERNEL_RADIUS * KERNEL_RADIUS) {
                        val weight = kotlin.math.exp(
                            -distanceSquared / (2f * KERNEL_SIGMA * KERNEL_SIGMA)
                        )
                        grid[y * gridWidth + x] += weight
                    }
                }
            }
        }

        val smoothed = gaussianBlur(grid, gridWidth, gridHeight)
        var peak = 0f
        for (value in smoothed) peak = max(peak, value)
        if (peak <= 0f) return

        // Use relative contour levels so a single-photo region remains visible
        // while crowded regions form nested isopleths instead of isolated blobs.
        val levels = floatArrayOf(0.075f, 0.14f, 0.23f, 0.35f, 0.5f, 0.68f, 0.84f)
        val colors = intArrayOf(
            Color.argb(30, 92, 150, 174),
            Color.argb(40, 83, 164, 181),
            Color.argb(54, 76, 174, 167),
            Color.argb(70, 126, 184, 145),
            Color.argb(88, 193, 190, 120),
            Color.argb(108, 211, 156, 101),
            Color.argb(126, 194, 119, 105)
        )

        // Each band is a union of softly joined grid cells. The subtle shared
        // edge treatment gives a readable filled-isopleth look without point glows.
        val path = Path()
        for (band in levels.indices.reversed()) {
            val threshold = peak * levels[band]
            path.reset()
            for (y in 0 until gridHeight) {
                for (x in 0 until gridWidth) {
                    val value = smoothed[y * gridWidth + x]
                    if (value >= threshold) {
                        val left = x * cell.toFloat()
                        val top = y * cell.toFloat()
                        path.addRect(left, top, left + cell + 0.5f, top + cell + 0.5f, Path.Direction.CW)
                    }
                }
            }
            fillPaint.color = colors[band]
            canvas.drawPath(path, fillPaint)

            // Thin, translucent contour edges preserve the scientific map aesthetic.
            if (band > 0) {
                contourPaint.color = Color.argb(24 + band * 3, 54, 91, 99)
                canvas.drawPath(path, contourPaint)
            }
        }
    }

    /**
     * Fast separable Gaussian blur over a small grid; no allocations per cell.
     */
    private fun gaussianBlur(source: FloatArray, width: Int, height: Int): FloatArray {
        val radius = BLUR_RADIUS
        val weights = FloatArray(radius * 2 + 1)
        var weightSum = 0f
        for (offset in -radius..radius) {
            val weight = kotlin.math.exp(
                -(offset * offset).toFloat() / (2f * BLUR_SIGMA * BLUR_SIGMA)
            )
            weights[offset + radius] = weight
            weightSum += weight
        }
        for (i in weights.indices) weights[i] /= weightSum

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
