package com.amresalehin.emregallery.editor.processing

import android.graphics.*
import kotlin.math.max

/**
 * Handles sharpness, Gaussian blur, vignette, and film grain filtering.
 */
object ImageSpatialFilters {

    fun applySharpness(src: Bitmap, intensity: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        val outputPixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val boost = intensity * 1.5f
        for (y in 1 until h - 1) {
            val yOffset = y * w
            for (x in 1 until w - 1) {
                val idx = yOffset + x
                val center = pixels[idx]
                val top = pixels[idx - w]
                val bottom = pixels[idx + w]
                val left = pixels[idx - 1]
                val right = pixels[idx + 1]

                val cR = (center shr 16) and 0xFF
                val cG = (center shr 8) and 0xFF
                val cB = center and 0xFF

                val tR = (top shr 16) and 0xFF
                val tG = (top shr 8) and 0xFF
                val tB = top and 0xFF

                val bR = (bottom shr 16) and 0xFF
                val bG = (bottom shr 8) and 0xFF
                val bB = bottom and 0xFF

                val lR = (left shr 16) and 0xFF
                val lG = (left shr 8) and 0xFF
                val lB = left and 0xFF

                val rR = (right shr 16) and 0xFF
                val rG = (right shr 8) and 0xFF
                val rB = right and 0xFF

                val newR = (cR + boost * (4 * cR - tR - bR - lR - rR)).toInt().coerceIn(0, 255)
                val newG = (cG + boost * (4 * cG - tG - bG - lG - rG)).toInt().coerceIn(0, 255)
                val newB = (cB + boost * (4 * cB - tB - bB - lB - rB)).toInt().coerceIn(0, 255)

                outputPixels[idx] = (0xFF shl 24) or (newR shl 16) or (newG shl 8) or newB
            }
        }
        out.setPixels(outputPixels, 0, w, 0, 0, w, h)
        return out
    }

    fun applyGaussianBlur(src: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return src
        val w = src.width
        val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)

        val r = radius.coerceIn(1, 25)
        val div = 2 * r + 1

        for (y in 0 until h) {
            val yOffset = y * w
            var rSum = 0; var gSum = 0; var bSum = 0
            for (i in -r..r) {
                val p = pixels[yOffset + i.coerceIn(0, w - 1)]
                rSum += (p shr 16) and 0xFF
                gSum += (p shr 8) and 0xFF
                bSum += p and 0xFF
            }
            for (x in 0 until w) {
                val outR = (rSum / div).coerceIn(0, 255)
                val outG = (gSum / div).coerceIn(0, 255)
                val outB = (bSum / div).coerceIn(0, 255)
                pixels[yOffset + x] = (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB

                val pLeft = pixels[yOffset + (x - r).coerceIn(0, w - 1)]
                val pRight = pixels[yOffset + (x + r + 1).coerceIn(0, w - 1)]
                rSum += ((pRight shr 16) and 0xFF) - ((pLeft shr 16) and 0xFF)
                gSum += ((pRight shr 8) and 0xFF) - ((pLeft shr 8) and 0xFF)
                bSum += (pRight and 0xFF) - (pLeft and 0xFF)
            }
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    fun applyVignette(bitmap: Bitmap, intensity: Float) {
        val canvas = Canvas(bitmap)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val radius = max(w, h) * 0.75f

        val shader = RadialGradient(
            w / 2f, h / 2f, radius,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb((intensity * 210).toInt(), 0, 0, 0)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    fun applyFilmGrain(bitmap: Bitmap, intensity: Float) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val random = java.util.Random(42)
        val grainFactor = (intensity * 38f).toInt()

        for (i in pixels.indices) {
            val noise = random.nextInt(grainFactor * 2 + 1) - grainFactor
            val color = pixels[i]
            val a = (color shr 24) and 0xFF
            val r = (((color shr 16) and 0xFF) + noise).coerceIn(0, 255)
            val g = (((color shr 8) and 0xFF) + noise).coerceIn(0, 255)
            val b = ((color and 0xFF) + noise).coerceIn(0, 255)
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }
}
