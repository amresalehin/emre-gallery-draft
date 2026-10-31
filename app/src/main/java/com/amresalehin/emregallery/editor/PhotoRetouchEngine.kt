package com.amresalehin.emregallery.editor

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.*
import kotlin.random.Random

/**
 * Pure offline retouching engine for portrait and image restoration tools.
 */
object PhotoRetouchEngine {

    fun applyRetouch(src: Bitmap, strokes: List<RetouchStroke>): Bitmap {
        if (strokes.isEmpty()) return src
        val result = src.copy(Bitmap.Config.ARGB_8888, true)

        for (stroke in strokes) {
            val radius = stroke.radius.toInt().coerceAtLeast(4)
            val intensity = stroke.intensity.coerceIn(0f, 1f)
            when (stroke.type) {
                RetouchType.BLEMISH -> {
                    for (p in stroke.points) {
                        removeBlemishCircle(result, p.x.toInt(), p.y.toInt(), radius)
                    }
                }
                RetouchType.OBJECT_REMOVAL -> {
                    for (p in stroke.points) {
                        removeBlemishCircle(result, p.x.toInt(), p.y.toInt(), (radius * 1.5f).toInt())
                    }
                }
                RetouchType.SKIN_SMOOTH -> {
                    for (p in stroke.points) {
                        smoothSkinCircle(result, p.x.toInt(), p.y.toInt(), radius, intensity)
                    }
                }
                RetouchType.RED_EYE -> {
                    for (p in stroke.points) {
                        fixRedEyeCircle(result, p.x.toInt(), p.y.toInt(), radius)
                    }
                }
                RetouchType.TEETH_WHITENING -> {
                    for (p in stroke.points) {
                        whitenTeethCircle(result, p.x.toInt(), p.y.toInt(), radius, intensity)
                    }
                }
                RetouchType.EYE_ENHANCEMENT -> {
                    for (p in stroke.points) {
                        enhanceEyeCircle(result, p.x.toInt(), p.y.toInt(), radius, intensity)
                    }
                }
                RetouchType.EYE_COLOUR -> {
                    for (p in stroke.points) {
                        tintEyeCircle(result, p.x.toInt(), p.y.toInt(), radius, stroke.tintColor, intensity)
                    }
                }
                RetouchType.WRINKLE_REMOVAL -> {
                    for (p in stroke.points) {
                        smoothSkinCircle(result, p.x.toInt(), p.y.toInt(), radius, intensity * 1.3f)
                    }
                }
                RetouchType.HAIR_RECOLOUR -> {
                    for (p in stroke.points) {
                        recolorHairCircle(result, p.x.toInt(), p.y.toInt(), radius, stroke.tintColor, intensity)
                    }
                }
                RetouchType.MAKEUP_BRUSH -> {
                    for (p in stroke.points) {
                        applyMakeupCircle(result, p.x.toInt(), p.y.toInt(), radius, stroke.tintColor, intensity)
                    }
                }
                RetouchType.SKIN_TONE -> {
                    for (p in stroke.points) {
                        adjustSkinToneCircle(result, p.x.toInt(), p.y.toInt(), radius, stroke.tintColor, intensity)
                    }
                }
                RetouchType.DODGE_BRUSH -> {
                    for (p in stroke.points) {
                        dodgeCircle(result, p.x.toInt(), p.y.toInt(), radius, intensity)
                    }
                }
                RetouchType.BURN_BRUSH -> {
                    for (p in stroke.points) {
                        burnCircle(result, p.x.toInt(), p.y.toInt(), radius, intensity)
                    }
                }
                RetouchType.SMUDGE_BRUSH -> {
                    applySmudgeStroke(result, stroke.points, radius, intensity)
                }
                RetouchType.CLONE_STAMP -> {
                    val srcPt = stroke.sourcePoint ?: stroke.points.firstOrNull() ?: Offset.Zero
                    val targetPt = stroke.points.lastOrNull() ?: Offset.Zero
                    applyCloneStamp(result, srcPt, targetPt, radius, intensity)
                }
                RetouchType.PATCH_HEALING -> {
                    val srcPt = stroke.sourcePoint ?: stroke.points.firstOrNull() ?: Offset.Zero
                    val targetPt = stroke.points.lastOrNull() ?: Offset.Zero
                    applyPatchHealing(result, srcPt, targetPt, radius, intensity)
                }
                RetouchType.FACE_RESHAPE -> {
                    for (p in stroke.points) {
                        liquifyLocalPinchOrExpand(result, p.x.toInt(), p.y.toInt(), radius * 2, -0.25f * intensity)
                    }
                }
                RetouchType.NOSE_RESHAPE -> {
                    for (p in stroke.points) {
                        liquifyLocalPinchOrExpand(result, p.x.toInt(), p.y.toInt(), radius, -0.3f * intensity)
                    }
                }
                RetouchType.LIP_RESHAPE -> {
                    for (p in stroke.points) {
                        liquifyLocalPinchOrExpand(result, p.x.toInt(), p.y.toInt(), radius, 0.25f * intensity)
                    }
                }
                RetouchType.SELECTIVE_BRUSH -> {
                    for (p in stroke.points) {
                        smoothSkinCircle(result, p.x.toInt(), p.y.toInt(), radius, intensity)
                    }
                }
            }
        }
        return result
    }

