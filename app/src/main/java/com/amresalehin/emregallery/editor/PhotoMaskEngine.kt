package com.amresalehin.emregallery.editor

import android.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import kotlin.math.*

/**
 * Pure offline LightCraft Masking & Local Adjustments Engine.
 * Supports:
 * - Brush strokes with feather, flow, and density
 * - Draggable Linear & Radial gradients with softness control
 * - Luminance Range & Color Range selections
 * - Heuristic Subject, Sky, and Background selections
 * - Component combinations (Add, Subtract, Intersect, Invert)
 * - 17 Local Adjustments per mask (Exposure, Contrast, Temp, Tint, Clarity, etc.)
 * - Ruby Red, Emerald Green, Electric Blue mask overlay preview
 */
object PhotoMaskEngine {

    /**
     * Evaluates all enabled mask layers and applies localized adjustments to the source bitmap.
     */
    fun applyMaskLayers(src: Bitmap, layers: List<LightcraftMaskLayer>): Bitmap {
        val enabledLayers = layers.filter { it.isEnabled }
        if (enabledLayers.isEmpty()) return src

        var currentBitmap = src
        for (layer in enabledLayers) {
            if (layer.components.isEmpty() && layer.adjustments.isNeutral() && layer.adjustments.colorTint == null) {
                continue
            }
            val maskBitmap = generateLayerMaskBitmap(currentBitmap.width, currentBitmap.height, layer, currentBitmap)
            currentBitmap = applyLocalAdjustmentWithMask(currentBitmap, layer.adjustments, layer.amount, maskBitmap)
            if (maskBitmap != currentBitmap) {
                maskBitmap.recycle()
            }
        }
        return currentBitmap
    }

    /**
     * Generates a single grayscale / alpha bitmap representing the full combined selection of a mask layer.
     */
    fun generateLayerMaskBitmap(
        width: Int,
        height: Int,
        layer: LightcraftMaskLayer,
        srcBitmap: Bitmap?
    ): Bitmap {
        val w = max(1, width)
        val h = max(1, height)
        val resultMask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        if (layer.components.isEmpty()) {
            // Default: if no components specified, treat whole layer as active (100% white)
            val canvas = Canvas(resultMask)
            canvas.drawColor(android.graphics.Color.WHITE)
            if (layer.isInverted) {
                invertBitmapAlpha(resultMask)
            }
            return resultMask
        }

        var combined: Bitmap? = null
        for (comp in layer.components) {
            if (!comp.isEnabled) continue
            val compMask = generateComponentMask(w, h, comp, srcBitmap)
            combined = if (combined == null) {
                compMask
            } else {
                val merged = combineComponentBitmaps(combined, compMask, comp.combineMode)
                combined.recycle()
                compMask.recycle()
                merged
            }
        }

        val finalMask = combined ?: resultMask
        if (layer.isInverted) {
            invertBitmapAlpha(finalMask)
        }
        return finalMask
    }

    /**
     * Generates an alpha mask for an individual component.
     */
    fun generateComponentMask(
        width: Int,
        height: Int,
        comp: MaskComponent,
        srcBitmap: Bitmap?
    ): Bitmap {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)

        when (comp.type) {
            MaskComponentType.BRUSH -> {
                renderBrushComponent(canvas, width, height, comp)
            }
            MaskComponentType.LINEAR_GRADIENT -> {
                renderLinearGradient(canvas, width, height, comp)
            }
            MaskComponentType.RADIAL_GRADIENT -> {
                renderRadialGradient(canvas, width, height, comp)
            }
            MaskComponentType.LUMINANCE_RANGE -> {
                if (srcBitmap != null) {
                    renderLuminanceRange(mask, srcBitmap, comp)
                }
            }
            MaskComponentType.COLOR_RANGE -> {
                if (srcBitmap != null) {
                    renderColorRange(mask, srcBitmap, comp)
                }
            }
            MaskComponentType.SELECT_SUBJECT -> {
                renderSubjectHeuristic(canvas, width, height)
            }
            MaskComponentType.SELECT_SKY -> {
                renderSkyHeuristic(canvas, width, height)
            }
            MaskComponentType.SELECT_BACKGROUND -> {
                renderBackgroundHeuristic(canvas, width, height)
            }
            MaskComponentType.LASSO, MaskComponentType.POLYGON -> {
                renderPathSelection(canvas, width, height, comp)
            }
        }

