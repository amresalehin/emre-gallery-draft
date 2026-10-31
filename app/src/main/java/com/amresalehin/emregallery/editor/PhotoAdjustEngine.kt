package com.amresalehin.emregallery.editor

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.*

/**
 * Pure offline color and tonal adjustment engine.
 * Handles Vibrance, HSL, RGB Curves, Levels, White Balance, Color Balance,
 * Split Toning, Gradient Maps, Duotone, Dehaze, Color Replace, and Color Splash.
 */
object PhotoAdjustEngine {

    fun applyExtendedAdjustments(src: Bitmap, adj: AdjustmentParams): Bitmap {
        // Fast-path: if no advanced adjustments are active, return src
        val hasHsl = adj.hslAdjustments.any { it.value.hueShift != 0f || it.value.satShift != 0f || it.value.lumShift != 0f }
        val hasCurves = isCurvesModified(adj.curves)
        val hasLevels = adj.levels.blackPoint > 0f || adj.levels.midtonesGamma != 1.0f || adj.levels.whitePoint < 255f
        val hasBalance = adj.colorBalance.shadowRed != 0f || adj.colorBalance.shadowGreen != 0f || adj.colorBalance.shadowBlue != 0f ||
                adj.colorBalance.midtoneRed != 0f || adj.colorBalance.midtoneGreen != 0f || adj.colorBalance.midtoneBlue != 0f ||
                adj.colorBalance.highlightRed != 0f || adj.colorBalance.highlightGreen != 0f || adj.colorBalance.highlightBlue != 0f
        val hasSplit = (adj.splitToning.highlightSat > 0f || adj.splitToning.shadowSat > 0f)
        val hasGradientMap = adj.gradientMap != GradientMapType.NONE
        val hasDuotone = adj.duotone.enabled
        val hasVibrance = adj.vibrance != 0f
        val hasDehaze = adj.dehaze > 0f
        val hasDodge = adj.dodge > 0f
        val hasBurn = adj.burn > 0f
        val hasColorReplace = adj.colorReplace.enabled
        val hasColorSplash = adj.colorSplash.enabled
        val hasWb = adj.whiteBalanceKelvin != 5500f

        if (!hasHsl && !hasCurves && !hasLevels && !hasBalance && !hasSplit && !hasGradientMap &&
            !hasDuotone && !hasVibrance && !hasDehaze && !hasDodge && !hasBurn && !hasColorReplace &&
            !hasColorSplash && !hasWb
        ) {
            return src
        }

        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // Precompute Curves LUTs
        val masterLut = if (hasCurves) buildCurveLut(adj.curves.master) else null
        val redLut = if (hasCurves) buildCurveLut(adj.curves.red) else null
        val greenLut = if (hasCurves) buildCurveLut(adj.curves.green) else null
        val blueLut = if (hasCurves) buildCurveLut(adj.curves.blue) else null

        // Precompute Levels LUT
        val levelsLut = if (hasLevels) buildLevelsLut(adj.levels) else null

        // Precompute White Balance Kelvin multipliers
        val (wbR, wbG, wbB) = kelvinToRgbMultipliers(adj.whiteBalanceKelvin)

        // Precompute Gradient Map LUT (256 RGB values)
        val gradMapLut = if (hasGradientMap) buildGradientMapLut(adj.gradientMap) else null
        val duotoneLut = if (hasDuotone) buildDuotoneLut(adj.duotone.shadowColor, adj.duotone.highlightColor) else null

        val hsv = FloatArray(3)

        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p shr 24) and 0xFF
            var r = (p shr 16) and 0xFF
            var g = (p shr 8) and 0xFF
            var b = p and 0xFF

            // 1. White balance
            if (hasWb) {
                r = (r * wbR).toInt().coerceIn(0, 255)
                g = (g * wbG).toInt().coerceIn(0, 255)
                b = (b * wbB).toInt().coerceIn(0, 255)
            }

            // 2. Levels
            if (levelsLut != null) {
                r = levelsLut[r]
                g = levelsLut[g]
                b = levelsLut[b]
            }

