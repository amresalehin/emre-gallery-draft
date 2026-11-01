package com.amresalehin.emregallery.editor

import android.graphics.*
import kotlin.math.*

object PhotoEffectsEngine {

    fun applyArtisticEffect(src: Bitmap, effect: FilterType, intensity: Float): Bitmap {
        if (effect == FilterType.NONE || intensity <= 0f) return src
        val factor = intensity.coerceIn(0f, 1f)
        return when (effect) {
            FilterType.OIL_PAINTING -> applyOilPainting(src, factor)
            FilterType.WATERCOLOR -> applyWatercolor(src, factor)
            FilterType.PENCIL_SKETCH, FilterType.SKTCH1 -> applyPencilSketch(src, factor)
            FilterType.CARTOON, FilterType.CARTOON1 -> applyCartoon(src, factor)
            FilterType.CARTOON2, FilterType.ANIMATION -> applyCartoon(src, factor * 0.8f)
            FilterType.INK_CONTOUR -> applyPencilSketch(src, factor)
            FilterType.HALFTONE, FilterType.HALFTONE_DOTS, FilterType.POW -> applyHalftone(src, factor)
            FilterType.EMBOSS -> applyEmboss(src, factor)
            FilterType.MOSAIC, FilterType.POLYGON -> applyMosaic(src, factor)
            FilterType.PIXELATE -> applyPixelate(src, factor)
            FilterType.KALEIDOSCOPE -> applyKaleidoscope(src, factor)
            FilterType.DOUBLE_EXPOSURE -> applyDoubleExposure(src, factor)
            FilterType.CHROMATIC_ABERRATION, FilterType.GLITCH, FilterType.RGB_GLITCH -> applyRgbGlitch(src, factor)
            FilterType.VHS -> applyVhs(src, factor)
            FilterType.LIGHT_LEAK, FilterType.FLARE -> applyLightLeak(src, factor)
            FilterType.BOKEH -> applyBokeh(src, factor)
            FilterType.RADIAL_BLUR, FilterType.FOCAL_ZOOM -> applyRadialBlur(src, factor)
            FilterType.MOTION_BLUR -> applyMotionBlur(src, factor)
            FilterType.BLUR, FilterType.SMART_BLUR -> applyRadialBlur(src, factor * 0.4f)
            FilterType.TEXTURE_OVERLAY, FilterType.CNV1, FilterType.CANVAS1, FilterType.CANVAS2,
            FilterType.GRAINY_CANVAS, FilterType.LINEN, FilterType.PAPER, FilterType.GRUNGE -> applyTextureOverlay(src, factor)
            FilterType.PATTERN_OVERLAY -> applyPatternOverlay(src, factor)
            FilterType.COLOR_HALATION, FilterType.GEODE -> applyColorHalation(src, factor)
            FilterType.SKETCHY -> applyPencilSketch(src, factor * 0.7f)
            FilterType.POSTER -> applyHalftone(src, factor * 0.5f)
            else -> src
        }
    }