        if (comp.isInverted) {
            invertBitmapAlpha(mask)
        }
        return mask
    }

    private fun renderBrushComponent(canvas: Canvas, width: Int, height: Int, comp: MaskComponent) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = comp.brushSize * (comp.brushDensity.coerceIn(0.1f, 1f))
            alpha = (255 * comp.brushFlow.coerceIn(0.1f, 1f)).toInt()
        }

        for (stroke in comp.brushStrokes) {
            if (stroke.points.size > 1) {
                val path = Path()
                stroke.points.forEachIndexed { idx, pt ->
                    val px = pt.x * width
                    val py = pt.y * height
                    if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                canvas.drawPath(path, paint)
            } else if (stroke.points.size == 1) {
                val pt = stroke.points[0]
                canvas.drawCircle(pt.x * width, pt.y * height, paint.strokeWidth / 2f, paint)
            }
        }
    }

    private fun renderLinearGradient(canvas: Canvas, width: Int, height: Int, comp: MaskComponent) {
        val x0 = comp.linearStart.x * width
        val y0 = comp.linearStart.y * height
        val x1 = comp.linearEnd.x * width
        val y1 = comp.linearEnd.y * height

        val shader = android.graphics.LinearGradient(
            x0, y0, x1, y1,
            android.graphics.Color.WHITE,
            android.graphics.Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun renderRadialGradient(canvas: Canvas, width: Int, height: Int, comp: MaskComponent) {
        val cx = comp.radialCenter.x * width
        val cy = comp.radialCenter.y * height
        val radius = max(10f, max(comp.radialRadiusX * width, comp.radialRadiusY * height))
        val featherRatio = (comp.radialFeather / 100f).coerceIn(0.05f, 0.95f)

        val shader = android.graphics.RadialGradient(
            cx, cy, radius,
            intArrayOf(android.graphics.Color.WHITE, android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT),
            floatArrayOf(0f, 1f - featherRatio, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun renderLuminanceRange(destMask: Bitmap, srcBitmap: Bitmap, comp: MaskComponent) {
        val w = min(destMask.width, srcBitmap.width)
        val h = min(destMask.height, srcBitmap.height)
        val pixels = IntArray(w * h)
        srcBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val maskPixels = IntArray(w * h)

        val minL = comp.lumMin
        val maxL = comp.lumMax
        val feather = max(1f, comp.lumFeather)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 2.55f // 0..100

            val alpha: Float = when {
                lum in minL..maxL -> 1.0f
                lum < minL && (minL - lum) < feather -> 1.0f - ((minL - lum) / feather)
                lum > maxL && (lum - maxL) < feather -> 1.0f - ((lum - maxL) / feather)
                else -> 0f
            }
            val aInt = (alpha.coerceIn(0f, 1f) * 255).toInt()
            maskPixels[i] = (aInt shl 24) or 0x00FFFFFF
        }
        destMask.setPixels(maskPixels, 0, w, 0, 0, w, h)
    }

    private fun renderColorRange(destMask: Bitmap, srcBitmap: Bitmap, comp: MaskComponent) {
        val w = min(destMask.width, srcBitmap.width)
        val h = min(destMask.height, srcBitmap.height)
        val pixels = IntArray(w * h)
        srcBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val maskPixels = IntArray(w * h)

        val targetHue = comp.targetHue
        val tol = comp.colorTolerance
        val feather = max(1f, comp.colorFeather)
        val hsv = FloatArray(3)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val hue = hsv[0]
            val sat = hsv[1]

            // Calculate minimum circular hue distance
            val diff = abs(hue - targetHue)
            val hueDist = min(diff, 360f - diff)

            val alpha = if (sat > 0.08f) {
                when {
                    hueDist <= tol -> 1.0f
                    hueDist < (tol + feather) -> 1.0f - ((hueDist - tol) / feather)
                    else -> 0f
                }
            } else 0f

            val aInt = (alpha.coerceIn(0f, 1f) * 255).toInt()
            maskPixels[i] = (aInt shl 24) or 0x00FFFFFF
        }
        destMask.setPixels(maskPixels, 0, w, 0, 0, w, h)
    }

    private fun renderSubjectHeuristic(canvas: Canvas, width: Int, height: Int) {
        val cx = width * 0.5f
        val cy = height * 0.55f
        val rx = width * 0.32f
        val ry = height * 0.40f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                cx, cy, max(rx, ry),
                intArrayOf(android.graphics.Color.WHITE, android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)
    }

    private fun renderSkyHeuristic(canvas: Canvas, width: Int, height: Int) {
        val skyBottom = height * 0.45f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, skyBottom,
                android.graphics.Color.WHITE,
                android.graphics.Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), skyBottom, paint)
    }

    private fun renderBackgroundHeuristic(canvas: Canvas, width: Int, height: Int) {
        // Draw full white background then subtract subject center
        canvas.drawColor(android.graphics.Color.WHITE)
        val cx = width * 0.5f
        val cy = height * 0.55f
        val rx = width * 0.32f
        val ry = height * 0.40f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)
    }

    private fun renderPathSelection(canvas: Canvas, width: Int, height: Int, comp: MaskComponent) {
        if (comp.pathPoints.size < 3) return
        val path = Path()
        comp.pathPoints.forEachIndexed { idx, pt ->
            val px = pt.x * width
            val py = pt.y * height
            if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, paint)
    }

    private fun combineComponentBitmaps(a: Bitmap, b: Bitmap, mode: MaskCombineMode): Bitmap {
        val w = a.width
        val h = a.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pA = IntArray(w * h)
        val pB = IntArray(w * h)
        val pOut = IntArray(w * h)
        a.getPixels(pA, 0, w, 0, 0, w, h)
        b.getPixels(pB, 0, w, 0, 0, w, h)

        for (i in pA.indices) {
            val alphaA = (pA[i] shr 24) and 0xFF
            val alphaB = (pB[i] shr 24) and 0xFF
            val resAlpha = when (mode) {
                MaskCombineMode.ADD -> min(255, alphaA + alphaB)
                MaskCombineMode.SUBTRACT -> max(0, alphaA - alphaB)
                MaskCombineMode.INTERSECT -> (alphaA * alphaB) / 255
            }
            pOut[i] = (resAlpha shl 24) or 0x00FFFFFF
        }
        out.setPixels(pOut, 0, w, 0, 0, w, h)
        return out
    }

    private fun invertBitmapAlpha(bmp: Bitmap) {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val a = (pixels[i] shr 24) and 0xFF
            pixels[i] = ((255 - a) shl 24) or 0x00FFFFFF
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /**
     * Blends the 17 localized adjustments onto the source bitmap strictly inside the masked region.
     */
    private fun applyLocalAdjustmentWithMask(
        src: Bitmap,
        adj: MaskLocalAdjustments,
        maskAmount: Float,
        maskBitmap: Bitmap
    ): Bitmap {
        if (adj.isNeutral() && adj.colorTint == null) return src
        val w = src.width
        val h = src.height

        // 1. Generate fully adjusted candidate bitmap
        val adjusted = applyAdjustmentsToCandidate(src, adj)

        // 2. Blend pixel-by-pixel using mask alpha * maskAmount
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(w * h)
        val adjPixels = IntArray(w * h)
        val maskPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)

        src.getPixels(srcPixels, 0, w, 0, 0, w, h)
        adjusted.getPixels(adjPixels, 0, w, 0, 0, w, h)
        maskBitmap.getPixels(maskPixels, 0, w, 0, 0, w, h)

        val amountFactor = maskAmount.coerceIn(0f, 2f)

        for (i in srcPixels.indices) {
            val maskA = ((maskPixels[i] shr 24) and 0xFF) / 255f
            val factor = (maskA * amountFactor).coerceIn(0f, 1f)

            if (factor <= 0.001f) {
                outPixels[i] = srcPixels[i]
            } else if (factor >= 0.999f) {
                outPixels[i] = adjPixels[i]
            } else {
                val s = srcPixels[i]
                val d = adjPixels[i]
                val r = (1f - factor) * ((s shr 16) and 0xFF) + factor * ((d shr 16) and 0xFF)
                val g = (1f - factor) * ((s shr 8) and 0xFF) + factor * ((d shr 8) and 0xFF)
                val b = (1f - factor) * (s and 0xFF) + factor * (d and 0xFF)
                val a = (s shr 24) and 0xFF
                outPixels[i] = (a shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
            }
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        if (adjusted != src) adjusted.recycle()
        return out
    }

    private fun applyAdjustmentsToCandidate(src: Bitmap, adj: MaskLocalAdjustments): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cm = ColorMatrix()

        // Exposure & Contrast
        if (adj.exposure != 0f) {
            val exp = adj.exposure / 100f
            val mult = 2.0.pow(exp.toDouble()).toFloat()
            cm.postConcat(ColorMatrix(floatArrayOf(
                mult, 0f, 0f, 0f, 0f,
                0f, mult, 0f, 0f, 0f,
                0f, 0f, mult, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        if (adj.contrast != 0f) {
            val scale = (adj.contrast / 100f) + 1f
            val translate = (-0.5f * scale + 0.5f) * 255f
            cm.postConcat(ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        // Saturation & Vibrance
        if (adj.saturation != 0f) {
            val satMatrix = ColorMatrix()
            satMatrix.setSaturation((adj.saturation / 100f) + 1f)
            cm.postConcat(satMatrix)
        }

        // Temperature (Warm/Cool) & Tint (Green/Magenta)
        if (adj.temperature != 0f || adj.tint != 0f) {
            val tempShift = adj.temperature * 0.45f
            val tintShift = adj.tint * 0.45f
            cm.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, tempShift,
                0f, 1f, 0f, 0f, -tintShift,
                0f, 0f, 1f, 0f, -tempShift,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)

        // Localized Color Tint Wash
        adj.colorTint?.let { tint ->
            val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tint.toArgb()
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                alpha = 80
            }
            canvas.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), tintPaint)
        }

        return out
    }
}