            // 3. Curves
            if (masterLut != null) {
                r = masterLut[r]
                g = masterLut[g]
                b = masterLut[b]
            }
            if (redLut != null) r = redLut[r]
            if (greenLut != null) g = greenLut[g]
            if (blueLut != null) b = blueLut[b]

            // 4. Color Balance (Shadows, Midtones, Highlights)
            if (hasBalance) {
                val lum = (r * 0.299f + g * 0.587f + b * 0.114f) / 255f
                val shadowWeight = (1f - lum * 2f).coerceIn(0f, 1f)
                val highlightWeight = (lum * 2f - 1f).coerceIn(0f, 1f)
                val midtoneWeight = (1f - 2f * abs(lum - 0.5f)).coerceIn(0f, 1f)

                r = (r + adj.colorBalance.shadowRed * shadowWeight +
                        adj.colorBalance.midtoneRed * midtoneWeight +
                        adj.colorBalance.highlightRed * highlightWeight).toInt().coerceIn(0, 255)
                g = (g + adj.colorBalance.shadowGreen * shadowWeight +
                        adj.colorBalance.midtoneGreen * midtoneWeight +
                        adj.colorBalance.highlightGreen * highlightWeight).toInt().coerceIn(0, 255)
                b = (b + adj.colorBalance.shadowBlue * shadowWeight +
                        adj.colorBalance.midtoneBlue * midtoneWeight +
                        adj.colorBalance.highlightBlue * highlightWeight).toInt().coerceIn(0, 255)
            }

            // 5. Split Toning
            if (hasSplit) {
                val lum = (r * 0.299f + g * 0.587f + b * 0.114f) / 255f
                val threshold = 0.5f + (adj.splitToning.balance / 200f)
                if (lum > threshold && adj.splitToning.highlightSat > 0f) {
                    val strength = ((lum - threshold) / (1f - threshold)).coerceIn(0f, 1f) * (adj.splitToning.highlightSat / 100f)
                    val tint = hueToRgb(adj.splitToning.highlightHue)
                    r = (r * (1f - strength) + tint[0] * strength).toInt().coerceIn(0, 255)
                    g = (g * (1f - strength) + tint[1] * strength).toInt().coerceIn(0, 255)
                    b = (b * (1f - strength) + tint[2] * strength).toInt().coerceIn(0, 255)
                } else if (lum <= threshold && adj.splitToning.shadowSat > 0f) {
                    val strength = ((threshold - lum) / threshold).coerceIn(0f, 1f) * (adj.splitToning.shadowSat / 100f)
                    val tint = hueToRgb(adj.splitToning.shadowHue)
                    r = (r * (1f - strength) + tint[0] * strength).toInt().coerceIn(0, 255)
                    g = (g * (1f - strength) + tint[1] * strength).toInt().coerceIn(0, 255)
                    b = (b * (1f - strength) + tint[2] * strength).toInt().coerceIn(0, 255)
                }
            }

            // 6. Dehaze
            if (hasDehaze) {
                val factor = adj.dehaze / 100f
                val minRgb = min(r, min(g, b))
                val darkOffset = (minRgb * factor * 0.45f).toInt()
                val contrastBoost = 1f + factor * 0.35f
                r = ((r - darkOffset) * contrastBoost).toInt().coerceIn(0, 255)
                g = ((g - darkOffset) * contrastBoost).toInt().coerceIn(0, 255)
                b = ((b - darkOffset) * contrastBoost).toInt().coerceIn(0, 255)
            }

            // 7. Dodge / Burn
            if (hasDodge) {
                val factor = 1f + (adj.dodge / 100f) * 0.75f
                r = (r * factor).toInt().coerceIn(0, 255)
                g = (g * factor).toInt().coerceIn(0, 255)
                b = (b * factor).toInt().coerceIn(0, 255)
            }
            if (hasBurn) {
                val factor = 1f - (adj.burn / 100f) * 0.55f
                r = (r * factor).toInt().coerceIn(0, 255)
                g = (g * factor).toInt().coerceIn(0, 255)
                b = (b * factor).toInt().coerceIn(0, 255)
            }

