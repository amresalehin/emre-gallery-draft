package com.amresalehin.emregallery.editor.renderers

import android.graphics.*
import androidx.compose.ui.graphics.toArgb
import com.amresalehin.emregallery.editor.CalloutItem
import com.amresalehin.emregallery.editor.CalloutStyle
import com.amresalehin.emregallery.editor.StickerItem

/**
 * Handles sticker rendering and comic / speech bubble callouts.
 */
object StickerCalloutRenderer {

    fun renderSticker(canvas: Canvas, width: Float, height: Float, stk: StickerItem, density: Float) {
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

    fun renderCallout(canvas: Canvas, width: Float, height: Float, callout: CalloutItem, density: Float) {
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
}
