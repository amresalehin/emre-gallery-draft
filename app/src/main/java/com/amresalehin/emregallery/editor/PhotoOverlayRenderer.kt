package com.amresalehin.emregallery.editor

import android.graphics.*
import android.graphics.Color as AndroidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import kotlin.math.*
import kotlin.random.Random

/**
 * Pure offline overlay rendering engine for Borders, Frames, Shapes, Drawing Brushes,
 * Text Typography, Stickers, Callouts, and Optical Lens Flares.
 */
object PhotoOverlayRenderer {

    fun renderAll(canvas: Canvas, width: Float, height: Float, state: EditSessionState, density: Float) {
        // 1. Borders
        renderBorder(canvas, width, height, state.border)

        // 2. Frames
        renderFrame(canvas, width, height, state.frame, density)

        // 3. Shapes
        for (shape in state.shapes) {
            renderShape(canvas, width, height, shape, density)
        }

        // 4. Drawing Strokes
        for (stroke in state.drawingStrokes) {
            renderBrushStroke(canvas, stroke, density)
        }

        // 5. Text Overlays
        for (textItem in state.textOverlays) {
            renderTextOverlay(canvas, width, height, textItem, density)
        }

        // 6. Stickers
        for (stk in state.stickers) {
            renderSticker(canvas, width, height, stk, density)
        }

        // 7. Callouts
        for (callout in state.callouts) {
            renderCallout(canvas, width, height, callout, density)
        }

        // 8. Lens Flares
        for (flare in state.lensFlares) {
            renderLensFlare(canvas, width, height, flare, density)
        }
    }

