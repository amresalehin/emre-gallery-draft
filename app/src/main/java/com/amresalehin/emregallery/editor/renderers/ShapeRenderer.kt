package com.amresalehin.emregallery.editor.renderers

import android.graphics.*
import androidx.compose.ui.graphics.toArgb
import com.amresalehin.emregallery.editor.ShapeItem
import com.amresalehin.emregallery.editor.ShapeType
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Renders vector geometric and decorative shapes onto canvas.
 */
object ShapeRenderer {

    fun renderShape(canvas: Canvas, width: Float, height: Float, shape: ShapeItem, density: Float) {
        val cx = shape.xRatio * width
        val cy = shape.yRatio * height
        val sw = shape.widthRatio * width
        val sh = shape.heightRatio * height

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (shape.gradientFill != null && shape.gradientFill.size >= 2) {
                shader = LinearGradient(
                    cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f,
                    shape.gradientFill.map { it.toArgb() }.toIntArray(), null, Shader.TileMode.CLAMP
                )
            } else {
                color = shape.fillColor.toArgb()
            }
            style = Paint.Style.FILL
        }

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shape.strokeColor.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = shape.strokeWidth * density
        }

        canvas.save()
        canvas.rotate(shape.rotation, cx, cy)

        val path = Path()
        when (shape.shapeType) {
            ShapeType.RECTANGLE -> {
                val rect = RectF(cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f)
                path.addRoundRect(rect, shape.cornerRadius * density, shape.cornerRadius * density, Path.Direction.CW)
            }
            ShapeType.ROUNDED_RECTANGLE -> {
                val rect = RectF(cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f)
                path.addRoundRect(rect, 24f * density, 24f * density, Path.Direction.CW)
            }
            ShapeType.CIRCLE -> {
                path.addCircle(cx, cy, min(sw, sh) / 2f, Path.Direction.CW)
            }
            ShapeType.TRIANGLE -> {
                path.moveTo(cx, cy - sh / 2f)
                path.lineTo(cx + sw / 2f, cy + sh / 2f)
                path.lineTo(cx - sw / 2f, cy + sh / 2f)
                path.close()
            }
            ShapeType.POLYGON -> {
                val sides = shape.polygonSides.coerceIn(3, 12)
                val r = min(sw, sh) / 2f
                for (i in 0 until sides) {
                    val angle = (i * (360.0 / sides) - 90) * Math.PI / 180.0
                    val x = (cx + r * cos(angle)).toFloat()
                    val y = (cy + r * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            ShapeType.LINE -> {
                path.moveTo(cx - sw / 2f, cy)
                path.lineTo(cx + sw / 2f, cy)
            }
            ShapeType.CURVED_LINE -> {
                path.moveTo(cx - sw / 2f, cy)
                path.quadTo(cx, cy - sh / 2f, cx + sw / 2f, cy)
            }
            ShapeType.STAR -> {
                val rOuter = min(sw, sh) / 2f
                val rInner = rOuter * 0.45f
                for (i in 0 until 10) {
                    val angle = (i * 36 - 90) * Math.PI / 180.0
                    val r = if (i % 2 == 0) rOuter else rInner
                    val x = (cx + r * cos(angle)).toFloat()
                    val y = (cy + r * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            ShapeType.HEART -> {
                val hw = sw / 2f
                val hh = sh / 2f
                path.moveTo(cx, cy + hh * 0.6f)
                path.cubicTo(cx - hw, cy, cx - hw, cy - hh, cx, cy - hh * 0.4f)
                path.cubicTo(cx + hw, cy - hh, cx + hw, cy, cx, cy + hh * 0.6f)
                path.close()
            }
            ShapeType.HEXAGON -> {
                val r = min(sw, sh) / 2f
                for (i in 0 until 6) {
                    val angle = (i * 60) * Math.PI / 180.0
                    val x = (cx + r * cos(angle)).toFloat()
                    val y = (cy + r * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            ShapeType.ARROW -> {
                path.moveTo(cx - sw / 2f, cy - sh * 0.15f)
                path.lineTo(cx + sw * 0.1f, cy - sh * 0.15f)
                path.lineTo(cx + sw * 0.1f, cy - sh * 0.45f)
                path.lineTo(cx + sw / 2f, cy)
                path.lineTo(cx + sw * 0.1f, cy + sh * 0.45f)
                path.lineTo(cx + sw * 0.1f, cy + sh * 0.15f)
                path.lineTo(cx - sw / 2f, cy + sh * 0.15f)
                path.close()
            }
            ShapeType.FREEFORM -> {
                path.moveTo(cx - sw / 2f, cy)
                path.cubicTo(cx - sw / 4f, cy - sh / 2f, cx + sw / 4f, cy + sh / 2f, cx + sw / 2f, cy)
            }
        }

        if (shape.fillColor != androidx.compose.ui.graphics.Color.Transparent || shape.gradientFill != null) {
            canvas.drawPath(path, fillPaint)
        }
        if (shape.strokeWidth > 0f) {
            canvas.drawPath(path, strokePaint)
        }
        canvas.restore()
    }
}
