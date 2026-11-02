package com.amresalehin.emregallery.editor.renderers

import android.graphics.*
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.toArgb
import com.amresalehin.emregallery.editor.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Handles multi-layer Photoshop-style overlay compositing, layer masks, and blend modes.
 */
object PhotoLayerStackRenderer {

    fun renderOverlayLayers(
        canvas: Canvas,
        width: Float,
        height: Float,
        layers: List<EditorOverlayLayer>,
        density: Float
    ) {
        for (i in layers.indices) {
            val layer = layers[i]
            if (!layer.isVisible) continue

            val cx = layer.xRatio * width
            val cy = layer.yRatio * height
            val lw = layer.widthRatio * width * layer.scaleFactor
            val lh = layer.heightRatio * height * layer.scaleFactor

            canvas.save()

            // If clipped to underlying layer, clip within previous layer's bounds
            if (layer.isClipped && i > 0) {
                val prev = layers[i - 1]
                val prevCx = prev.xRatio * width
                val prevCy = prev.yRatio * height
                val prevLw = prev.widthRatio * width * prev.scaleFactor
                val prevLh = prev.heightRatio * height * prev.scaleFactor
                val prevRect = RectF(
                    prevCx - prevLw / 2f,
                    prevCy - prevLh / 2f,
                    prevCx + prevLw / 2f,
                    prevCy + prevLh / 2f
                )
                canvas.clipRect(prevRect)
            }

            canvas.translate(cx, cy)
            canvas.rotate(layer.rotationDegrees)
            if (layer.isFlippedH || layer.isFlippedV) {
                canvas.scale(if (layer.isFlippedH) -1f else 1f, if (layer.isFlippedV) -1f else 1f)
            }

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                val maskDensity = layer.mask?.density ?: 1.0f
                alpha = ((layer.opacity.coerceIn(0f, 1f) * maskDensity.coerceIn(0f, 1f)) * 255).toInt()
                xfermode = when (layer.blendMode) {
                    "Multiply" -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                    "Screen" -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                    "Overlay" -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
                    "Darken" -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
                    "Lighten" -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
                    "Linear dodge (add)" -> PorterDuffXfermode(PorterDuff.Mode.ADD)
                    "Color dodge" -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                    "Color burn" -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                    "Difference" -> PorterDuffXfermode(PorterDuff.Mode.XOR)
                    else -> null
                }
            }

            val layerRect = RectF(-lw / 2f, -lh / 2f, lw / 2f, lh / 2f)

            // Layer Mask Clipping
            val mask = layer.mask
            val hasMask = mask?.isEnabled == true

            if (hasMask) {
                canvas.save()
                val maskPath = Path()
                if (mask!!.hasSubjectMask) {
                    val expansion = mask.contractExpand * density
                    val ovalRect = RectF(
                        layerRect.left + 30f * density - expansion,
                        layerRect.top + 20f * density - expansion,
                        layerRect.right - 30f * density + expansion,
                        layerRect.bottom - 10f * density + expansion
                    )
                    maskPath.addRoundRect(ovalRect, 40f * density, 40f * density, Path.Direction.CW)
                } else {
                    maskPath.addRect(layerRect, Path.Direction.CW)
                }

                if (mask.isInverted) {
                    val fullPath = Path().apply { addRect(RectF(-width, -height, width, height), Path.Direction.CW) }
                    fullPath.op(maskPath, Path.Op.DIFFERENCE)
                    canvas.clipPath(fullPath)
                } else {
                    canvas.clipPath(maskPath)
                }
            }

            when (layer.type) {
                OverlayLayerType.IMAGE -> {
                    val bmp = if (!layer.stockImageKey.isNullOrEmpty()) {
                        StockPhotoAssets.getStockBitmap(layer.stockImageKey, lw.toInt().coerceAtLeast(10), lh.toInt().coerceAtLeast(10))
                    } else if (layer.imageUri != null) {
                        try {
                            BitmapFactory.decodeFile(layer.imageUri)
                        } catch (_: Exception) {
                            null
                        }
                    } else null

                    if (bmp != null) {
                        canvas.drawBitmap(bmp, null, layerRect, paint)
                    } else {
                        paint.color = AndroidColor.rgb(45, 52, 65)
                        canvas.drawRoundRect(layerRect, 16f, 16f, paint)
                    }
                }
                OverlayLayerType.ADJUSTMENT -> {
                    when (layer.adjustmentType) {
                        AdjustmentLayerType.BLACK_AND_WHITE -> {
                            val bw = layer.bwSettings
                            val rWeight = (bw.reds / 100f) * 0.3f
                            val yWeight = (bw.yellows / 100f) * 0.2f
                            val gWeight = (bw.greens / 100f) * 0.3f
                            val cWeight = (bw.cyans / 100f) * 0.1f
                            val bWeight = (bw.blues / 100f) * 0.1f
                            val mWeight = (bw.magentas / 100f) * 0.1f
                            val r = rWeight + yWeight / 2f + mWeight / 2f
                            val g = gWeight + yWeight / 2f + cWeight / 2f
                            val b = bWeight + cWeight / 2f + mWeight / 2f

                            val cm = ColorMatrix(floatArrayOf(
                                r, g, b, 0f, 0f,
                                r, g, b, 0f, 0f,
                                r, g, b, 0f, 0f,
                                0f, 0f, 0f, 1f, 0f
                            ))
                            paint.colorFilter = ColorMatrixColorFilter(cm)
                            paint.color = AndroidColor.WHITE
                            canvas.drawRect(layerRect, paint)
                        }
                        AdjustmentLayerType.HUE_SATURATION -> {
                            val cm = ColorMatrix()
                            if (layer.satShift != 0f) {
                                cm.setSaturation((layer.satShift + 100f) / 100f)
                            }
                            if (layer.hueShift != 0f) {
                                val hueCm = ColorMatrix()
                                val cosA = cos(Math.toRadians(layer.hueShift.toDouble())).toFloat()
                                val sinA = sin(Math.toRadians(layer.hueShift.toDouble())).toFloat()
                                val a = 0.213f; val b = 0.715f; val c = 0.072f
                                hueCm.set(floatArrayOf(
                                    a + cosA * (1 - a) + sinA * (-a), b + cosA * (-b) + sinA * (-b), c + cosA * (-c) + sinA * (1 - c), 0f, 0f,
                                    a + cosA * (-a) + sinA * (0.143f), b + cosA * (1 - b) + sinA * (0.140f), c + cosA * (-c) + sinA * (-0.283f), 0f, 0f,
                                    a + cosA * (-a) + sinA * (-(1 - a)), b + cosA * (-b) + sinA * (b), c + cosA * (1 - c) + sinA * (c), 0f, 0f,
                                    0f, 0f, 0f, 1f, 0f
                                ))
                                cm.postConcat(hueCm)
                            }
                            paint.colorFilter = ColorMatrixColorFilter(cm)
                            paint.color = AndroidColor.WHITE
                            canvas.drawRect(layerRect, paint)
                        }
                        else -> {
                            paint.color = AndroidColor.argb(80, 255, 255, 255)
                            canvas.drawRect(layerRect, paint)
                        }
                    }
                }
                OverlayLayerType.FILL -> {
                    paint.color = layer.fillSolidColor.toArgb()
                    canvas.drawRect(layerRect, paint)
                }
                OverlayLayerType.TYPE -> {
                    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = layer.textColor.toArgb()
                        textSize = layer.fontSizeSp * density
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.DEFAULT_BOLD
                        alpha = paint.alpha
                    }
                    if (layer.textBgColor != androidx.compose.ui.graphics.Color.Transparent) {
                        val bgP = Paint().apply { color = layer.textBgColor.toArgb() }
                        canvas.drawRoundRect(layerRect, 12f * density, 12f * density, bgP)
                    }
                    canvas.drawText(layer.textContent, 0f, textPaint.textSize / 3f, textPaint)
                }
                OverlayLayerType.EMPTY -> {}
            }

            if (hasMask) {
                canvas.restore()
            }
            canvas.restore()
        }
    }
}
