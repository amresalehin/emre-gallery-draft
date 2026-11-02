package com.amresalehin.emregallery.editor.renderers

import android.graphics.*
import android.graphics.Color as AndroidColor
import com.amresalehin.emregallery.editor.LensFlareItem
import com.amresalehin.emregallery.editor.LensFlareType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Handles optical lens flare rendering, sunbursts, anamorphic streaks, and light leaks.
 */
object LensFlareRenderer {

    fun renderLensFlare(canvas: Canvas, width: Float, height: Float, flare: LensFlareItem, density: Float) {
        val fx = flare.xRatio * width
        val fy = flare.yRatio * height
        val intensity = flare.intensity

        canvas.save()
        val flarePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (flare.flareType) {
            LensFlareType.SUNBURST, LensFlareType.SUN_RAYS -> {
                flarePaint.shader = RadialGradient(
                    fx, fy, 130f * density * intensity,
                    intArrayOf(AndroidColor.WHITE, AndroidColor.argb((intensity * 180).toInt(), 255, 230, 150), AndroidColor.TRANSPARENT),
                    floatArrayOf(0f, 0.35f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(fx, fy, 130f * density * intensity, flarePaint)

                val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.argb((intensity * 120).toInt(), 255, 240, 200)
                    strokeWidth = 2.5f * density
                    style = Paint.Style.STROKE
                }
                val step = if (flare.flareType == LensFlareType.SUN_RAYS) 15 else 30
                for (deg in 0 until 360 step step) {
                    val rad = deg * Math.PI / 180.0
                    val len = (90f + (deg % 60) * 2f) * density * intensity
                    canvas.drawLine(fx, fy, (fx + len * cos(rad)).toFloat(), (fy + len * sin(rad)).toFloat(), rayPaint)
                }
            }
            LensFlareType.ANAMORPHIC, LensFlareType.ANAMORPHIC_STREAKS -> {
                val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        fx - width * 0.45f, fy, fx + width * 0.45f, fy,
                        intArrayOf(AndroidColor.TRANSPARENT, AndroidColor.argb((intensity * 220).toInt(), 100, 200, 255), AndroidColor.WHITE, AndroidColor.argb((intensity * 220).toInt(), 100, 200, 255), AndroidColor.TRANSPARENT),
                        floatArrayOf(0f, 0.4f, 0.5f, 0.6f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    strokeWidth = 14f * density * intensity
                }
                canvas.drawLine(fx - width * 0.45f, fy, fx + width * 0.45f, fy, streakPaint)
            }
            LensFlareType.GOLDEN_HALO, LensFlareType.GLOW_BLOOM -> {
                val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.argb((intensity * 160).toInt(), 255, 190, 80)
                    style = Paint.Style.STROKE
                    strokeWidth = 5f * density
                }
                canvas.drawCircle(fx, fy, 80f * density * intensity, haloPaint)
                canvas.drawCircle(fx, fy, 140f * density * intensity, haloPaint)
            }
            LensFlareType.RAINBOW_RING, LensFlareType.PRISM_REFRACTION -> {
                val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = SweepGradient(
                        fx, fy,
                        intArrayOf(AndroidColor.RED, AndroidColor.YELLOW, AndroidColor.GREEN, AndroidColor.CYAN, AndroidColor.MAGENTA, AndroidColor.RED),
                        null
                    )
                    style = Paint.Style.STROKE
                    strokeWidth = 8f * density * intensity
                    alpha = (intensity * 140).toInt()
                }
                canvas.drawCircle(fx, fy, 100f * density * intensity, ringPaint)
            }
            LensFlareType.LENS_GHOSTS -> {
                val cx = width / 2f
                val cy = height / 2f
                val dx = cx - fx
                val dy = cy - fy
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    alpha = (intensity * 100).toInt()
                }
                p.color = AndroidColor.argb((intensity * 80).toInt(), 100, 255, 150)
                canvas.drawCircle(cx + dx * 0.3f, cy + dy * 0.3f, 25f * density, p)
                p.color = AndroidColor.argb((intensity * 80).toInt(), 255, 100, 200)
                canvas.drawCircle(cx + dx * 0.6f, cy + dy * 0.6f, 40f * density, p)
                p.color = AndroidColor.argb((intensity * 80).toInt(), 100, 180, 255)
                canvas.drawCircle(cx + dx * 1.1f, cy + dy * 1.1f, 18f * density, p)
            }
            LensFlareType.LIGHT_LEAK -> {
                flarePaint.shader = RadialGradient(
                    0f, 0f, width * 0.7f,
                    intArrayOf(AndroidColor.argb((intensity * 180).toInt(), 255, 120, 50), AndroidColor.TRANSPARENT),
                    floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
                )
                canvas.drawCircle(0f, 0f, width * 0.7f, flarePaint)
            }
            LensFlareType.BOKEH_LIGHTS -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    alpha = (intensity * 120).toInt()
                    style = Paint.Style.FILL
                }
                val random = Random(42)
                for (i in 0 until 12) {
                    val bx = fx + random.nextInt(-150, 150) * density
                    val by = fy + random.nextInt(-150, 150) * density
                    val br = random.nextInt(12, 35) * density * intensity
                    canvas.drawCircle(bx, by, br, p)
                }
            }
            LensFlareType.STAR_FILTER -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.argb((intensity * 200).toInt(), 255, 255, 255)
                    strokeWidth = 2f * density
                }
                val r = 60f * density * intensity
                canvas.drawLine(fx - r, fy, fx + r, fy, p)
                canvas.drawLine(fx, fy - r, fx, fy + r, p)
                canvas.drawLine(fx - r * 0.7f, fy - r * 0.7f, fx + r * 0.7f, fy + r * 0.7f, p)
                canvas.drawLine(fx - r * 0.7f, fy + r * 0.7f, fx + r * 0.7f, fy - r * 0.7f, p)
            }
        }
        canvas.restore()
    }
}
