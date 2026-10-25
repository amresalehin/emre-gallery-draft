package com.amresalehin.emregallery.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Shader
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.floor

/**
 * Draws a Google Maps-inspired density heatmap overlay from photo GPS points.
 */
class PhotoHeatmapOverlay(
    private val points: List<GeoPoint>
) : Overlay() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private data class ScreenPoint(val x: Float, val y: Float)

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        super.draw(canvas, mapView, shadow)
        if (shadow || points.isEmpty() || mapView.width <= 0 || mapView.height <= 0) return

        val projection = mapView.projection
        val projected = Point()
        val radius = 38f
        val neighbourRadius = 42f
        val neighbourRadiusSquared = neighbourRadius * neighbourRadius
        val margin = radius.toInt()

        val screenPoints = points.mapNotNull { point ->
            projection.toPixels(point, projected)
            val x = projected.x.toFloat()
            val y = projected.y.toFloat()
            if (x < -margin || x > mapView.width + margin ||
                y < -margin || y > mapView.height + margin
            ) null else ScreenPoint(x, y)
        }
        if (screenPoints.isEmpty()) return

        val cellSize = neighbourRadius.toInt()
        fun cellKey(x: Int, y: Int): Long =
            (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)

        val buckets = HashMap<Long, MutableList<Int>>()
        screenPoints.forEachIndexed { index, point ->
            val cellX = floor(point.x / cellSize).toInt()
            val cellY = floor(point.y / cellSize).toInt()
            buckets.getOrPut(cellKey(cellX, cellY)) { mutableListOf() }.add(index)
        }

        val densities = IntArray(screenPoints.size)
        screenPoints.forEachIndexed { index, point ->
            val cellX = floor(point.x / cellSize).toInt()
            val cellY = floor(point.y / cellSize).toInt()
            var count = 0
            for (dy in -1..1) {
                for (dx in -1..1) {
                    buckets[cellKey(cellX + dx, cellY + dy)]?.forEach { otherIndex ->
                        val other = screenPoints[otherIndex]
                        val deltaX = point.x - other.x
                        val deltaY = point.y - other.y
                        if (deltaX * deltaX + deltaY * deltaY <= neighbourRadiusSquared) count++
                    }
                }
            }
            densities[index] = count
        }

        fun densityColor(count: Int): Int {
            val stops = intArrayOf(
                Color.rgb(0, 105, 255),
                Color.rgb(0, 220, 255),
                Color.rgb(0, 220, 110),
                Color.rgb(255, 230, 0),
                Color.rgb(255, 135, 0),
                Color.rgb(245, 35, 25)
            )
            val thresholds = intArrayOf(1, 2, 4, 7, 12, 20)
            if (count <= thresholds.first()) return stops.first()
            if (count >= thresholds.last()) return stops.last()
            val upper = thresholds.indexOfFirst { count <= it }
            val lower = upper - 1
            val fraction = (count - thresholds[lower]).toFloat() /
                (thresholds[upper] - thresholds[lower]).toFloat()
            val a = stops[lower]
            val b = stops[upper]
            fun channel(color: Int, shift: Int) = (color shr shift) and 0xff
            val red = (channel(a, 16) + (channel(b, 16) - channel(a, 16)) * fraction).toInt()
            val green = (channel(a, 8) + (channel(b, 8) - channel(a, 8)) * fraction).toInt()
            val blue = (channel(a, 0) + (channel(b, 0) - channel(a, 0)) * fraction).toInt()
            return Color.rgb(red, green, blue)
        }

        screenPoints.forEachIndexed { index, point ->
            val color = densityColor(densities[index])
            paint.shader = RadialGradient(
                point.x,
                point.y,
                radius,
                intArrayOf(
                    Color.argb(150, Color.red(color), Color.green(color), Color.blue(color)),
                    Color.argb(70, Color.red(color), Color.green(color), Color.blue(color)),
                    Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(point.x, point.y, radius, paint)
        }
        paint.shader = null
    }
}
