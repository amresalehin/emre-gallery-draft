package com.amresalehin.emregallery.editor

import android.graphics.*
import androidx.compose.ui.graphics.toArgb
import kotlin.math.*

/**
 * Pure offline cutout, subject isolation, mask refinement, and realistic subject shadow engine.
 */
object PhotoCutoutEngine {

    /**
     * Extracts subject foreground mask with optional hair refinement and edge softness.
     */
    fun extractSubjectMask(src: Bitmap, personOnly: Boolean, hairRefinement: Boolean): Bitmap {
        val w = src.width
        val h = src.height
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        val maskPixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val cx = w / 2f
        val cy = h / 2f

        for (y in 0 until h) {
            val yOffset = y * w
            for (x in 0 until w) {
                val idx = yOffset + x
                val p = pixels[idx]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                // Central saliency prior
                val dx = (x - cx) / cx
                val dy = (y - cy) / cy
                val distSq = dx * dx + dy * dy
                val centerWeight = (1f - distSq * 0.7f).coerceIn(0f, 1f)

                // Color distinctiveness & saturation
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val sat = if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f
                val lum = (r * 0.299f + g * 0.587f + b * 0.114f) / 255f

                // Skin tone heuristic for person isolation
                val isSkin = personOnly && (r > 95 && g > 40 && b > 20 &&
                        (maxC - minC) > 15 && abs(r - g) > 15 && r > g && r > b)

                val saliency = if (isSkin) {
                    0.95f
                } else {
                    (sat * 0.5f + abs(lum - 0.5f) * 0.3f + centerWeight * 0.6f).coerceIn(0f, 1f)
                }

                // Smooth sigmoid threshold
                val alpha = if (saliency > 0.48f) {
                    val t = ((saliency - 0.48f) / 0.15f).coerceIn(0f, 1f)
                    (t * 255f).toInt()
                } else {
                    0
                }
                maskPixels[idx] = (alpha shl 24) or 0x00FFFFFF
            }
        }

        // Apply hair refinement (boundary alpha preservation) if requested
        if (hairRefinement) {
            refineHairEdges(maskPixels, pixels, w, h)
        }

        mask.setPixels(maskPixels, 0, w, 0, 0, w, h)
        return mask
    }

    private fun refineHairEdges(maskPixels: IntArray, rgb: IntArray, w: Int, h: Int) {
        for (y in 1 until h - 1) {
            val yOffset = y * w
            for (x in 1 until w - 1) {
                val idx = yOffset + x
                val mVal = (maskPixels[idx] shr 24) and 0xFF
                if (mVal in 30..220) {
                    val p = rgb[idx]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val lum = (r * 0.299f + g * 0.587f + b * 0.114f)
                    if (lum < 80 || lum > 200) {
                        val boostedAlpha = (mVal * 1.25f).toInt().coerceIn(0, 255)
                        maskPixels[idx] = (boostedAlpha shl 24) or 0x00FFFFFF
                    }
                }
            }
        }
    }

    /**
     * Applies full cutout and background replacement with optional realistic subject shadow.
     */
    fun applyBackgroundCutout(src: Bitmap, bg: BackgroundSettings): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        var maskBmp = extractSubjectMask(src, bg.personOnly, bg.hairRefinement)

        // Invert mask if specified
        if (bg.invertMask) {
            maskBmp = invertAlphaMask(maskBmp)
        }

        // 1. Draw Background
        when (bg.mode) {
            BackgroundMode.ORIGINAL -> {
                canvas.drawBitmap(src, 0f, 0f, null)
            }
            BackgroundMode.TRANSPARENT -> {
                // Clear background, transparent canvas
            }
            BackgroundMode.SOLID -> {
                canvas.drawColor(bg.solidColor.toArgb())
            }
            BackgroundMode.GRADIENT -> {
                val colors = bg.gradientColors.map { it.toArgb() }.toIntArray()
                val shader = LinearGradient(0f, 0f, 0f, h.toFloat(), colors, null, Shader.TileMode.CLAMP)
                val paint = Paint().apply { this.shader = shader }
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            BackgroundMode.BLUR -> {
                val blurred = PhotoProcessor.applyGaussianBlur(src, bg.blurRadius.toInt().coerceIn(5, 25))
                canvas.drawBitmap(blurred, 0f, 0f, null)
            }
            BackgroundMode.STAGE -> {
                val floorPaint = Paint().apply {
                    shader = LinearGradient(
                        0f, h * 0.6f, 0f, h.toFloat(),
                        intArrayOf(android.graphics.Color.WHITE, android.graphics.Color.LTGRAY),
                        null, Shader.TileMode.CLAMP
                    )
                }
                canvas.drawColor(android.graphics.Color.rgb(240, 240, 245))
                canvas.drawRect(0f, h * 0.6f, w.toFloat(), h.toFloat(), floorPaint)
            }
        }

        // 2. Realistic Subject Drop Shadow
        if (bg.subjectShadow && bg.mode != BackgroundMode.TRANSPARENT && bg.mode != BackgroundMode.ORIGINAL) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(android.graphics.Color.BLACK, PorterDuff.Mode.SRC_IN)
                alpha = (bg.shadowOpacity.coerceIn(0.1f, 1f) * 255).toInt()
            }
            val rad = bg.shadowAngle * Math.PI / 180.0
            val offsetX = (cos(rad) * bg.shadowDistance).toFloat()
            val offsetY = (sin(rad) * bg.shadowDistance).toFloat()
            canvas.drawBitmap(maskBmp, offsetX, offsetY, shadowPaint)
        }

        // 3. Draw Subject Masked
        val fgBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val fgCanvas = Canvas(fgBmp)
        fgCanvas.drawBitmap(src, 0f, 0f, null)

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            alpha = (bg.maskDensity.coerceIn(0.1f, 1f) * 255).toInt()
        }
        fgCanvas.drawBitmap(maskBmp, 0f, 0f, maskPaint)

        canvas.drawBitmap(fgBmp, 0f, 0f, null)
        return out
    }

    private fun invertAlphaMask(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val a = (pixels[i] shr 24) and 0xFF
            pixels[i] = ((255 - a) shl 24) or 0x00FFFFFF
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}
