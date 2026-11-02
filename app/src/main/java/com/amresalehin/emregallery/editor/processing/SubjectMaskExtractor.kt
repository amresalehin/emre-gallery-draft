package com.amresalehin.emregallery.editor.processing

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Extracts foreground subject masks using luminance and chroma saliency.
 */
object SubjectMaskExtractor {

    fun extractSubjectMask(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val pixels = IntArray(w * h)
        val maskPixels = ByteArray(w * h)
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

                // High contrast and saturation skin/subject heuristic
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val sat = if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f
                val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

                val subjectScore = (centerWeight * 0.65f + sat * 0.25f + (1f - abs(lum - 0.5f)) * 0.1f)
                val alpha = (subjectScore * 255f).toInt().coerceIn(0, 255).toByte()
                maskPixels[idx] = alpha
            }
        }
        mask.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(maskPixels))
        return mask
    }
}