    private fun removeBlemishCircle(bmp: Bitmap, cx: Int, cy: Int, r: Int) {
        val w = bmp.width
        val h = bmp.height
        val minX = (cx - r).coerceIn(0, w - 1)
        val maxX = (cx + r).coerceIn(0, w - 1)
        val minY = (cy - r).coerceIn(0, h - 1)
        val maxY = (cy + r).coerceIn(0, h - 1)
        val patchW = maxX - minX + 1
        val patchH = maxY - minY + 1
        if (patchW <= 0 || patchH <= 0) return

        val pixels = IntArray(patchW * patchH)
        bmp.getPixels(pixels, 0, patchW, minX, minY, patchW, patchH)

        var borderR = 0L; var borderG = 0L; var borderB = 0L; var count = 0
        for (y in 0 until patchH) {
            for (x in 0 until patchW) {
                val dx = x - (cx - minX)
                val dy = y - (cy - minY)
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist in (r * 0.75f)..r.toFloat()) {
                    val p = pixels[y * patchW + x]
                    borderR += (p shr 16) and 0xFF
                    borderG += (p shr 8) and 0xFF
                    borderB += p and 0xFF
                    count++
                }
            }
        }
        if (count == 0) return
        val avgR = (borderR / count).toInt()
        val avgG = (borderG / count).toInt()
        val avgB = (borderB / count).toInt()
        val random = Random(cx * 31 + cy)