    /**
     * Kuwahara-inspired fast oil painting filter.
     */
    fun applyOilPainting(src: Bitmap, factor: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val radius = (3 + (factor * 5).toInt()).coerceIn(2, 8)
        val intensityLevels = 20

        val inPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        src.getPixels(inPixels, 0, w, 0, 0, w, h)

        val intensityCount = IntArray(intensityLevels)
        val avgR = IntArray(intensityLevels)
        val avgG = IntArray(intensityLevels)
        val avgB = IntArray(intensityLevels)

        // Process with stride 1 or 2 for high performance
        val step = if (w * h > 1000000) 2 else 1
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                intensityCount.fill(0)
                avgR.fill(0)
                avgG.fill(0)
                avgB.fill(0)

                for (dy in -radius..radius) {
                    val py = (y + dy).coerceIn(0, h - 1)
                    val row = py * w
                    for (dx in -radius..radius) {
                        val px = (x + dx).coerceIn(0, w - 1)
                        val c = inPixels[row + px]
                        val r = (c shr 16) and 0xFF
                        val g = (c shr 8) and 0xFF
                        val b = c and 0xFF
                        val curIntensity = (((r + g + b) / 3.0f * intensityLevels) / 256.0f).toInt().coerceIn(0, intensityLevels - 1)
                        intensityCount[curIntensity]++
                        avgR[curIntensity] += r
                        avgG[curIntensity] += g
                        avgB[curIntensity] += b
                    }
                }

                var maxCount = 0
                var maxIndex = 0
                for (i in 0 until intensityLevels) {
                    if (intensityCount[i] > maxCount) {
                        maxCount = intensityCount[i]
                        maxIndex = i
                    }
                }

                val finalR = if (maxCount > 0) avgR[maxIndex] / maxCount else 128
                val finalG = if (maxCount > 0) avgG[maxIndex] / maxCount else 128
                val finalB = if (maxCount > 0) avgB[maxIndex] / maxCount else 128
                val finalColor = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB

                outPixels[y * w + x] = finalColor
                if (step == 2) {
                    if (x + 1 < w) outPixels[y * w + x + 1] = finalColor
                    if (y + 1 < h) outPixels[(y + 1) * w + x] = finalColor
                    if (x + 1 < w && y + 1 < h) outPixels[(y + 1) * w + x + 1] = finalColor
                }
            }
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Watercolor simulation: Quantized color wash + dark border bleed.
     */
    fun applyWatercolor(src: Bitmap, factor: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val inPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        src.getPixels(inPixels, 0, w, 0, 0, w, h)

        val quant = (8 + (1f - factor) * 16).toInt() // color banding
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                val c = inPixels[idx]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                val left = inPixels[idx - 1]
                val right = inPixels[idx + 1]
                val top = inPixels[idx - w]
                val bottom = inPixels[idx + w]

                val grad = (abs(r - ((left shr 16) and 0xFF)) +
                        abs(g - ((top shr 8) and 0xFF)) +
                        abs(b - (bottom and 0xFF)))

                // Quantize and bleed
                var qr = (r / quant) * quant
                var qg = (g / quant) * quant
                var qb = (b / quant) * quant

                // Darken along edge outlines for pigment pooling
                if (grad > 35) {
                    val dark = (grad * 0.25f * factor).toInt()
                    qr = (qr - dark).coerceAtLeast(0)
                    qg = (qg - dark).coerceAtLeast(0)
                    qb = (qb - dark).coerceAtLeast(0)
                }

                outPixels[idx] = (0xFF shl 24) or (qr shl 16) or (qg shl 8) or qb
            }
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Pencil Sketch: High-contrast pencil hatching & inverted color dodge.
     */
    fun applyPencilSketch(src: Bitmap, factor: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val inPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        src.getPixels(inPixels, 0, w, 0, 0, w, h)

        // Convert to grayscale & Sobel edge map
        for (y in 1 until h - 1) {
            val yOffset = y * w
            for (x in 1 until w - 1) {
                val idx = yOffset + x
                // 3x3 Sobel
                val p00 = inPixels[idx - w - 1] and 0xFF
                val p01 = inPixels[idx - w] and 0xFF
                val p02 = inPixels[idx - w + 1] and 0xFF
                val p10 = inPixels[idx - 1] and 0xFF
                val p12 = inPixels[idx + 1] and 0xFF
                val p20 = inPixels[idx + w - 1] and 0xFF
                val p21 = inPixels[idx + w] and 0xFF
                val p22 = inPixels[idx + w + 1] and 0xFF

                val gx = (-p00 + p02 - 2 * p10 + 2 * p12 - p20 + p22)
                val gy = (-p00 - 2 * p01 - p02 + p20 + 2 * p21 + p22)
                val edge = sqrt((gx * gx + gy * gy).toFloat())

                // Invert edge: black lines on white paper
                val sketchVal = (255 - (edge * factor * 1.5f).toInt()).coerceIn(0, 255)
                outPixels[idx] = (0xFF shl 24) or (sketchVal shl 16) or (sketchVal shl 8) or sketchVal
            }
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Cartoon / Cel shading.
     */
    fun applyCartoon(src: Bitmap, factor: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val inPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        src.getPixels(inPixels, 0, w, 0, 0, w, h)

        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                val c = inPixels[idx]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                val left = inPixels[idx - 1]
                val right = inPixels[idx + 1]
                val top = inPixels[idx - w]
                val bottom = inPixels[idx + w]

                val edge = abs(r - ((left shr 16) and 0xFF)) +
                        abs(g - ((top shr 8) and 0xFF)) +
                        abs(b - (bottom and 0xFF))

                if (edge > 60 * (1.2f - factor * 0.4f)) {
                    // Black contour
                    outPixels[idx] = (0xFF shl 24) or (20 shl 16) or (20 shl 8) or 20
                } else {
                    // Posterize 4 levels
                    val cr = (r / 64) * 64 + 32
                    val cg = (g / 64) * 64 + 32
                    val cb = (b / 64) * 64 + 32
                    outPixels[idx] = (0xFF shl 24) or (cr shl 16) or (cg shl 8) or cb
                }
            }
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Halftone dot pattern.
     */
    fun applyHalftone(src: Bitmap, factor: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val step = (8 + (1f - factor) * 12).toInt().coerceAtLeast(6)
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val pixel = src.getPixel(x.coerceIn(0, w - 1), y.coerceIn(0, h - 1))
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val brightness = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                val radius = (step / 2f) * (1f - brightness) * factor
                if (radius > 0.5f) {
                    canvas.drawCircle(x + step / 2f, y + step / 2f, radius, dotPaint)
                }
            }
        }
        return out
    }

