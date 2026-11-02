package com.amresalehin.emregallery.editor.renderers

import android.graphics.*
import androidx.compose.ui.graphics.toArgb
import com.amresalehin.emregallery.editor.TextAlignment
import com.amresalehin.emregallery.editor.TextFontFamily
import com.amresalehin.emregallery.editor.TextOverlayItem

/**
 * Handles text overlay typography, curvature, shadows, and styling.
 */
object TextOverlayRenderer {

    fun renderTextOverlay(canvas: Canvas, width: Float, height: Float, item: TextOverlayItem, density: Float) {
        val cx = item.xRatio * width
        val cy = item.yRatio * height
        val fontSizePx = item.fontSizeSp * density * item.scale

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontSizePx
            textAlign = when (item.alignment) {
                TextAlignment.LEFT -> Paint.Align.LEFT
                TextAlignment.CENTER -> Paint.Align.CENTER
                TextAlignment.RIGHT -> Paint.Align.RIGHT
            }
            letterSpacing = item.characterSpacing / 10f

            val style = if (item.isBold) Typeface.BOLD else Typeface.NORMAL
            typeface = when (item.fontFamily) {
                TextFontFamily.DEFAULT -> Typeface.create(Typeface.DEFAULT, style)
                TextFontFamily.SANS_SERIF -> Typeface.create(Typeface.SANS_SERIF, style)
                TextFontFamily.SERIF -> Typeface.create(Typeface.SERIF, style)
                TextFontFamily.MONOSPACE -> Typeface.create(Typeface.MONOSPACE, style)
                TextFontFamily.CURSIVE, TextFontFamily.HANDWRITING -> Typeface.create("cursive", style)
                TextFontFamily.DISPLAY, TextFontFamily.TECH -> Typeface.create("sans-serif-black", style)
            }

            if (item.gradientColors != null && item.gradientColors.size >= 2) {
                shader = LinearGradient(
                    cx - 100f, cy, cx + 100f, cy,
                    item.gradientColors.map { it.toArgb() }.toIntArray(), null, Shader.TileMode.CLAMP
                )
            } else {
                color = item.color.toArgb()
            }
        }

        // Shadow or Glow
        if (item.glowColor != null) {
            textPaint.setShadowLayer(item.glowRadius * density, 0f, 0f, item.glowColor.toArgb())
        } else if (item.shadowColor != null) {
            textPaint.setShadowLayer(item.shadowRadius * density, item.shadowOffset.x * density, item.shadowOffset.y * density, item.shadowColor.toArgb())
        }

        canvas.save()
        canvas.rotate(item.rotation, cx, cy)

        if (item.curvedArcDegrees != 0f) {
            // Draw on curved arc path
            val arcPath = Path()
            val radius = width * 0.45f
            val arcRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            arcPath.addArc(arcRect, 180f - item.curvedArcDegrees / 2f, item.curvedArcDegrees)
            canvas.drawTextOnPath(item.text, arcPath, 0f, 0f, textPaint)
        } else {
            val bounds = Rect()
            textPaint.getTextBounds(item.text, 0, item.text.length, bounds)

            // Background box if present
            if (item.backgroundColor.alpha > 0.05f) {
                val padX = 14f * density * item.scale
                val padY = 8f * density * item.scale
                val bgRect = RectF(
                    cx - bounds.width() / 2f - padX,
                    cy - bounds.height() / 2f - padY,
                    cx + bounds.width() / 2f + padX,
                    cy + bounds.height() / 2f + padY
                )
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = item.backgroundColor.toArgb()
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(bgRect, 12f * density, 12f * density, bgPaint)
            }

            // Stroke outline if present
            if (item.strokeWidth > 0f) {
                val strokePaint = Paint(textPaint).apply {
                    shader = null
                    color = item.strokeColor.toArgb()
                    style = Paint.Style.STROKE
                    strokeWidth = item.strokeWidth * density
                }
                canvas.drawText(item.text, cx, cy + bounds.height() / 3f, strokePaint)
            }

            if (!item.isOutlineOnly) {
                canvas.drawText(item.text, cx, cy + bounds.height() / 3f, textPaint)
            }
        }

        canvas.restore()
    }
}