            // 8. Vibrance
            if (hasVibrance) {
                AndroidColor.RGBToHSV(r, g, b, hsv)
                val currentSat = hsv[1]
                // Enhance lower saturation colors more; protect high saturation colors
                val satChange = (1f - currentSat) * (adj.vibrance / 100f)
                hsv[1] = (currentSat + satChange).coerceIn(0f, 1f)
                val vibColor = AndroidColor.HSVToColor(hsv)
                r = (vibColor shr 16) and 0xFF
                g = (vibColor shr 8) and 0xFF
                b = vibColor and 0xFF
            }

            // 9. HSL per-color range adjustment
            if (hasHsl) {
                AndroidColor.RGBToHSV(r, g, b, hsv)
                val hue = hsv[0]
                val band = getHueColorBand(hue)
                val adjustment = adj.hslAdjustments[band]
                if (adjustment != null) {
                    if (adjustment.hueShift != 0f) {
                        hsv[0] = (hsv[0] + adjustment.hueShift + 360f) % 360f
                    }
                    if (adjustment.satShift != 0f) {
                        hsv[1] = (hsv[1] * (1f + adjustment.satShift / 100f)).coerceIn(0f, 1f)
                    }
                    if (adjustment.lumShift != 0f) {
                        hsv[2] = (hsv[2] * (1f + adjustment.lumShift / 100f)).coerceIn(0f, 1f)
                    }
                    val hslColor = AndroidColor.HSVToColor(hsv)
                    r = (hslColor shr 16) and 0xFF
                    g = (hslColor shr 8) and 0xFF
                    b = hslColor and 0xFF
                }
            }

            // 10. Color Replace
            if (hasColorReplace) {
                AndroidColor.RGBToHSV(r, g, b, hsv)
                val hueDiff = abs(hsv[0] - adj.colorReplace.sourceHue).let { min(it, 360f - it) }
                if (hueDiff <= adj.colorReplace.tolerance && hsv[1] > 0.15f) {
                    val weight = (1f - (hueDiff / adj.colorReplace.tolerance)).coerceIn(0f, 1f)
                    hsv[0] = (hsv[0] * (1f - weight) + adj.colorReplace.targetHue * weight + 360f) % 360f
                    val repColor = AndroidColor.HSVToColor(hsv)
                    r = (repColor shr 16) and 0xFF
                    g = (repColor shr 8) and 0xFF
                    b = repColor and 0xFF
                }
            }

            // 11. Color Splash
            if (hasColorSplash) {
                AndroidColor.RGBToHSV(r, g, b, hsv)
                val hueDiff = abs(hsv[0] - adj.colorSplash.targetHue).let { min(it, 360f - it) }
                if (hueDiff > adj.colorSplash.tolerance || hsv[1] < 0.1f) {
                    val gray = (r * 0.299f + g * 0.587f + b * 0.114f).toInt().coerceIn(0, 255)
                    r = gray
                    g = gray
                    b = gray
                }
            }

            // 12. Gradient Map / Duotone
            if (gradMapLut != null) {
                val lum = (r * 0.299f + g * 0.587f + b * 0.114f).toInt().coerceIn(0, 255)
                val mapped = gradMapLut[lum]
                r = (mapped shr 16) and 0xFF
                g = (mapped shr 8) and 0xFF
                b = mapped and 0xFF
            } else if (duotoneLut != null) {
                val lum = (r * 0.299f + g * 0.587f + b * 0.114f).toInt().coerceIn(0, 255)
                val mapped = duotoneLut[lum]
                r = (mapped shr 16) and 0xFF
                g = (mapped shr 8) and 0xFF
                b = mapped and 0xFF
            }

            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun getHueColorBand(hue: Float): ColorBand = when (hue) {
        in 345f..360f, in 0f..15f -> ColorBand.RED
        in 15f..45f -> ColorBand.ORANGE
        in 45f..80f -> ColorBand.YELLOW
        in 80f..160f -> ColorBand.GREEN
        in 160f..210f -> ColorBand.CYAN
        in 210f..260f -> ColorBand.BLUE
        in 260f..305f -> ColorBand.PURPLE
        else -> ColorBand.MAGENTA
    }