    private fun renderBorder(canvas: Canvas, width: Float, height: Float, border: BorderSettings) {
        if (border.widthRatio <= 0f) return
        val borderWidth = border.widthRatio * min(width, height)
        val padding = border.paddingRatio * min(width, height)
        val radius = border.cornerRadiusRatio * min(width, height)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = border.color.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = borderWidth
        }
        val inset = padding + borderWidth / 2f
        val rect = RectF(inset, inset, width - inset, height - inset)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
    }

    private fun renderFrame(canvas: Canvas, width: Float, height: Float, frame: FrameStyle, density: Float) {
        when (frame) {
            FrameStyle.NONE -> {}
            FrameStyle.POLAROID -> {
                val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    style = Paint.Style.FILL
                }
                val borderSide = width * 0.06f
                val borderTop = height * 0.06f
                val borderBottom = height * 0.18f
                canvas.drawRect(0f, 0f, width, borderTop, cardPaint)
                canvas.drawRect(0f, 0f, borderSide, height, cardPaint)
                canvas.drawRect(width - borderSide, 0f, width, height, cardPaint)
                canvas.drawRect(0f, height - borderBottom, width, height, cardPaint)
            }
            FrameStyle.FILM_STRIP -> {
                val filmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.BLACK }
                val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
                val barW = width * 0.08f
                canvas.drawRect(0f, 0f, barW, height, filmPaint)
                canvas.drawRect(width - barW, 0f, width, height, filmPaint)

                val holeH = 18f * density
                val holeW = barW * 0.5f
                var y = 16f * density
                while (y < height - holeH) {
                    canvas.drawRoundRect(RectF(barW * 0.25f, y, barW * 0.25f + holeW, y + holeH), 4f, 4f, holePaint)
                    canvas.drawRoundRect(RectF(width - barW + barW * 0.25f, y, width - barW * 0.25f, y + holeH), 4f, 4f, holePaint)
                    y += holeH + 14f * density
                }
            }
            FrameStyle.MODERN_WHITE -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = min(width, height) * 0.05f
                }
                val inset = p.strokeWidth / 2f
                canvas.drawRect(inset, inset, width - inset, height - inset, p)
            }
            FrameStyle.DOUBLE_BORDER -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * density
                }
                canvas.drawRect(12f * density, 12f * density, width - 12f * density, height - 12f * density, p)
                canvas.drawRect(22f * density, 22f * density, width - 22f * density, height - 22f * density, p)
            }
            FrameStyle.VINTAGE_FADED, FrameStyle.MINIMAL_BLACK -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (frame == FrameStyle.MINIMAL_BLACK) AndroidColor.BLACK else AndroidColor.DKGRAY
                    style = Paint.Style.STROKE
                    strokeWidth = min(width, height) * 0.04f
                }
                val inset = p.strokeWidth / 2f
                canvas.drawRect(inset, inset, width - inset, height - inset, p)
            }
            FrameStyle.ROUNDED_FRAME -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 16f * density
                }
                canvas.drawRoundRect(RectF(8f * density, 8f * density, width - 8f * density, height - 8f * density), 32f * density, 32f * density, p)
            }
            FrameStyle.DECORATIVE_VINTAGE -> {
                val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.rgb(212, 175, 55)
                    style = Paint.Style.STROKE
                    strokeWidth = 8f * density
                }
                canvas.drawRect(16f * density, 16f * density, width - 16f * density, height - 16f * density, goldPaint)
                goldPaint.strokeWidth = 2f * density
                canvas.drawRect(24f * density, 24f * density, width - 24f * density, height - 24f * density, goldPaint)
            }
            FrameStyle.CAMERA_VIEWFINDER -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * density
                }
                val cornerLen = 24f * density
                val margin = 20f * density
                // 4 viewfinder corners
                canvas.drawLine(margin, margin, margin + cornerLen, margin, p)
                canvas.drawLine(margin, margin, margin, margin + cornerLen, p)
                canvas.drawLine(width - margin, margin, width - margin - cornerLen, margin, p)
                canvas.drawLine(width - margin, margin, width - margin, margin + cornerLen, p)
                canvas.drawLine(margin, height - margin, margin + cornerLen, height - margin, p)
                canvas.drawLine(margin, height - margin, margin, height - margin - cornerLen, p)
                canvas.drawLine(width - margin, height - margin, width - margin - cornerLen, height - margin, p)
                canvas.drawLine(width - margin, height - margin, width - margin, height - margin - cornerLen, p)
            }
            FrameStyle.INSTAX_MINI, FrameStyle.SX70_VINTAGE -> {
                val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(250, 248, 245) }
                val border = width * 0.05f
                val bot = height * 0.22f
                canvas.drawRect(0f, 0f, width, border, cardPaint)
                canvas.drawRect(0f, 0f, border, height, cardPaint)
                canvas.drawRect(width - border, 0f, width, height, cardPaint)
                canvas.drawRect(0f, height - bot, width, height, cardPaint)
            }
            FrameStyle.GRADIENT_BORDER -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, 0f, width, height, intArrayOf(AndroidColor.CYAN, AndroidColor.MAGENTA, AndroidColor.YELLOW), null, Shader.TileMode.CLAMP)
                    style = Paint.Style.STROKE
                    strokeWidth = 10f * density
                }
                val inset = 5f * density
                canvas.drawRect(inset, inset, width - inset, height - inset, p)
            }
            FrameStyle.PATTERN_STITCH -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * density
                    pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
                }
                canvas.drawRect(16f * density, 16f * density, width - 16f * density, height - 16f * density, p)
            }
            FrameStyle.PATTERN_CHAIN -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.rgb(220, 180, 80)
                    style = Paint.Style.STROKE
                    strokeWidth = 4f * density
                    pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
                }
                canvas.drawRect(18f * density, 18f * density, width - 18f * density, height - 18f * density, p)
            }
            FrameStyle.SHADOW_FLOAT -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.argb(120, 0, 0, 0)
                    style = Paint.Style.STROKE
                    strokeWidth = 8f * density
                    maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawRect(16f * density, 16f * density, width - 16f * density, height - 16f * density, p)
            }
        }
    }

    private fun renderShape(canvas: Canvas, width: Float, height: Float, shape: ShapeItem, density: Float) {
        val cx = shape.xRatio * width
        val cy = shape.yRatio * height
        val sw = shape.widthRatio * width
        val sh = shape.heightRatio * height

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (shape.gradientFill != null && shape.gradientFill.size >= 2) {
                shader = LinearGradient(
                    cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f,
                    shape.gradientFill.map { it.toArgb() }.toIntArray(), null, Shader.TileMode.CLAMP
                )
            } else {
                color = shape.fillColor.toArgb()
            }
            style = Paint.Style.FILL
        }

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shape.strokeColor.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = shape.strokeWidth * density
        }

        canvas.save()
        canvas.rotate(shape.rotation, cx, cy)

        val path = Path()
        when (shape.shapeType) {
            ShapeType.RECTANGLE -> {
                val rect = RectF(cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f)
                path.addRoundRect(rect, shape.cornerRadius * density, shape.cornerRadius * density, Path.Direction.CW)
            }
            ShapeType.ROUNDED_RECTANGLE -> {
                val rect = RectF(cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f)
                path.addRoundRect(rect, 24f * density, 24f * density, Path.Direction.CW)
            }
            ShapeType.CIRCLE -> {
                path.addCircle(cx, cy, min(sw, sh) / 2f, Path.Direction.CW)
            }
            ShapeType.TRIANGLE -> {
                path.moveTo(cx, cy - sh / 2f)
                path.lineTo(cx + sw / 2f, cy + sh / 2f)
                path.lineTo(cx - sw / 2f, cy + sh / 2f)
                path.close()
            }
            ShapeType.POLYGON -> {
                val sides = shape.polygonSides.coerceIn(3, 12)
                val r = min(sw, sh) / 2f
                for (i in 0 until sides) {
                    val angle = (i * (360.0 / sides) - 90) * Math.PI / 180.0
                    val x = (cx + r * cos(angle)).toFloat()
                    val y = (cy + r * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            ShapeType.LINE -> {
                path.moveTo(cx - sw / 2f, cy)
                path.lineTo(cx + sw / 2f, cy)
            }
            ShapeType.CURVED_LINE -> {
                path.moveTo(cx - sw / 2f, cy)
                path.quadTo(cx, cy - sh / 2f, cx + sw / 2f, cy)
            }
            ShapeType.STAR -> {
                val rOuter = min(sw, sh) / 2f
                val rInner = rOuter * 0.45f
                for (i in 0 until 10) {
                    val angle = (i * 36 - 90) * Math.PI / 180.0
                    val r = if (i % 2 == 0) rOuter else rInner
                    val x = (cx + r * cos(angle)).toFloat()
                    val y = (cy + r * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            ShapeType.HEART -> {
                val hw = sw / 2f
                val hh = sh / 2f
                path.moveTo(cx, cy + hh * 0.6f)
                path.cubicTo(cx - hw, cy, cx - hw, cy - hh, cx, cy - hh * 0.4f)
                path.cubicTo(cx + hw, cy - hh, cx + hw, cy, cx, cy + hh * 0.6f)
                path.close()
            }
            ShapeType.HEXAGON -> {
                val r = min(sw, sh) / 2f
                for (i in 0 until 6) {
                    val angle = (i * 60) * Math.PI / 180.0
                    val x = (cx + r * cos(angle)).toFloat()
                    val y = (cy + r * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            ShapeType.ARROW -> {
                path.moveTo(cx - sw / 2f, cy - sh * 0.15f)
                path.lineTo(cx + sw * 0.1f, cy - sh * 0.15f)
                path.lineTo(cx + sw * 0.1f, cy - sh * 0.45f)
                path.lineTo(cx + sw / 2f, cy)
                path.lineTo(cx + sw * 0.1f, cy + sh * 0.45f)
                path.lineTo(cx + sw * 0.1f, cy + sh * 0.15f)
                path.lineTo(cx - sw / 2f, cy + sh * 0.15f)
                path.close()
            }
            ShapeType.FREEFORM -> {
                path.moveTo(cx - sw / 2f, cy)
                path.cubicTo(cx - sw / 4f, cy - sh / 2f, cx + sw / 4f, cy + sh / 2f, cx + sw / 2f, cy)
            }
        }

        if (shape.fillColor != androidx.compose.ui.graphics.Color.Transparent || shape.gradientFill != null) {
            canvas.drawPath(path, fillPaint)
        }
        if (shape.strokeWidth > 0f) {
            canvas.drawPath(path, strokePaint)
        }
        canvas.restore()
    }

    private fun renderBrushStroke(canvas: Canvas, stroke: DrawingStroke, density: Float) {
        if (stroke.points.size < 2) return
        val path = Path()
        stroke.points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }

        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color.toArgb()
            strokeWidth = stroke.width * density
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        when (stroke.brushType) {
            BrushType.PEN, BrushType.CALLIGRAPHY -> {
                if (stroke.brushType == BrushType.CALLIGRAPHY) {
                    basePaint.strokeCap = Paint.Cap.SQUARE
                }
                canvas.drawPath(path, basePaint)
            }
            BrushType.GLOW -> {
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stroke.color.copy(alpha = 0.5f).toArgb()
                    strokeWidth = (stroke.width * 2.8f) * density
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawPath(path, glowPaint)
                val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    strokeWidth = (stroke.width * 0.8f) * density
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawPath(path, corePaint)
            }
            BrushType.HIGHLIGHTER -> {
                basePaint.alpha = (stroke.color.alpha * 0.45f * 255).toInt()
                basePaint.strokeWidth = stroke.width * 2.2f * density
                basePaint.strokeCap = Paint.Cap.SQUARE
                canvas.drawPath(path, basePaint)
            }
            BrushType.DOTTED -> {
                basePaint.pathEffect = DashPathEffect(floatArrayOf(10f, 14f), 0f)
                canvas.drawPath(path, basePaint)
            }
            BrushType.ERASER -> {
                basePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                basePaint.strokeWidth = stroke.width * 2f * density
                canvas.drawPath(path, basePaint)
            }
            BrushType.PENCIL -> {
                basePaint.strokeWidth = stroke.width * 0.7f * density
                basePaint.alpha = 210
                basePaint.pathEffect = DiscretePathEffect(4f, 2f)
                canvas.drawPath(path, basePaint)
            }
            BrushType.SOFT_AIRBRUSH, BrushType.BLUR -> {
                basePaint.alpha = 60
                basePaint.maskFilter = BlurMaskFilter(stroke.width * density * 0.6f, BlurMaskFilter.Blur.NORMAL)
                canvas.drawPath(path, basePaint)
            }
            BrushType.HARD_AIRBRUSH -> {
                basePaint.alpha = 150
                canvas.drawPath(path, basePaint)
            }
            BrushType.SPRAY_PAINT, BrushType.PIXEL -> {
                val sprayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stroke.color.toArgb()
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
                basePaint.alpha = 90
                basePaint.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
                canvas.drawPath(path, basePaint)
            }
            BrushType.OIL -> {
                basePaint.strokeWidth = stroke.width * 1.4f * density
                basePaint.pathEffect = DiscretePathEffect(6f, 3f)
                canvas.drawPath(path, basePaint)
            }
            BrushType.TEXTURE, BrushType.PATTERN, BrushType.SMUDGE, BrushType.SHAPE -> {
                basePaint.alpha = 180
                canvas.drawPath(path, basePaint)
            }
        }
    }

    private fun renderTextOverlay(canvas: Canvas, width: Float, height: Float, item: TextOverlayItem, density: Float) {
        val cx = item.xRatio * width
        val cy = item.yRatio * height
        val fontSizePx = item.fontSizeSp * density * item.scale

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontSizePx
            textAlign = when (item.alignment) {
                TextAlignment.LEFT -> Paint.Align.LEFT
                TextAlignment.CENTER -> Paint.Align.CENTER
                TextAlignment.RIGHT -> Paint.Align.RIGHT
            }
            letterSpacing = item.characterSpacing / 10f

            val style = if (item.isBold) Typeface.BOLD else Typeface.NORMAL
            typeface = when (item.fontFamily) {
                TextFontFamily.DEFAULT -> Typeface.create(Typeface.DEFAULT, style)
                TextFontFamily.SANS_SERIF -> Typeface.create(Typeface.SANS_SERIF, style)
                TextFontFamily.SERIF -> Typeface.create(Typeface.SERIF, style)
                TextFontFamily.MONOSPACE -> Typeface.create(Typeface.MONOSPACE, style)
                TextFontFamily.CURSIVE, TextFontFamily.HANDWRITING -> Typeface.create("cursive", style)
                TextFontFamily.DISPLAY, TextFontFamily.TECH -> Typeface.create("sans-serif-black", style)
            }

            if (item.gradientColors != null && item.gradientColors.size >= 2) {
                shader = LinearGradient(
                    cx - 100f, cy, cx + 100f, cy,
                    item.gradientColors.map { it.toArgb() }.toIntArray(), null, Shader.TileMode.CLAMP
                )
            } else {
                color = item.color.toArgb()
            }
        }

        // Shadow or Glow
        if (item.glowColor != null) {
            textPaint.setShadowLayer(item.glowRadius * density, 0f, 0f, item.glowColor.toArgb())
        } else if (item.shadowColor != null) {
            textPaint.setShadowLayer(item.shadowRadius * density, item.shadowOffset.x * density, item.shadowOffset.y * density, item.shadowColor.toArgb())
        }

        canvas.save()
        canvas.rotate(item.rotation, cx, cy)

        if (item.curvedArcDegrees != 0f) {
            // Draw on curved arc path
            val arcPath = Path()
            val radius = width * 0.45f
            val arcRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            arcPath.addArc(arcRect, 180f - item.curvedArcDegrees / 2f, item.curvedArcDegrees)
            canvas.drawTextOnPath(item.text, arcPath, 0f, 0f, textPaint)
        } else {
            val bounds = Rect()
            textPaint.getTextBounds(item.text, 0, item.text.length, bounds)

            // Background box if present
            if (item.backgroundColor.alpha > 0.05f) {
                val padX = 14f * density * item.scale
                val padY = 8f * density * item.scale
                val bgRect = RectF(
                    cx - bounds.width() / 2f - padX,
                    cy - bounds.height() / 2f - padY,
                    cx + bounds.width() / 2f + padX,
                    cy + bounds.height() / 2f + padY
                )
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = item.backgroundColor.toArgb()
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(bgRect, 12f * density, 12f * density, bgPaint)
            }

            // Stroke outline if present
            if (item.strokeWidth > 0f) {
                val strokePaint = Paint(textPaint).apply {
                    shader = null
                    color = item.strokeColor.toArgb()
                    style = Paint.Style.STROKE
                    strokeWidth = item.strokeWidth * density
                }
                canvas.drawText(item.text, cx, cy + bounds.height() / 3f, strokePaint)
            }

            if (!item.isOutlineOnly) {
                canvas.drawText(item.text, cx, cy + bounds.height() / 3f, textPaint)
            }
        }

        canvas.restore()
    }

    private fun renderSticker(canvas: Canvas, width: Float, height: Float, stk: StickerItem, density: Float) {
        val cx = stk.xRatio * width
        val cy = stk.yRatio * height
        val size = stk.sizeDp * density * stk.scale

        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            textAlign = Paint.Align.CENTER
        }
        if (stk.shadowRadius > 0f) {
            emojiPaint.setShadowLayer(stk.shadowRadius * density, 2f, 2f, stk.shadowColor.toArgb())
        }
        if (stk.tintColor != null) {
            emojiPaint.colorFilter = PorterDuffColorFilter(stk.tintColor.toArgb(), PorterDuff.Mode.SRC_IN)
        }

        canvas.save()
        canvas.rotate(stk.rotation, cx, cy)
        canvas.drawText(stk.emojiOrRes, cx, cy + size * 0.35f, emojiPaint)
        canvas.restore()
    }

    private fun renderCallout(canvas: Canvas, width: Float, height: Float, callout: CalloutItem, density: Float) {
        val cx = callout.xRatio * width
        val cy = callout.yRatio * height
        val tailX = callout.tailPointRatio.x * width
        val tailY = callout.tailPointRatio.y * height

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = callout.textColor.toArgb()
            textSize = 18f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        textPaint.getTextBounds(callout.text, 0, callout.text.length, bounds)

        val bubbleW = bounds.width() + 32f * density
        val bubbleH = bounds.height() + 24f * density
        val rect = RectF(cx - bubbleW / 2f, cy - bubbleH / 2f, cx + bubbleW / 2f, cy + bubbleH / 2f)

        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = callout.backgroundColor.toArgb()
            style = Paint.Style.FILL
        }

        when (callout.style) {
            CalloutStyle.SPEECH_BUBBLE, CalloutStyle.DUAL_TAIL -> {
                canvas.drawRoundRect(rect, 20f * density, 20f * density, bubblePaint)
                val tailPath = Path().apply {
                    moveTo(cx - 10f * density, cy + bubbleH / 2f - 2f)
                    lineTo(cx + 10f * density, cy + bubbleH / 2f - 2f)
                    lineTo(tailX, tailY)
                    close()
                }
                canvas.drawPath(tailPath, bubblePaint)
                if (callout.style == CalloutStyle.DUAL_TAIL && callout.tailPoint2Ratio != null) {
                    val tail2Path = Path().apply {
                        moveTo(cx - 5f * density, cy + bubbleH / 2f - 2f)
                        lineTo(cx + 5f * density, cy + bubbleH / 2f - 2f)
                        lineTo(callout.tailPoint2Ratio.x * width, callout.tailPoint2Ratio.y * height)
                        close()
                    }
                    canvas.drawPath(tail2Path, bubblePaint)
                }
            }
            CalloutStyle.THOUGHT_BUBBLE, CalloutStyle.CLOUD_BUBBLE -> {
                canvas.drawRoundRect(rect, 24f * density, 24f * density, bubblePaint)
                // Thought trailing dots
                val dot1X = cx + (tailX - cx) * 0.4f
                val dot1Y = cy + (tailY - cy) * 0.4f
                val dot2X = cx + (tailX - cx) * 0.75f
                val dot2Y = cy + (tailY - cy) * 0.75f
                canvas.drawCircle(dot1X, dot1Y, 7f * density, bubblePaint)
                canvas.drawCircle(dot2X, dot2Y, 4f * density, bubblePaint)
            }
            CalloutStyle.NUMBERED_CALLOUT -> {
                canvas.drawCircle(cx, cy, 18f * density, bubblePaint)
                val pinStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = callout.textColor.toArgb()
                    style = Paint.Style.STROKE
                    strokeWidth = 2f * density
                }
                canvas.drawCircle(cx, cy, 18f * density, pinStroke)
            }
            CalloutStyle.ARROW_CALLOUT, CalloutStyle.LABEL_CALLOUT, CalloutStyle.COMIC_BURST -> {
                canvas.drawRoundRect(rect, 10f * density, 10f * density, bubblePaint)
            }
        }
        canvas.drawText(callout.text, cx, cy + bounds.height() / 3f, textPaint)
    }

    private fun renderLensFlare(canvas: Canvas, width: Float, height: Float, flare: LensFlareItem, density: Float) {
        val fx = flare.xRatio * width
        val fy = flare.yRatio * height
        val intensity = flare.intensity

        canvas.save()
        val flarePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (flare.flareType) {
            LensFlareType.SUNBURST, LensFlareType.SUN_RAYS -> {
                flarePaint.shader = RadialGradient(
                    fx, fy, 130f * density * intensity,
                    intArrayOf(AndroidColor.WHITE, AndroidColor.argb((intensity * 180).toInt(), 255, 230, 150), AndroidColor.TRANSPARENT),
                    floatArrayOf(0f, 0.35f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(fx, fy, 130f * density * intensity, flarePaint)

                val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.argb((intensity * 120).toInt(), 255, 240, 200)
                    strokeWidth = 2.5f * density
                    style = Paint.Style.STROKE
                }
                val step = if (flare.flareType == LensFlareType.SUN_RAYS) 15 else 30
                for (deg in 0 until 360 step step) {
                    val rad = deg * Math.PI / 180.0
                    val len = (90f + (deg % 60) * 2f) * density * intensity
                    canvas.drawLine(fx, fy, (fx + len * cos(rad)).toFloat(), (fy + len * sin(rad)).toFloat(), rayPaint)
                }
            }
            LensFlareType.ANAMORPHIC, LensFlareType.ANAMORPHIC_STREAKS -> {
                val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        fx - width * 0.45f, fy, fx + width * 0.45f, fy,
                        intArrayOf(AndroidColor.TRANSPARENT, AndroidColor.argb((intensity * 220).toInt(), 100, 200, 255), AndroidColor.WHITE, AndroidColor.argb((intensity * 220).toInt(), 100, 200, 255), AndroidColor.TRANSPARENT),
                        floatArrayOf(0f, 0.4f, 0.5f, 0.6f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    strokeWidth = 14f * density * intensity
                }
                canvas.drawLine(fx - width * 0.45f, fy, fx + width * 0.45f, fy, streakPaint)
            }
            LensFlareType.GOLDEN_HALO, LensFlareType.GLOW_BLOOM -> {
                val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.argb((intensity * 160).toInt(), 255, 190, 80)
                    style = Paint.Style.STROKE
                    strokeWidth = 5f * density
                }
                canvas.drawCircle(fx, fy, 80f * density * intensity, haloPaint)
                canvas.drawCircle(fx, fy, 140f * density * intensity, haloPaint)
            }
            LensFlareType.RAINBOW_RING, LensFlareType.PRISM_REFRACTION -> {
                val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = SweepGradient(
                        fx, fy,
                        intArrayOf(AndroidColor.RED, AndroidColor.YELLOW, AndroidColor.GREEN, AndroidColor.CYAN, AndroidColor.MAGENTA, AndroidColor.RED),
                        null
                    )
                    style = Paint.Style.STROKE
                    strokeWidth = 8f * density * intensity
                    alpha = (intensity * 140).toInt()
                }
                canvas.drawCircle(fx, fy, 100f * density * intensity, ringPaint)
            }
            LensFlareType.LENS_GHOSTS -> {
                // Secondary optical reflection discs along line through center
                val cx = width / 2f
                val cy = height / 2f
                val dx = cx - fx
                val dy = cy - fy
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    alpha = (intensity * 100).toInt()
                }
                p.color = AndroidColor.argb((intensity * 80).toInt(), 100, 255, 150)
                canvas.drawCircle(cx + dx * 0.3f, cy + dy * 0.3f, 25f * density, p)
                p.color = AndroidColor.argb((intensity * 80).toInt(), 255, 100, 200)
                canvas.drawCircle(cx + dx * 0.6f, cy + dy * 0.6f, 40f * density, p)
                p.color = AndroidColor.argb((intensity * 80).toInt(), 100, 180, 255)
                canvas.drawCircle(cx + dx * 1.1f, cy + dy * 1.1f, 18f * density, p)
            }
            LensFlareType.LIGHT_LEAK -> {
                flarePaint.shader = RadialGradient(
                    0f, 0f, width * 0.7f,
                    intArrayOf(AndroidColor.argb((intensity * 180).toInt(), 255, 120, 50), AndroidColor.TRANSPARENT),
                    floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
                )
                canvas.drawCircle(0f, 0f, width * 0.7f, flarePaint)
            }
            LensFlareType.BOKEH_LIGHTS -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    alpha = (intensity * 120).toInt()
                    style = Paint.Style.FILL
                }
                val random = Random(42)
                for (i in 0 until 12) {
                    val bx = fx + random.nextInt(-150, 150) * density
                    val by = fy + random.nextInt(-150, 150) * density
                    val br = random.nextInt(12, 35) * density * intensity
                    canvas.drawCircle(bx, by, br, p)
                }
            }
            LensFlareType.STAR_FILTER -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.argb((intensity * 200).toInt(), 255, 255, 255)
                    strokeWidth = 2f * density
                }
                val r = 60f * density * intensity
                canvas.drawLine(fx - r, fy, fx + r, fy, p)
                canvas.drawLine(fx, fy - r, fx, fy + r, p)
                canvas.drawLine(fx - r * 0.7f, fy - r * 0.7f, fx + r * 0.7f, fy + r * 0.7f, p)
                canvas.drawLine(fx - r * 0.7f, fy + r * 0.7f, fx + r * 0.7f, fy - r * 0.7f, p)
            }
        }
        canvas.restore()
    }
}