    /**
     * 3D Emboss filter.
     */
    fun applyEmboss(src: Bitmap, factor: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val inPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        src.getPixels(inPixels, 0, w, 0, 0, w, h)

        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                val tl = inPixels[idx - w - 1]
                val br = inPixels[idx + w + 1]

                val tlGray = ((tl shr 16 and 0xFF) + (tl shr 8 and 0xFF) + (tl and 0xFF)) / 3
                val brGray = ((br shr 16 and 0xFF) + (br shr 8 and 0xFF) + (br and 0xFF)) / 3

                val diff = (tlGray - brGray) * factor * 2f
                val v = (128 + diff).toInt().coerceIn(0, 255)
                outPixels[idx] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Mosaic tiled effect.
     */
    fun applyMosaic(src: Bitmap, factor: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val tileSize = (12 + (factor * 32).toInt()).coerceAtLeast(4)

        val inPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        src.getPixels(inPixels, 0, w, 0, 0, w, h)

        for (ty in 0 until h step tileSize) {
            val yEnd = (ty + tileSize).coerceAtMost(h)
            for (tx in 0 until w step tileSize) {
                val xEnd = (tx + tileSize).coerceAtMost(w)

                // Compute tile average
                var rSum = 0; var gSum = 0; var bSum = 0; var count = 0
                for (y in ty until yEnd) {
                    val rOffset = y * w
                    for (x in tx until xEnd) {
                        val p = inPixels[rOffset + x]
                        rSum += (p shr 16) and 0xFF
                        gSum += (p shr 8) and 0xFF
                        bSum += p and 0xFF
                        count++
                    }
                }
                if (count == 0) continue
                val avgColor = (0xFF shl 24) or ((rSum / count) shl 16) or ((gSum / count) shl 8) or (bSum / count)

                // Fill block with subtle grout edge
                for (y in ty until yEnd) {
                    val rOffset = y * w
                    for (x in tx until xEnd) {
                        if (x == tx || y == ty) {
                            // Dark tile grout line
                            outPixels[rOffset + x] = 0xFF222222.toInt()
                        } else {
                            outPixels[rOffset + x] = avgColor
                        }
                    }
                }
            }
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Pixel block effect.
     */
    fun applyPixelate(src: Bitmap, factor: Float): Bitmap {
        val w = src.width
        val h = src.height
        val blockSize = (8 + (factor * 36).toInt()).coerceAtLeast(2)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val inPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        src.getPixels(inPixels, 0, w, 0, 0, w, h)

        for (by in 0 until h step blockSize) {
            val yEnd = (by + blockSize).coerceAtMost(h)
            for (bx in 0 until w step blockSize) {
                val xEnd = (bx + blockSize).coerceAtMost(w)
                val sample = inPixels[by * w + bx]

                for (y in by until yEnd) {
                    val row = y * w
                    for (x in bx until xEnd) {
                        outPixels[row + x] = sample
                    }
                }
            }
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Symmetrical 8-segment kaleidoscope reflection.
     */
    fun applyKaleidoscope(src: Bitmap, factor: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cx = w / 2f
        val cy = h / 2f

        val inPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        src.getPixels(inPixels, 0, w, 0, 0, w, h)

        val segments = 8
        val anglePerSegment = ((2.0 * Math.PI) / segments).toFloat()
        val twoPi = (2.0 * Math.PI).toFloat()

        for (y in 0 until h) {
            val dy = y - cy
            val row = y * w
            for (x in 0 until w) {
                val dx = x - cx
                val r = sqrt(dx * dx + dy * dy)
                var theta = atan2(dy, dx)
                if (theta < 0f) theta += twoPi

                var modTheta = theta % anglePerSegment
                if (modTheta > anglePerSegment / 2f) {
                    modTheta = anglePerSegment - modTheta
                }

                val sx = (cx + r * cos(modTheta)).toInt().coerceIn(0, w - 1)
                val sy = (cy + r * sin(modTheta)).toInt().coerceIn(0, h - 1)
                outPixels[row + x] = inPixels[sy * w + sx]
            }
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Chromatic Aberration: Red / Blue horizontal displacement.
     */
    fun applyChromaticAberration(src: Bitmap, factor: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val shift = (factor * 28).toInt().coerceAtLeast(2)

        val inPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        src.getPixels(inPixels, 0, w, 0, 0, w, h)

        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val rX = (x + shift).coerceIn(0, w - 1)
                val bX = (x - shift).coerceIn(0, w - 1)

                val redVal = (inPixels[row + rX] shr 16) and 0xFF
                val greenVal = (inPixels[row + x] shr 8) and 0xFF
                val blueVal = inPixels[row + bX] and 0xFF

                outPixels[row + x] = (0xFF shl 24) or (redVal shl 16) or (greenVal shl 8) or blueVal
            }
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * RGB Glitch: Digital horizontal tear displacements.
     */
    fun applyRgbGlitch(src: Bitmap, factor: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val inPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        src.getPixels(inPixels, 0, w, 0, 0, w, h)

        val random = java.util.Random(1337)
        var glitchOffset = 0
        var glitchCountdown = 0

        for (y in 0 until h) {
            if (glitchCountdown <= 0) {
                if (random.nextFloat() < (0.12f * factor)) {
                    glitchOffset = (random.nextInt(40) - 20)
                    glitchCountdown = random.nextInt(12) + 4
                } else {
                    glitchOffset = 0
                }
            } else {
                glitchCountdown--
            }

            val row = y * w
            for (x in 0 until w) {
                val targetX = (x + glitchOffset).coerceIn(0, w - 1)
                val color = inPixels[row + targetX]

                if (glitchOffset != 0 && random.nextFloat() < 0.2f) {
                    // Shift green or blue
                    val r = (color shr 16) and 0xFF
                    val g = 0
                    val b = (color and 0xFF)
                    outPixels[row + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                } else {
                    outPixels[row + x] = color
                }
            }
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * VHS analog video simulation: scanlines + chroma drift + noise.
     */
    fun applyVhs(src: Bitmap, factor: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val inPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        src.getPixels(inPixels, 0, w, 0, 0, w, h)
        val random = java.util.Random(42)

        for (y in 0 until h) {
            val isScanline = (y % 4 == 0)
            val row = y * w
            val drift = if (y in (h * 0.7).toInt()..(h * 0.75).toInt()) ((sin(y * 0.2) * 16 * factor).toInt()) else 0

            for (x in 0 until w) {
                val srcX = (x + drift).coerceIn(0, w - 1)
                val c = inPixels[row + srcX]
                var r = (c shr 16) and 0xFF
                var g = (c shr 8) and 0xFF
                var b = c and 0xFF

                if (isScanline) {
                    val dim = (35 * factor).toInt()
                    r = (r - dim).coerceAtLeast(0)
                    g = (g - dim).coerceAtLeast(0)
                    b = (b - dim).coerceAtLeast(0)
                }

                // Add analog noise grain
                val noise = ((random.nextInt(25) - 12) * factor).toInt()
                r = (r + noise).coerceIn(0, 255)
                g = (g + noise).coerceIn(0, 255)
                b = (b + noise).coerceIn(0, 255)

                outPixels[row + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Warm organic Light Leak.
     */
    fun applyLightLeak(src: Bitmap, factor: Float): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.1f, h * 0.1f, w * 0.85f,
                intArrayOf(
                    Color.argb((factor * 220).toInt(), 255, 120, 60),
                    Color.argb((factor * 160).toInt(), 255, 60, 140),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        canvas.drawRect(0f, 0f, w, h, paint)
        return out
    }

    /**
     * Bokeh lights scatter.
     */
    fun applyBokeh(src: Bitmap, factor: Float): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()

        val random = java.util.Random(999)
        val count = (18 * factor).toInt().coerceAtLeast(5)

        for (i in 0 until count) {
            val cx = random.nextFloat() * w
            val cy = random.nextFloat() * h
            val radius = (20f + random.nextFloat() * 60f) * factor
            val alpha = (80 + random.nextInt(120)) * factor

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alpha.toInt(), 255, 240, 200)
                style = Paint.Style.FILL
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            }
            canvas.drawCircle(cx, cy, radius, paint)

            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((alpha * 1.3f).toInt().coerceAtMost(255), 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            }
            canvas.drawCircle(cx, cy, radius, ringPaint)
        }
        return out
    }

    /**
     * Radial / Zoom blur radiating from center.
     */
    fun applyRadialBlur(src: Bitmap, factor: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cx = w / 2f
        val cy = h / 2f
        val steps = (6 + (factor * 12).toInt()).coerceIn(4, 16)
        val inPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        src.getPixels(inPixels, 0, w, 0, 0, w, h)

        for (y in 0 until h) {
            val dy = y - cy
            val row = y * w
            for (x in 0 until w) {
                val dx = x - cx
                var rSum = 0; var gSum = 0; var bSum = 0

                for (s in 0 until steps) {
                    val scale = 1f - (s.toFloat() / steps) * 0.18f * factor
                    val sx = (cx + dx * scale).toInt().coerceIn(0, w - 1)
                    val sy = (cy + dy * scale).toInt().coerceIn(0, h - 1)
                    val p = inPixels[sy * w + sx]
                    rSum += (p shr 16) and 0xFF
                    gSum += (p shr 8) and 0xFF
                    bSum += p and 0xFF
                }

                outPixels[row + x] = (0xFF shl 24) or ((rSum / steps) shl 16) or ((gSum / steps) shl 8) or (bSum / steps)
            }
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Motion Blur: Directional linear filter.
     */
    fun applyMotionBlur(src: Bitmap, factor: Float, angleDegrees: Float = 0f): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val radius = (4 + (factor * 24).toInt()).coerceIn(2, 30)
        val rad = angleDegrees * Math.PI / 180.0
        val cosA = cos(rad)
        val sinA = sin(rad)

        val inPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        src.getPixels(inPixels, 0, w, 0, 0, w, h)

        val samples = radius * 2 + 1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var rSum = 0; var gSum = 0; var bSum = 0
                for (i in -radius..radius) {
                    val sx = (x + i * cosA).toInt().coerceIn(0, w - 1)
                    val sy = (y + i * sinA).toInt().coerceIn(0, h - 1)
                    val p = inPixels[sy * w + sx]
                    rSum += (p shr 16) and 0xFF
                    gSum += (p shr 8) and 0xFF
                    bSum += p and 0xFF
                }
                outPixels[row + x] = (0xFF shl 24) or ((rSum / samples) shl 16) or ((gSum / samples) shl 8) or (bSum / samples)
            }
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Artistic Double Exposure.
     */
    fun applyDoubleExposure(src: Bitmap, factor: Float): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()

        // Generate artistic high-contrast cloud/galaxy horizon exposure layer
        val gradient = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(
                Color.argb((factor * 180).toInt(), 30, 144, 255),
                Color.argb((factor * 140).toInt(), 255, 105, 180),
                Color.argb((factor * 180).toInt(), 255, 215, 0)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        canvas.drawRect(0f, 0f, w, h, paint)
        return out
    }

    /**
     * Film / Paper Texture overlay.
     */
    fun applyTextureOverlay(src: Bitmap, factor: Float): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width
        val h = out.height
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)

        val random = java.util.Random(555)
        val intensity = (factor * 45).toInt()
        for (i in pixels.indices) {
            val grain = random.nextInt(intensity * 2 + 1) - intensity
            val p = pixels[i]
            val r = (((p shr 16) and 0xFF) + grain).coerceIn(0, 255)
            val g = (((p shr 8) and 0xFF) + grain).coerceIn(0, 255)
            val b = ((p and 0xFF) + grain).coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Pattern Screen / Grid Overlay.
     */
    fun applyPatternOverlay(src: Bitmap, factor: Float): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((factor * 60).toInt(), 255, 255, 255)
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }

        val step = 16f
        var x = 0f
        while (x < w + h) {
            canvas.drawLine(x, 0f, x - h, h, linePaint)
            x += step
        }
        return out
    }

    /**
     * Color Halation: Red/orange glowing edges along highlights.
     */
    fun applyColorHalation(src: Bitmap, factor: Float): Bitmap {
        val w = src.width
        val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val inPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        src.getPixels(inPixels, 0, w, 0, 0, w, h)

        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                val c = inPixels[idx]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                val left = inPixels[idx - 1]
                val diff = abs(r - ((left shr 16) and 0xFF))

                if (diff > 45 && r > 160) {
                    val boostR = (r + 40 * factor).toInt().coerceIn(0, 255)
                    val glowG = (g + 15 * factor).toInt().coerceIn(0, 255)
                    outPixels[idx] = (0xFF shl 24) or (boostR shl 16) or (glowG shl 8) or b
                } else {
                    outPixels[idx] = c
                }
            }
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }
}
