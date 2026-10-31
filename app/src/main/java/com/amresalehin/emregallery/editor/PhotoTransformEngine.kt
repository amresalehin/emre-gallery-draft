package com.amresalehin.emregallery.editor

import android.graphics.*
import androidx.compose.ui.geometry.Offset
import kotlin.math.*
import kotlin.random.Random

/**
 * Pure offline geometric transformation, warping, selection, and horizon auto-level engine.
 */
object PhotoTransformEngine {

    /**
     * 4-Corner Pin Perspective Warp using Android Matrix.setPolyToPoly.
     */
    fun applyCornerPinPerspective(src: Bitmap, pins: List<CornerPinPoint>): Bitmap {
        if (pins.size != 4) return src
        val w = src.width.toFloat()
        val h = src.height.toFloat()

        val srcPoints = floatArrayOf(
            0f, 0f,       // Top-Left
            w, 0f,        // Top-Right
            w, h,         // Bottom-Right
            0f, h         // Bottom-Left
        )

        val dstPoints = floatArrayOf(
            pins[0].x * w, pins[0].y * h,
            pins[1].x * w, pins[1].y * h,
            pins[2].x * w, pins[2].y * h,
            pins[3].x * w, pins[3].y * h
        )

        val matrix = Matrix()
        val success = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)
        if (!success) return src

        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, matrix, paint)
        return out
    }

    /**
     * Liquify deformation supporting PUSH, PULL, EXPAND, and SHRINK.
     */
    fun applyLiquify(src: Bitmap, points: List<LiquifyPoint>): Bitmap {
        if (points.isEmpty()) return src
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = result.width
        val h = result.height

        for (pt in points) {
            val cx = (pt.x * w).toInt().coerceIn(0, w - 1)
            val cy = (pt.y * h).toInt().coerceIn(0, h - 1)
            val r = (pt.radius.coerceIn(10f, 200f) * (min(w, h) / 500f)).toInt()
            val strength = pt.strength.coerceIn(0.1f, 1.0f)

            val minX = (cx - r).coerceIn(0, w - 1)
            val maxX = (cx + r).coerceIn(0, w - 1)
            val minY = (cy - r).coerceIn(0, h - 1)
            val maxY = (cy + r).coerceIn(0, h - 1)
            val patchW = maxX - minX + 1
            val patchH = maxY - minY + 1
            if (patchW <= 0 || patchH <= 0) continue

            val patchPixels = IntArray(patchW * patchH)
            for (y in 0 until patchH) {
                for (x in 0 until patchW) {
                    val px = minX + x
                    val py = minY + y
                    val dx = px - cx
                    val dy = py - cy
                    val dist = sqrt((dx * dx + dy * dy).toFloat())
                    if (dist <= r) {
                        val normDist = dist / r
                        val factor = when (pt.mode) {
                            LiquifyMode.EXPAND -> 1f - (1f - normDist) * 0.45f * strength
                            LiquifyMode.SHRINK -> 1f + (1f - normDist) * 0.45f * strength
                            LiquifyMode.PUSH -> 1f - (1f - normDist) * 0.25f * strength
                            LiquifyMode.PULL -> 1f + (1f - normDist) * 0.25f * strength
                        }
                        val sampleDist = dist * factor
                        val angle = atan2(dy.toFloat(), dx.toFloat())
                        val sx = (cx + sampleDist * cos(angle)).toInt().coerceIn(0, w - 1)
                        val sy = (cy + sampleDist * sin(angle)).toInt().coerceIn(0, h - 1)
                        patchPixels[y * patchW + x] = result.getPixel(sx, sy)
                    } else {
                        patchPixels[y * patchW + x] = result.getPixel(px, py)
                    }
                }
            }
            result.setPixels(patchPixels, 0, patchW, minX, minY, patchW, patchH)
        }
        return result
    }

    /**
     * Shape Crop presets masking bitmap inside geometric shapes with anti-aliasing.
     */
    fun applyShapeCrop(src: Bitmap, shape: ShapeCropType): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val path = Path()
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f

        when (shape) {
            ShapeCropType.RECTANGLE -> path.addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
            ShapeCropType.CIRCLE -> path.addCircle(cx, cy, r, Path.Direction.CW)
            ShapeCropType.OVAL -> path.addOval(RectF(0f, 0f, w.toFloat(), h.toFloat()), Path.Direction.CW)
            ShapeCropType.TRIANGLE -> {
                path.moveTo(cx, 0f)
                path.lineTo(w.toFloat(), h.toFloat())
                path.lineTo(0f, h.toFloat())
                path.close()
            }
            ShapeCropType.HEART -> {
                path.moveTo(cx, cy + r * 0.65f)
                path.cubicTo(cx - r, cy, cx - r, cy - r, cx, cy - r * 0.4f)
                path.cubicTo(cx + r, cy - r, cx + r, cy, cx, cy + r * 0.65f)
                path.close()
            }
            ShapeCropType.STAR -> {
                val rOuter = r
                val rInner = r * 0.45f
                for (i in 0 until 10) {
                    val angle = (i * 36 - 90) * Math.PI / 180.0
                    val rad = if (i % 2 == 0) rOuter else rInner
                    val x = (cx + rad * cos(angle)).toFloat()
                    val y = (cy + rad * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            ShapeCropType.HEXAGON -> {
                for (i in 0 until 6) {
                    val angle = (i * 60) * Math.PI / 180.0
                    val x = (cx + r * cos(angle)).toFloat()
                    val y = (cy + r * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            ShapeCropType.DIAMOND -> {
                path.moveTo(cx, 0f)
                path.lineTo(w.toFloat(), cy)
                path.lineTo(cx, h.toFloat())
                path.lineTo(0f, cy)
                path.close()
            }
            ShapeCropType.SHIELD -> {
                path.moveTo(0f, 0f)
                path.lineTo(w.toFloat(), 0f)
                path.lineTo(w.toFloat(), cy)
                path.cubicTo(w.toFloat(), h.toFloat() * 0.85f, cx, h.toFloat(), cx, h.toFloat())
                path.cubicTo(cx, h.toFloat(), 0f, h.toFloat() * 0.85f, 0f, cy)
                path.close()
            }
        }

        canvas.clipPath(path)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /**
     * Auto-Level Horizon Straighten:
     * Analyzes image edges using Sobel gradient angles and detects tilted horizon line.
     * Returns tilt angle in degrees (-25..+25).
     */
    fun detectHorizonAngle(src: Bitmap): Float {
        val sampleW = 200
        val sampleH = (src.height * (200f / src.width)).toInt().coerceAtLeast(100)
        val sampled = Bitmap.createScaledBitmap(src, sampleW, sampleH, true)

        val pixels = IntArray(sampleW * sampleH)
        sampled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)

        // Accumulate edge angles in a histogram between -25 and +25 degrees
        val binCount = 51 // -25 to +25
        val histogram = FloatArray(binCount)

        for (y in 2 until sampleH - 2) {
            for (x in 2 until sampleW - 2) {
                // Sobel 3x3 kernels
                val p00 = (pixels[(y - 1) * sampleW + (x - 1)] shr 16) and 0xFF
                val p02 = (pixels[(y - 1) * sampleW + (x + 1)] shr 16) and 0xFF
                val p10 = (pixels[y * sampleW + (x - 1)] shr 16) and 0xFF
                val p12 = (pixels[y * sampleW + (x + 1)] shr 16) and 0xFF
                val p20 = (pixels[(y + 1) * sampleW + (x - 1)] shr 16) and 0xFF
                val p22 = (pixels[(y + 1) * sampleW + (x + 1)] shr 16) and 0xFF

                val gx = (p02 + 2 * p12 + p22) - (p00 + 2 * p10 + p20)

                val p01 = (pixels[(y - 1) * sampleW + x] shr 16) and 0xFF
                val p21 = (pixels[(y + 1) * sampleW + x] shr 16) and 0xFF
                val gy = (p20 + 2 * p21 + p22) - (p00 + 2 * p01 + p02)

                val mag = sqrt((gx * gx + gy * gy).toFloat())
                if (mag > 40f) {
                    // Angle of horizontal edge
                    val angleDeg = (atan2(gy.toFloat(), gx.toFloat()) * 180f / Math.PI).toFloat()
                    val tilt = if (angleDeg in -25f..25f) angleDeg
                    else if (angleDeg in 155f..180f) angleDeg - 180f
                    else if (angleDeg in -180f..-155f) angleDeg + 180f
                    else null

                    if (tilt != null) {
                        val bin = (tilt + 25f).roundToInt().coerceIn(0, binCount - 1)
                        histogram[bin] += mag
                    }
                }
            }
        }

        var maxMag = 0f
        var bestBin = 25 // 0 degrees
        for (i in 0 until binCount) {
            if (histogram[i] > maxMag) {
                maxMag = histogram[i]
                bestBin = i
            }
        }

        val detectedTilt = bestBin - 25f
        return -detectedTilt // Opposite to straighten
    }

    /**
     * Dispersion fragment effect.
     */
    fun applyDispersion(src: Bitmap, amount: Float): Bitmap {
        if (amount <= 0f) return src
        val w = src.width
        val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val random = Random(42)
        val fragmentCount = (amount * 3.5f).toInt()
        val maxSpread = (w * 0.35f * (amount / 100f)).toInt()

        for (i in 0 until fragmentCount) {
            val sx = random.nextInt((w * 0.4f).toInt(), (w * 0.95f).toInt())
            val sy = random.nextInt((h * 0.1f).toInt(), (h * 0.9f).toInt())
            val size = random.nextInt(8, 28)
            val color = src.getPixel(sx, sy)

            paint.color = color
            paint.alpha = random.nextInt(120, 240)

            val dx = sx + random.nextInt(10, maxSpread.coerceAtLeast(12))
            val dy = sy + random.nextInt(-maxSpread / 2, maxSpread / 2)

            val path = Path().apply {
                moveTo(dx.toFloat(), dy.toFloat())
                lineTo(dx.toFloat() + size, dy.toFloat() + size * 0.3f)
                lineTo(dx.toFloat() + size * 0.4f, dy.toFloat() + size)
                close()
            }
            canvas.drawPath(path, paint)
        }
        return out
    }

    /**
     * Motion stretch blur effect.
     */
    fun applyMotion(src: Bitmap, amount: Float, angleDegrees: Float): Bitmap {
        if (amount <= 0f) return src
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val steps = (amount * 0.25f).toInt().coerceIn(3, 16)
        val maxOffset = w * 0.12f * (amount / 100f)
        val rad = angleDegrees * Math.PI / 180.0
        val stepX = (cos(rad) * maxOffset / steps).toFloat()
        val stepY = (sin(rad) * maxOffset / steps).toFloat()

        paint.alpha = (255f / steps * 1.5f).toInt().coerceIn(20, 255)

        for (i in 0 until steps) {
            val ox = stepX * i
            val oy = stepY * i
            canvas.drawBitmap(src, ox, oy, paint)
        }
        return out
    }

    /**
     * Magic Wand connected pixel flood region selection.
     */
    fun computeMagicWandMask(src: Bitmap, tapX: Int, tapY: Int, tolerance: Float): Bitmap {
        val w = src.width
        val h = src.height
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        val maskPixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val startColor = pixels[tapY * w + tapX]
        val sR = (startColor shr 16) and 0xFF
        val sG = (startColor shr 8) and 0xFF
        val sB = startColor and 0xFF

        val tolSq = (tolerance * 2.55f).pow(2)
        val queue = ArrayDeque<Int>()
        val visited = BooleanArray(w * h)

        val startIdx = tapY * w + tapX
        queue.add(startIdx)
        visited[startIdx] = true

        val dx = intArrayOf(1, -1, 0, 0)
        val dy = intArrayOf(0, 0, 1, -1)

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            maskPixels[curr] = 0xFFFFFFFF.toInt()

            val cx = curr % w
            val cy = curr / w

            for (k in 0..3) {
                val nx = cx + dx[k]
                val ny = cy + dy[k]
                if (nx in 0 until w && ny in 0 until h) {
                    val nIdx = ny * w + nx
                    if (!visited[nIdx]) {
                        visited[nIdx] = true
                        val p = pixels[nIdx]
                        val r = (p shr 16) and 0xFF
                        val g = (p shr 8) and 0xFF
                        val b = p and 0xFF
                        val diffSq = (r - sR) * (r - sR) + (g - sG) * (g - sG) + (b - sB) * (b - sB)
                        if (diffSq <= tolSq) {
                            queue.add(nIdx)
                        }
                    }
                }
            }
        }

        mask.setPixels(maskPixels, 0, w, 0, 0, w, h)
        return mask
    }
}