    private fun isCurvesModified(curves: RgbCurves): Boolean {
        fun isMod(list: List<CurvePoint>) = list.size != 2 ||
                list.first().inVal != 0f || list.first().outVal != 0f ||
                list.last().inVal != 1f || list.last().outVal != 1f
        return isMod(curves.master) || isMod(curves.red) || isMod(curves.green) || isMod(curves.blue)
    }

    private fun buildCurveLut(points: List<CurvePoint>): IntArray {
        val lut = IntArray(256)
        val sorted = points.sortedBy { it.inVal }
        if (sorted.isEmpty()) {
            for (i in 0..255) lut[i] = i
            return lut
        }
        for (i in 0..255) {
            val normalizedIn = i / 255f
            val mapped = evaluatePiecewiseCurve(sorted, normalizedIn)
            lut[i] = (mapped * 255f).toInt().coerceIn(0, 255)
        }
        return lut
    }

    private fun evaluatePiecewiseCurve(points: List<CurvePoint>, inVal: Float): Float {
        if (points.size == 1) return points[0].outVal
        if (inVal <= points.first().inVal) return points.first().outVal
        if (inVal >= points.last().inVal) return points.last().outVal
        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            if (inVal >= p0.inVal && inVal <= p1.inVal) {
                val span = p1.inVal - p0.inVal
                val t = if (span > 0.0001f) (inVal - p0.inVal) / span else 0f
                // Smooth hermite interpolation
                val smoothT = t * t * (3f - 2f * t)
                return p0.outVal + smoothT * (p1.outVal - p0.outVal)
            }
        }
        return inVal
    }

    private fun buildLevelsLut(levels: LevelsParams): IntArray {
        val lut = IntArray(256)
        val inBlack = levels.blackPoint.coerceIn(0f, 254f)
        val inWhite = levels.whitePoint.coerceIn(inBlack + 1f, 255f)
        val gamma = levels.midtonesGamma.coerceIn(0.1f, 5.0f)
        val invGamma = 1.0f / gamma

        for (i in 0..255) {
            val v = (i.toFloat() - inBlack) / (inWhite - inBlack)
            val clamped = v.coerceIn(0f, 1f)
            val corrected = clamped.pow(invGamma)
            lut[i] = (corrected * 255f).toInt().coerceIn(0, 255)
        }
        return lut
    }

    private fun kelvinToRgbMultipliers(kelvin: Float): Triple<Float, Float, Float> {
        val temp = kelvin / 100f
        val red: Float
        val green: Float
        val blue: Float

        if (temp <= 66) {
            red = 255f
            green = (99.4708025861 * ln(temp.toDouble()) - 161.1195681661).toFloat().coerceIn(0f, 255f)
            blue = if (temp <= 19) 0f else (138.5177312231 * ln((temp - 10).toDouble()) - 305.0447927307).toFloat().coerceIn(0f, 255f)
        } else {
            red = (329.698727446 * (temp - 60).toDouble().pow(-0.1332047592)).toFloat().coerceIn(0f, 255f)
            green = (288.1221695283 * (temp - 60).toDouble().pow(-0.0755148492)).toFloat().coerceIn(0f, 255f)
            blue = 255f
        }
        // Normalize against neutral 5500K reference
        return Triple(red / 255f, green / 255f, blue / 255f)
    }

    private fun hueToRgb(hue: Float): IntArray {
        val hsv = floatArrayOf((hue + 360f) % 360f, 1.0f, 1.0f)
        val color = AndroidColor.HSVToColor(hsv)
        return intArrayOf((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF)
    }

    private fun buildGradientMapLut(type: GradientMapType): IntArray {
        val lut = IntArray(256)
        val (stops, colors) = when (type) {
            GradientMapType.SEPIA_GOLD -> floatArrayOf(0f, 0.5f, 1f) to arrayOf(
                AndroidColor.rgb(20, 10, 5), AndroidColor.rgb(180, 130, 70), AndroidColor.rgb(255, 240, 200)
            )
            GradientMapType.SUNSET_GLOW -> floatArrayOf(0f, 0.45f, 0.8f, 1f) to arrayOf(
                AndroidColor.rgb(40, 10, 50), AndroidColor.rgb(210, 50, 80), AndroidColor.rgb(255, 140, 40), AndroidColor.rgb(255, 235, 150)
            )
            GradientMapType.CYBER_NEON -> floatArrayOf(0f, 0.5f, 1f) to arrayOf(
                AndroidColor.rgb(10, 5, 30), AndroidColor.rgb(230, 20, 120), AndroidColor.rgb(0, 245, 255)
            )
            GradientMapType.DEEP_OCEAN -> floatArrayOf(0f, 0.5f, 1f) to arrayOf(
                AndroidColor.rgb(2, 12, 35), AndroidColor.rgb(10, 95, 140), AndroidColor.rgb(180, 240, 255)
            )
            GradientMapType.EMERALD_FOREST -> floatArrayOf(0f, 0.5f, 1f) to arrayOf(
                AndroidColor.rgb(5, 25, 10), AndroidColor.rgb(30, 140, 80), AndroidColor.rgb(210, 255, 180)
            )
            GradientMapType.VINTAGE_PASTEL -> floatArrayOf(0f, 0.5f, 1f) to arrayOf(
                AndroidColor.rgb(60, 45, 75), AndroidColor.rgb(195, 140, 160), AndroidColor.rgb(250, 235, 215)
            )
            GradientMapType.BW_CONTRAST -> floatArrayOf(0f, 0.5f, 1f) to arrayOf(
                AndroidColor.rgb(0, 0, 0), AndroidColor.rgb(120, 120, 120), AndroidColor.rgb(255, 255, 255)
            )
            GradientMapType.NONE -> floatArrayOf(0f, 1f) to arrayOf(
                AndroidColor.rgb(0, 0, 0), AndroidColor.rgb(255, 255, 255)
            )
        }
        for (i in 0..255) {
            val t = i / 255f
            lut[i] = interpolateMultiStopColor(stops, colors, t)
        }
        return lut
    }

    private fun buildDuotoneLut(shadowColor: Color, highlightColor: Color): IntArray {
        val lut = IntArray(256)
        val sArgb = shadowColor.toArgb()
        val hArgb = highlightColor.toArgb()
        val sR = (sArgb shr 16) and 0xFF
        val sG = (sArgb shr 8) and 0xFF
        val sB = sArgb and 0xFF
        val hR = (hArgb shr 16) and 0xFF
        val hG = (hArgb shr 8) and 0xFF
        val hB = hArgb and 0xFF

        for (i in 0..255) {
            val t = i / 255f
            val r = (sR * (1f - t) + hR * t).toInt()
            val g = (sG * (1f - t) + hG * t).toInt()
            val b = (sB * (1f - t) + hB * t).toInt()
            lut[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return lut
    }

    private fun interpolateMultiStopColor(stops: FloatArray, colors: Array<Int>, t: Float): Int {
        if (t <= stops.first()) return colors.first()
        if (t >= stops.last()) return colors.last()
        for (i in 0 until stops.size - 1) {
            val s0 = stops[i]
            val s1 = stops[i + 1]
            if (t >= s0 && t <= s1) {
                val factor = (t - s0) / (s1 - s0)
                val c0 = colors[i]
                val c1 = colors[i + 1]
                val r = (((c0 shr 16) and 0xFF) * (1f - factor) + ((c1 shr 16) and 0xFF) * factor).toInt()
                val g = (((c0 shr 8) and 0xFF) * (1f - factor) + ((c1 shr 8) and 0xFF) * factor).toInt()
                val b = ((c0 and 0xFF) * (1f - factor) + (c1 and 0xFF) * factor).toInt()
                return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return colors.last()
    }
}