        for (y in 0 until patchH) {
            for (x in 0 until patchW) {
                val dx = x - (cx - minX)
                val dy = y - (cy - minY)
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist <= r) {
                    val alphaRatio = (1f - (dist / r)).pow(1.5f)
                    val idx = y * patchW + x
                    val orig = pixels[idx]
                    val oR = (orig shr 16) and 0xFF
                    val oG = (orig shr 8) and 0xFF
                    val oB = orig and 0xFF

                    val nR = (oR * (1f - alphaRatio) + (avgR + random.nextInt(5) - 2) * alphaRatio).toInt().coerceIn(0, 255)
                    val nG = (oG * (1f - alphaRatio) + (avgG + random.nextInt(5) - 2) * alphaRatio).toInt().coerceIn(0, 255)
                    val nB = (oB * (1f - alphaRatio) + (avgB + random.nextInt(5) - 2) * alphaRatio).toInt().coerceIn(0, 255)
                    pixels[idx] = (0xFF shl 24) or (nR shl 16) or (nG shl 8) or nB
                }
            }
        }
        bmp.setPixels(pixels, 0, patchW, minX, minY, patchW, patchH)
    }

    private fun smoothSkinCircle(bmp: Bitmap, cx: Int, cy: Int, r: Int, intensity: Float) {
        val w = bmp.width
        val h = bmp.height
        val minX = (cx - r).coerceIn(0, w - 1)
        val maxX = (cx + r).coerceIn(0, w - 1)
        val minY = (cy - r).coerceIn(0, h - 1)
        val maxY = (cy + r).coerceIn(0, h - 1)
        val patchW = maxX - minX + 1
        val patchH = maxY - minY + 1
        if (patchW <= 2 || patchH <= 2) return

        val pixels = IntArray(patchW * patchH)
        bmp.getPixels(pixels, 0, patchW, minX, minY, patchW, patchH)

        for (y in 1 until patchH - 1) {
            for (x in 1 until patchW - 1) {
                val dx = x - (cx - minX)
                val dy = y - (cy - minY)
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist <= r) {
                    val idx = y * patchW + x
                    val center = pixels[idx]
                    val cR = (center shr 16) and 0xFF
                    val cG = (center shr 8) and 0xFF
                    val cB = center and 0xFF

                    var wSum = 0f
                    var rSum = 0f; var gSum = 0f; var bSum = 0f

                    for (ky in -1..1) {
                        for (kx in -1..1) {
                            val neighbor = pixels[(y + ky) * patchW + (x + kx)]
                            val nR = (neighbor shr 16) and 0xFF
                            val nG = (neighbor shr 8) and 0xFF
                            val nB = neighbor and 0xFF

                            val colorDiff = abs(cR - nR) + abs(cG - nG) + abs(cB - nB)
                            if (colorDiff < 75) {
                                val weight = 1f / (1f + colorDiff * 0.1f)
                                wSum += weight
                                rSum += nR * weight
                                gSum += nG * weight
                                bSum += nB * weight
                            }
                        }
                    }

                    if (wSum > 0f) {
                        val smoothR = (rSum / wSum).toInt().coerceIn(0, 255)
                        val smoothG = (gSum / wSum).toInt().coerceIn(0, 255)
                        val smoothB = (bSum / wSum).toInt().coerceIn(0, 255)

                        val falloff = (1f - (dist / r)).coerceIn(0f, 1f) * intensity
                        val finalR = (cR * (1f - falloff) + smoothR * falloff).toInt()
                        val finalG = (cG * (1f - falloff) + smoothG * falloff).toInt()
                        val finalB = (cB * (1f - falloff) + smoothB * falloff).toInt()
                        pixels[idx] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
                    }
                }
            }
        }
        bmp.setPixels(pixels, 0, patchW, minX, minY, patchW, patchH)
    }

    private fun fixRedEyeCircle(bmp: Bitmap, cx: Int, cy: Int, r: Int) {
        val w = bmp.width
        val h = bmp.height
        val minX = (cx - r).coerceIn(0, w - 1)
        val maxX = (cx + r).coerceIn(0, w - 1)
        val minY = (cy - r).coerceIn(0, h - 1)
        val maxY = (cy + r).coerceIn(0, h - 1)
        val patchW = maxX - minX + 1
        val patchH = maxY - minY + 1
        if (patchW <= 0 || patchH <= 0) return

        val pixels = IntArray(patchW * patchH)
        bmp.getPixels(pixels, 0, patchW, minX, minY, patchW, patchH)

        for (y in 0 until patchH) {
            for (x in 0 until patchW) {
                val dx = x - (cx - minX)
                val dy = y - (cy - minY)
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist <= r) {
                    val idx = y * patchW + x
                    val p = pixels[idx]
                    val red = (p shr 16) and 0xFF
                    val green = (p shr 8) and 0xFF
                    val blue = p and 0xFF
                    if (red > 70 && red > green * 1.35f && red > blue * 1.35f) {
                        val neutral = ((green + blue) / 2.0f * 0.75f).toInt().coerceIn(10, 75)
                        pixels[idx] = (0xFF shl 24) or (neutral shl 16) or (neutral shl 8) or neutral
                    }
                }
            }
        }
        bmp.setPixels(pixels, 0, patchW, minX, minY, patchW, patchH)
    }

    private fun whitenTeethCircle(bmp: Bitmap, cx: Int, cy: Int, r: Int, intensity: Float) {
        val w = bmp.width
        val h = bmp.height
        val minX = (cx - r).coerceIn(0, w - 1)
        val maxX = (cx + r).coerceIn(0, w - 1)
        val minY = (cy - r).coerceIn(0, h - 1)
        val maxY = (cy + r).coerceIn(0, h - 1)
        val patchW = maxX - minX + 1
        val patchH = maxY - minY + 1
        if (patchW <= 0 || patchH <= 0) return

        val pixels = IntArray(patchW * patchH)
        bmp.getPixels(pixels, 0, patchW, minX, minY, patchW, patchH)
        val hsv = FloatArray(3)

        for (y in 0 until patchH) {
            for (x in 0 until patchW) {
                val dx = x - (cx - minX)
                val dy = y - (cy - minY)
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist <= r) {
                    val idx = y * patchW + x
                    val p = pixels[idx]
                    val rC = (p shr 16) and 0xFF
                    val gC = (p shr 8) and 0xFF
                    val bC = p and 0xFF
                    AndroidColor.RGBToHSV(rC, gC, bC, hsv)
                    // Yellow/warm tint is typically hue 25..65
                    if (hsv[0] in 20f..70f && hsv[2] > 0.3f) {
                        val falloff = (1f - (dist / r)).coerceIn(0f, 1f) * intensity
                        hsv[1] = (hsv[1] * (1f - 0.7f * falloff)).coerceIn(0f, 1f) // desaturate yellow
                        hsv[2] = (hsv[2] * (1f + 0.25f * falloff)).coerceIn(0f, 1f) // brighten
                        pixels[idx] = AndroidColor.HSVToColor(hsv)
                    }
                }
            }
        }
        bmp.setPixels(pixels, 0, patchW, minX, minY, patchW, patchH)
    }

    private fun enhanceEyeCircle(bmp: Bitmap, cx: Int, cy: Int, r: Int, intensity: Float) {
        val w = bmp.width
        val h = bmp.height
        val minX = (cx - r).coerceIn(0, w - 1)
        val maxX = (cx + r).coerceIn(0, w - 1)
        val minY = (cy - r).coerceIn(0, h - 1)
        val maxY = (cy + r).coerceIn(0, h - 1)
        val patchW = maxX - minX + 1
        val patchH = maxY - minY + 1
        if (patchW <= 0 || patchH <= 0) return

        val pixels = IntArray(patchW * patchH)
        bmp.getPixels(pixels, 0, patchW, minX, minY, patchW, patchH)

        for (y in 0 until patchH) {
            for (x in 0 until patchW) {
                val dx = x - (cx - minX)
                val dy = y - (cy - minY)
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist <= r) {
                    val idx = y * patchW + x
                    val p = pixels[idx]
                    val rC = (p shr 16) and 0xFF
                    val gC = (p shr 8) and 0xFF
                    val bC = p and 0xFF
                    val falloff = (1f - (dist / r)).coerceIn(0f, 1f) * intensity
                    // Boost contrast and brightness of iris/sclera
                    val boost = 1f + 0.35f * falloff
                    val nR = (rC * boost).toInt().coerceIn(0, 255)
                    val nG = (gC * boost).toInt().coerceIn(0, 255)
                    val nB = (bC * boost).toInt().coerceIn(0, 255)
                    pixels[idx] = (0xFF shl 24) or (nR shl 16) or (nG shl 8) or nB
                }
            }
        }
        bmp.setPixels(pixels, 0, patchW, minX, minY, patchW, patchH)
    }

    private fun tintEyeCircle(bmp: Bitmap, cx: Int, cy: Int, r: Int, tintColor: Color, intensity: Float) {
        val w = bmp.width
        val h = bmp.height
        val minX = (cx - r).coerceIn(0, w - 1)
        val maxX = (cx + r).coerceIn(0, w - 1)
        val minY = (cy - r).coerceIn(0, h - 1)
        val maxY = (cy + r).coerceIn(0, h - 1)
        val patchW = maxX - minX + 1
        val patchH = maxY - minY + 1
        if (patchW <= 0 || patchH <= 0) return

        val pixels = IntArray(patchW * patchH)
        bmp.getPixels(pixels, 0, patchW, minX, minY, patchW, patchH)
        val tArgb = tintColor.toArgb()
        val tR = (tArgb shr 16) and 0xFF
        val tG = (tArgb shr 8) and 0xFF
        val tB = tArgb and 0xFF

        for (y in 0 until patchH) {
            for (x in 0 until patchW) {
                val dx = x - (cx - minX)
                val dy = y - (cy - minY)
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist <= r) {
                    val idx = y * patchW + x
                    val p = pixels[idx]
                    val rC = (p shr 16) and 0xFF
                    val gC = (p shr 8) and 0xFF
                    val bC = p and 0xFF
                    val lum = (rC * 0.299f + gC * 0.587f + bC * 0.114f) / 255f
                    // Exclude deep pupil and bright specular reflections
                    if (lum in 0.15f..0.85f) {
                        val falloff = (1f - (dist / r)).coerceIn(0f, 1f) * intensity * 0.65f
                        val nR = (rC * (1f - falloff) + tR * lum * falloff).toInt().coerceIn(0, 255)
                        val nG = (gC * (1f - falloff) + tG * lum * falloff).toInt().coerceIn(0, 255)
                        val nB = (bC * (1f - falloff) + tB * lum * falloff).toInt().coerceIn(0, 255)
                        pixels[idx] = (0xFF shl 24) or (nR shl 16) or (nG shl 8) or nB
                    }
                }
            }
        }
        bmp.setPixels(pixels, 0, patchW, minX, minY, patchW, patchH)
    }

    private fun recolorHairCircle(bmp: Bitmap, cx: Int, cy: Int, r: Int, tintColor: Color, intensity: Float) {
        tintEyeCircle(bmp, cx, cy, r, tintColor, intensity)
    }

    private fun applyMakeupCircle(bmp: Bitmap, cx: Int, cy: Int, r: Int, tintColor: Color, intensity: Float) {
        val w = bmp.width
        val h = bmp.height
        val minX = (cx - r).coerceIn(0, w - 1)
        val maxX = (cx + r).coerceIn(0, w - 1)
        val minY = (cy - r).coerceIn(0, h - 1)
        val maxY = (cy + r).coerceIn(0, h - 1)
        val patchW = maxX - minX + 1
        val patchH = maxY - minY + 1
        if (patchW <= 0 || patchH <= 0) return

        val pixels = IntArray(patchW * patchH)
        bmp.getPixels(pixels, 0, patchW, minX, minY, patchW, patchH)
        val tArgb = tintColor.toArgb()
        val tR = (tArgb shr 16) and 0xFF
        val tG = (tArgb shr 8) and 0xFF
        val tB = tArgb and 0xFF

        for (y in 0 until patchH) {
            for (x in 0 until patchW) {
                val dx = x - (cx - minX)
                val dy = y - (cy - minY)
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist <= r) {
                    val idx = y * patchW + x
                    val p = pixels[idx]
                    val rC = (p shr 16) and 0xFF
                    val gC = (p shr 8) and 0xFF
                    val bC = p and 0xFF
                    val falloff = (1f - (dist / r)).pow(2f).coerceIn(0f, 1f) * intensity * 0.45f
                    val nR = (rC * (1f - falloff) + tR * falloff).toInt().coerceIn(0, 255)
                    val nG = (gC * (1f - falloff) + tG * falloff).toInt().coerceIn(0, 255)
                    val nB = (bC * (1f - falloff) + tB * falloff).toInt().coerceIn(0, 255)
                    pixels[idx] = (0xFF shl 24) or (nR shl 16) or (nG shl 8) or nB
                }
            }
        }
        bmp.setPixels(pixels, 0, patchW, minX, minY, patchW, patchH)
    }

    private fun adjustSkinToneCircle(bmp: Bitmap, cx: Int, cy: Int, r: Int, tintColor: Color, intensity: Float) {
        applyMakeupCircle(bmp, cx, cy, r, tintColor, intensity * 0.5f)
    }

    private fun dodgeCircle(bmp: Bitmap, cx: Int, cy: Int, r: Int, intensity: Float) {
        val w = bmp.width
        val h = bmp.height
        val minX = (cx - r).coerceIn(0, w - 1)
        val maxX = (cx + r).coerceIn(0, w - 1)
        val minY = (cy - r).coerceIn(0, h - 1)
        val maxY = (cy + r).coerceIn(0, h - 1)
        val patchW = maxX - minX + 1
        val patchH = maxY - minY + 1
        if (patchW <= 0 || patchH <= 0) return

        val pixels = IntArray(patchW * patchH)
        bmp.getPixels(pixels, 0, patchW, minX, minY, patchW, patchH)

        for (y in 0 until patchH) {
            for (x in 0 until patchW) {
                val dx = x - (cx - minX)
                val dy = y - (cy - minY)
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist <= r) {
                    val idx = y * patchW + x
                    val p = pixels[idx]
                    val rC = (p shr 16) and 0xFF
                    val gC = (p shr 8) and 0xFF
                    val bC = p and 0xFF
                    val factor = 1f + (1f - (dist / r)).coerceIn(0f, 1f) * intensity * 0.4f
                    val nR = (rC * factor).toInt().coerceIn(0, 255)
                    val nG = (gC * factor).toInt().coerceIn(0, 255)
                    val nB = (bC * factor).toInt().coerceIn(0, 255)
                    pixels[idx] = (0xFF shl 24) or (nR shl 16) or (nG shl 8) or nB
                }
            }
        }
        bmp.setPixels(pixels, 0, patchW, minX, minY, patchW, patchH)
    }

    private fun burnCircle(bmp: Bitmap, cx: Int, cy: Int, r: Int, intensity: Float) {
        val w = bmp.width
        val h = bmp.height
        val minX = (cx - r).coerceIn(0, w - 1)
        val maxX = (cx + r).coerceIn(0, w - 1)
        val minY = (cy - r).coerceIn(0, h - 1)
        val maxY = (cy + r).coerceIn(0, h - 1)
        val patchW = maxX - minX + 1
        val patchH = maxY - minY + 1
        if (patchW <= 0 || patchH <= 0) return

        val pixels = IntArray(patchW * patchH)
        bmp.getPixels(pixels, 0, patchW, minX, minY, patchW, patchH)

        for (y in 0 until patchH) {
            for (x in 0 until patchW) {
                val dx = x - (cx - minX)
                val dy = y - (cy - minY)
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist <= r) {
                    val idx = y * patchW + x
                    val p = pixels[idx]
                    val rC = (p shr 16) and 0xFF
                    val gC = (p shr 8) and 0xFF
                    val bC = p and 0xFF
                    val factor = 1f - (1f - (dist / r)).coerceIn(0f, 1f) * intensity * 0.35f
                    val nR = (rC * factor).toInt().coerceIn(0, 255)
                    val nG = (gC * factor).toInt().coerceIn(0, 255)
                    val nB = (bC * factor).toInt().coerceIn(0, 255)
                    pixels[idx] = (0xFF shl 24) or (nR shl 16) or (nG shl 8) or nB
                }
            }
        }
        bmp.setPixels(pixels, 0, patchW, minX, minY, patchW, patchH)
    }

    private fun applySmudgeStroke(bmp: Bitmap, points: List<Offset>, radius: Int, intensity: Float) {
        if (points.size < 2) return
        val w = bmp.width
        val h = bmp.height
        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val dx = p1.x - p0.x
            val dy = p1.y - p0.y
            val dist = sqrt(dx * dx + dy * dy)
            val steps = max(1, (dist / (radius * 0.5f)).toInt())
            for (s in 0..steps) {
                val t = s.toFloat() / steps
                val cx = (p0.x + dx * t).toInt()
                val cy = (p0.y + dy * t).toInt()
                val shiftX = (dx / steps * 0.6f * intensity).toInt()
                val shiftY = (dy / steps * 0.6f * intensity).toInt()
                blendSmudge(bmp, cx, cy, radius, shiftX, shiftY)
            }
        }
    }

    private fun blendSmudge(bmp: Bitmap, cx: Int, cy: Int, r: Int, shiftX: Int, shiftY: Int) {
        val w = bmp.width
        val h = bmp.height
        val minX = (cx - r).coerceIn(0, w - 1)
        val maxX = (cx + r).coerceIn(0, w - 1)
        val minY = (cy - r).coerceIn(0, h - 1)
        val maxY = (cy + r).coerceIn(0, h - 1)
        val patchW = maxX - minX + 1
        val patchH = maxY - minY + 1
        if (patchW <= 0 || patchH <= 0) return

        val pixels = IntArray(patchW * patchH)
        bmp.getPixels(pixels, 0, patchW, minX, minY, patchW, patchH)

        for (y in 0 until patchH) {
            for (x in 0 until patchW) {
                val dx = x - (cx - minX)
                val dy = y - (cy - minY)
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist <= r) {
                    val falloff = (1f - (dist / r)).coerceIn(0f, 1f) * 0.5f
                    val srcX = (minX + x - shiftX).coerceIn(0, w - 1)
                    val srcY = (minY + y - shiftY).coerceIn(0, h - 1)
                    val srcP = bmp.getPixel(srcX, srcY)
                    val currP = pixels[y * patchW + x]

                    val sR = (srcP shr 16) and 0xFF
                    val sG = (srcP shr 8) and 0xFF
                    val sB = srcP and 0xFF
                    val cR = (currP shr 16) and 0xFF
                    val cG = (currP shr 8) and 0xFF
                    val cB = currP and 0xFF

                    val nR = (cR * (1f - falloff) + sR * falloff).toInt()
                    val nG = (cG * (1f - falloff) + sG * falloff).toInt()
                    val nB = (cB * (1f - falloff) + sB * falloff).toInt()
                    pixels[y * patchW + x] = (0xFF shl 24) or (nR shl 16) or (nG shl 8) or nB
                }
            }
        }
        bmp.setPixels(pixels, 0, patchW, minX, minY, patchW, patchH)
    }

    private fun applyCloneStamp(bmp: Bitmap, srcPt: Offset, targetPt: Offset, radius: Int, intensity: Float) {
        val w = bmp.width
        val h = bmp.height
        val sx = srcPt.x.toInt()
        val sy = srcPt.y.toInt()
        val tx = targetPt.x.toInt()
        val ty = targetPt.y.toInt()

        val minX = (tx - radius).coerceIn(0, w - 1)
        val maxX = (tx + radius).coerceIn(0, w - 1)
        val minY = (ty - radius).coerceIn(0, h - 1)
        val maxY = (ty + radius).coerceIn(0, h - 1)
        val patchW = maxX - minX + 1
        val patchH = maxY - minY + 1
        if (patchW <= 0 || patchH <= 0) return

        val pixels = IntArray(patchW * patchH)
        bmp.getPixels(pixels, 0, patchW, minX, minY, patchW, patchH)

        for (y in 0 until patchH) {
            for (x in 0 until patchW) {
                val dx = x - (tx - minX)
                val dy = y - (ty - minY)
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist <= radius) {
                    val falloff = (1f - (dist / radius)).coerceIn(0f, 1f) * intensity
                    val sampleX = (sx + dx).coerceIn(0, w - 1)
                    val sampleY = (sy + dy).coerceIn(0, h - 1)
                    val sampleP = bmp.getPixel(sampleX, sampleY)
                    val origP = pixels[y * patchW + x]

                    val sR = (sampleP shr 16) and 0xFF
                    val sG = (sampleP shr 8) and 0xFF
                    val sB = sampleP and 0xFF
                    val oR = (origP shr 16) and 0xFF
                    val oG = (origP shr 8) and 0xFF
                    val oB = origP and 0xFF

                    val nR = (oR * (1f - falloff) + sR * falloff).toInt()
                    val nG = (oG * (1f - falloff) + sG * falloff).toInt()
                    val nB = (oB * (1f - falloff) + sB * falloff).toInt()
                    pixels[y * patchW + x] = (0xFF shl 24) or (nR shl 16) or (nG shl 8) or nB
                }
            }
        }
        bmp.setPixels(pixels, 0, patchW, minX, minY, patchW, patchH)
    }

    private fun applyPatchHealing(bmp: Bitmap, srcPt: Offset, targetPt: Offset, radius: Int, intensity: Float) {
        applyCloneStamp(bmp, srcPt, targetPt, radius, intensity)
        smoothSkinCircle(bmp, targetPt.x.toInt(), targetPt.y.toInt(), radius, intensity * 0.4f)
    }

    private fun liquifyLocalPinchOrExpand(bmp: Bitmap, cx: Int, cy: Int, radius: Int, factor: Float) {
        val w = bmp.width
        val h = bmp.height
        val minX = (cx - radius).coerceIn(0, w - 1)
        val maxX = (cx + radius).coerceIn(0, w - 1)
        val minY = (cy - radius).coerceIn(0, h - 1)
        val maxY = (cy + radius).coerceIn(0, h - 1)
        val patchW = maxX - minX + 1
        val patchH = maxY - minY + 1
        if (patchW <= 0 || patchH <= 0) return

        val outPixels = IntArray(patchW * patchH)
        for (y in 0 until patchH) {
            for (x in 0 until patchW) {
                val dx = (minX + x) - cx
                val dy = (minY + y) - cy
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist <= radius) {
                    val normDist = dist / radius
                    val disp = if (factor >= 0) {
                        // Expand: push outward
                        normDist * (1f - factor * (1f - normDist))
                    } else {
                        // Pinch: pull inward
                        normDist * (1f - factor * (1f - normDist))
                    }
                    val srcDist = disp * radius
                    val angle = atan2(dy.toFloat(), dx.toFloat())
                    val sampleX = (cx + srcDist * cos(angle)).toInt().coerceIn(0, w - 1)
                    val sampleY = (cy + srcDist * sin(angle)).toInt().coerceIn(0, h - 1)
                    outPixels[y * patchW + x] = bmp.getPixel(sampleX, sampleY)
                } else {
                    outPixels[y * patchW + x] = bmp.getPixel(minX + x, minY + y)
                }
            }
        }
        bmp.setPixels(outPixels, 0, patchW, minX, minY, patchW, patchH)
    }
}
