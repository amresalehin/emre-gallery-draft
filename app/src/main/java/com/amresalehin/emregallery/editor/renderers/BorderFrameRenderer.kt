package com.amresalehin.emregallery.editor.renderers

import android.graphics.*
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.toArgb
import com.amresalehin.emregallery.editor.BorderSettings
import com.amresalehin.emregallery.editor.FrameStyle
import kotlin.math.min

/**
 * Renders borders and frames around the canvas.
 */
object BorderFrameRenderer {

    fun renderBorder(canvas: Canvas, width: Float, height: Float, border: BorderSettings) {
        if (border.widthRatio <= 0f) return
        val borderWidth = border.widthRatio * min(width, height)
        val padding = border.paddingRatio * min(width, height)
        val radius = border.cornerRadiusRatio * min(width, height)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = border.color.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = borderWidth
        }
        val inset = padding + borderWidth / 2f
        val rect = RectF(inset, inset, width - inset, height - inset)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
    }

    fun renderFrame(canvas: Canvas, width: Float, height: Float, frame: FrameStyle, density: Float) {
        when (frame) {
            FrameStyle.NONE -> {}
            FrameStyle.POLAROID -> {
                val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    style = Paint.Style.FILL
                }
                val borderSide = width * 0.06f
                val borderTop = height * 0.06f
                val borderBottom = height * 0.18f
                canvas.drawRect(0f, 0f, width, borderTop, cardPaint)
                canvas.drawRect(0f, 0f, borderSide, height, cardPaint)
                canvas.drawRect(width - borderSide, 0f, width, height, cardPaint)
                canvas.drawRect(0f, height - borderBottom, width, height, cardPaint)
            }
            FrameStyle.FILM_STRIP -> {
                val filmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.BLACK }
                val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
                val barW = width * 0.08f
                canvas.drawRect(0f, 0f, barW, height, filmPaint)
                canvas.drawRect(width - barW, 0f, width, height, filmPaint)

                val holeH = 18f * density
                val holeW = barW * 0.5f
                var y = 16f * density
                while (y < height - holeH) {
                    canvas.drawRoundRect(RectF(barW * 0.25f, y, barW * 0.25f + holeW, y + holeH), 4f, 4f, holePaint)
                    canvas.drawRoundRect(RectF(width - barW + barW * 0.25f, y, width - barW * 0.25f, y + holeH), 4f, 4f, holePaint)
                    y += holeH + 14f * density
                }
            }
            FrameStyle.MODERN_WHITE -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = min(width, height) * 0.05f
                }
                val inset = p.strokeWidth / 2f
                canvas.drawRect(inset, inset, width - inset, height - inset, p)
            }
            FrameStyle.DOUBLE_BORDER -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * density
                }
                canvas.drawRect(12f * density, 12f * density, width - 12f * density, height - 12f * density, p)
                canvas.drawRect(22f * density, 22f * density, width - 22f * density, height - 22f * density, p)
            }
            FrameStyle.VINTAGE_FADED, FrameStyle.MINIMAL_BLACK -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (frame == FrameStyle.MINIMAL_BLACK) AndroidColor.BLACK else AndroidColor.DKGRAY
                    style = Paint.Style.STROKE
                    strokeWidth = min(width, height) * 0.04f
                }
                val inset = p.strokeWidth / 2f
                canvas.drawRect(inset, inset, width - inset, height - inset, p)
            }
            FrameStyle.ROUNDED_FRAME -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 16f * density
                }
                canvas.drawRoundRect(RectF(8f * density, 8f * density, width - 8f * density, height - 8f * density), 32f * density, 32f * density, p)
            }
            FrameStyle.DECORATIVE_VINTAGE -> {
                val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.rgb(212, 175, 55)
                    style = Paint.Style.STROKE
                    strokeWidth = 8f * density
                }
                canvas.drawRect(16f * density, 16f * density, width - 16f * density, height - 16f * density, goldPaint)
                goldPaint.strokeWidth = 2f * density
                canvas.drawRect(24f * density, 24f * density, width - 24f * density, height - 24f * density, goldPaint)
            }
            FrameStyle.CAMERA_VIEWFINDER -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * density
                }
                val cornerLen = 24f * density
                val margin = 20f * density
                // 4 viewfinder corners
                canvas.drawLine(margin, margin, margin + cornerLen, margin, p)
                canvas.drawLine(margin, margin, margin, margin + cornerLen, p)
                canvas.drawLine(width - margin, margin, width - margin - cornerLen, margin, p)
                canvas.drawLine(width - margin, margin, width - margin, margin + cornerLen, p)
                canvas.drawLine(margin, height - margin, margin + cornerLen, height - margin, p)
                canvas.drawLine(margin, height - margin, margin, height - margin - cornerLen, p)
                canvas.drawLine(width - margin, height - margin, width - margin - cornerLen, height - margin, p)
                canvas.drawLine(width - margin, height - margin, width - margin, height - margin - cornerLen, p)
            }
            FrameStyle.INSTAX_MINI, FrameStyle.SX70_VINTAGE -> {
                val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(250, 248, 245) }
                val border = width * 0.05f
                val bot = height * 0.22f
                canvas.drawRect(0f, 0f, width, border, cardPaint)
                canvas.drawRect(0f, 0f, border, height, cardPaint)
                canvas.drawRect(width - border, 0f, width, height, cardPaint)
                canvas.drawRect(0f, height - bot, width, height, cardPaint)
            }
            FrameStyle.GRADIENT_BORDER -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, 0f, width, height, intArrayOf(AndroidColor.CYAN, AndroidColor.MAGENTA, AndroidColor.YELLOW), null, Shader.TileMode.CLAMP)
                    style = Paint.Style.STROKE
                    strokeWidth = 10f * density
                }
                val inset = 5f * density
                canvas.drawRect(inset, inset, width - inset, height - inset, p)
            }
            FrameStyle.PATTERN_STITCH -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * density
                    pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
                }
                canvas.drawRect(16f * density, 16f * density, width - 16f * density, height - 16f * density, p)
            }
            FrameStyle.PATTERN_CHAIN -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.rgb(220, 180, 80)
                    style = Paint.Style.STROKE
                    strokeWidth = 4f * density
                    pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
                }
                canvas.drawRect(18f * density, 18f * density, width - 18f * density, height - 18f * density, p)
            }
            FrameStyle.SHADOW_FLOAT -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.argb(120, 0, 0, 0)
                    style = Paint.Style.STROKE
                    strokeWidth = 8f * density
                    maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawRect(16f * density, 16f * density, width - 16f * density, height - 16f * density, p)
            }
        }
    }
}
