package com.amresalehin.emregallery.editor.renderers

import android.graphics.*
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.toArgb
import com.amresalehin.emregallery.editor.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Handles artistic brush strokes and layered drawing rendering with blend modes.
 */
object DrawLayerRenderer {

    fun renderDrawLayers(canvas: Canvas, width: Float, height: Float, state: EditSessionState, density: Float) {
        val layers = state.drawLayers.ifEmpty { listOf(DrawLayer("layer_1", "Layer 1")) }
        val allStrokes = state.drawingStrokes
        if (allStrokes.isEmpty()) return

        for (layer in layers) {
            if (!layer.isVisible) continue
            val strokes = allStrokes.filter {
                it.layerId == layer.id || (it.layerId.isEmpty() && layer.id == layers.firstOrNull()?.id)
            }
            if (strokes.isEmpty()) continue

            val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
                xfermode = when (layer.blendMode) {
                    DrawBlendMode.NORMAL -> null
                    DrawBlendMode.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                    DrawBlendMode.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                    DrawBlendMode.OVERLAY -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
                    DrawBlendMode.DARKEN -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
                    DrawBlendMode.LIGHTEN -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
                    DrawBlendMode.ADD -> PorterDuffXfermode(PorterDuff.Mode.ADD)
                }
            }

            val needsLayerSave = layer.opacity < 0.999f || layer.blendMode != DrawBlendMode.NORMAL
            val saveCount = if (needsLayerSave) {
                canvas.saveLayer(0f, 0f, width, height, layerPaint)
            } else {
                -1
            }

            for (stroke in strokes) {
                renderBrushStroke(canvas, stroke, density)
            }

            if (needsLayerSave && saveCount != -1) {
                canvas.restoreToCount(saveCount)
            }
        }
    }

    private fun renderBrushStroke(canvas: Canvas, stroke: DrawingStroke, density: Float) {
        if (stroke.points.size < 2) return
        val path = Path()
        stroke.points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }

        val effectiveAlpha = (stroke.color.alpha * stroke.opacity * 255).toInt().coerceIn(0, 255)
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color.toArgb()
            alpha = effectiveAlpha
            strokeWidth = stroke.width * density
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        if (stroke.hardness < 0.95f) {
            val blurRadius = ((1f - stroke.hardness) * stroke.width * density * 0.45f).coerceAtLeast(1f)
            basePaint.maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
        }

        when (stroke.brushType) {
            BrushType.PEN -> {
                canvas.drawPath(path, basePaint)
            }
            BrushType.CHISEL, BrushType.CALLIGRAPHY -> {
                basePaint.strokeCap = Paint.Cap.SQUARE
                if (stroke.brushType == BrushType.CHISEL) {
                    basePaint.strokeWidth = stroke.width * 1.5f * density
                }
                canvas.drawPath(path, basePaint)
            }
            BrushType.GLOW -> {
                val glowAlpha = (effectiveAlpha * 0.4f).toInt().coerceIn(1, 255)
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stroke.color.toArgb()
                    alpha = glowAlpha
                    strokeWidth = (stroke.width * 2.8f) * density
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    maskFilter = BlurMaskFilter((stroke.width * 0.8f * density).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawPath(path, glowPaint)

                val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stroke.color.toArgb()
                    alpha = (effectiveAlpha * 0.85f).toInt().coerceIn(1, 255)
                    strokeWidth = stroke.width * 1.4f * density
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawPath(path, midPaint)

                val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    alpha = effectiveAlpha
                    strokeWidth = (stroke.width * 0.6f) * density
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawPath(path, corePaint)
            }
            BrushType.SPARKLE -> {
                canvas.drawPath(path, basePaint)
                val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    style = Paint.Style.FILL
                }
                val glowStarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stroke.color.toArgb()
                    alpha = effectiveAlpha
                    style = Paint.Style.STROKE
                    strokeWidth = 2f * density
                }
                val step = max(2, stroke.points.size / 12)
                for (i in stroke.points.indices step step) {
                    val p = stroke.points[i]
                    val s = (stroke.width * 0.8f * density).coerceIn(4f * density, 18f * density)
                    canvas.drawLine(p.x - s, p.y, p.x + s, p.y, glowStarPaint)
                    canvas.drawLine(p.x, p.y - s, p.x, p.y + s, glowStarPaint)
                    canvas.drawCircle(p.x, p.y, s * 0.3f, starPaint)
                }
            }
            BrushType.RAINBOW -> {
                val pts = stroke.points
                val rainbowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    strokeWidth = stroke.width * density
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                for (i in 0 until pts.size - 1) {
                    val p1 = pts[i]
                    val p2 = pts[i + 1]
                    val hue = ((i.toFloat() / max(1, pts.size)) * 360f) % 360f
                    val colorInt = AndroidColor.HSVToColor(effectiveAlpha, floatArrayOf(hue, 1f, 1f))
                    rainbowPaint.color = colorInt
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, rainbowPaint)
                }
            }
            BrushType.HIGHLIGHTER -> {
                basePaint.alpha = (effectiveAlpha * 0.42f).toInt().coerceIn(1, 255)
                basePaint.strokeWidth = stroke.width * 2.2f * density
                basePaint.strokeCap = Paint.Cap.SQUARE
                canvas.drawPath(path, basePaint)
            }
            BrushType.DOTTED -> {
                basePaint.pathEffect = DashPathEffect(floatArrayOf(10f * density, 14f * density), 0f)
                canvas.drawPath(path, basePaint)
            }
            BrushType.ERASER -> {
                basePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                basePaint.strokeWidth = stroke.width * 2f * density
                canvas.drawPath(path, basePaint)
            }
            BrushType.PENCIL -> {
                basePaint.strokeWidth = stroke.width * 0.7f * density
                basePaint.alpha = (effectiveAlpha * 0.82f).toInt().coerceIn(1, 255)
                basePaint.pathEffect = DiscretePathEffect(4f * density, 2f * density)
                canvas.drawPath(path, basePaint)
            }
            BrushType.SOFT_AIRBRUSH, BrushType.BLUR -> {
                basePaint.alpha = (effectiveAlpha * 0.28f).toInt().coerceIn(1, 255)
                basePaint.maskFilter = BlurMaskFilter((stroke.width * density * 0.6f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
                canvas.drawPath(path, basePaint)
            }
            BrushType.HARD_AIRBRUSH -> {
                basePaint.alpha = (effectiveAlpha * 0.65f).toInt().coerceIn(1, 255)
                canvas.drawPath(path, basePaint)
            }
            BrushType.SPRAY_PAINT, BrushType.PIXEL -> {
                val sprayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stroke.color.toArgb()
                    alpha = effectiveAlpha
                    style = Paint.Style.FILL
                }
                val random = Random(42)
                for (p in stroke.points) {
                    val count = (stroke.width * 0.75f).toInt().coerceIn(3, 20)
                    for (c in 0 until count) {
                        val angle = random.nextFloat() * 2f * Math.PI
                        val dist = random.nextFloat() * stroke.width * density * 0.6f
                        val sx = (p.x + dist * cos(angle)).toFloat()
                        val sy = (p.y + dist * sin(angle)).toFloat()
                        val r = (1.5f * density).coerceAtLeast(1f)
                        canvas.drawCircle(sx, sy, r, sprayPaint)
                    }
                }
            }
            BrushType.WATERCOLOR -> {
                basePaint.alpha = (effectiveAlpha * 0.38f).toInt().coerceIn(1, 255)
                basePaint.maskFilter = BlurMaskFilter(4f * density, BlurMaskFilter.Blur.NORMAL)
                canvas.drawPath(path, basePaint)
            }
            BrushType.OIL -> {
                basePaint.strokeWidth = stroke.width * 1.4f * density
                basePaint.pathEffect = DiscretePathEffect(6f * density, 3f * density)
                canvas.drawPath(path, basePaint)
            }
            BrushType.TEXTURE, BrushType.PATTERN, BrushType.SMUDGE, BrushType.SHAPE -> {
                basePaint.alpha = (effectiveAlpha * 0.7f).toInt().coerceIn(1, 255)
                canvas.drawPath(path, basePaint)
            }
        }
    }
}
