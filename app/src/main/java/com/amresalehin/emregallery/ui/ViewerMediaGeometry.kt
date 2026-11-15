package com.amresalehin.emregallery.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal data class FittedMediaDimensions(
    val widthPx: Float,
    val heightPx: Float
)

internal fun computeFittedMediaDimensions(
    intrinsicWidth: Int,
    intrinsicHeight: Int,
    containerWidth: Float,
    containerHeight: Float,
    targetBounds: Rect?
): FittedMediaDimensions {
    val imgW = intrinsicWidth.toFloat()
    val imgH = intrinsicHeight.toFloat()
    val imageAspect = if (imgW > 0f && imgH > 0f) {
        imgW / imgH
    } else if (targetBounds != null && targetBounds.width > 0f && targetBounds.height > 0f) {
        targetBounds.width / targetBounds.height
    } else if (containerHeight > 0f) {
        containerWidth / containerHeight
    } else {
        1f
    }
    val containerAspect = if (containerHeight > 0f) containerWidth / containerHeight else 1f

    if (containerWidth <= 0f || containerHeight <= 0f) {
        return FittedMediaDimensions(widthPx = 0f, heightPx = 0f)
    }

    return if (imageAspect >= containerAspect) {
        FittedMediaDimensions(
            widthPx = containerWidth,
            heightPx = if (imageAspect > 0f) containerWidth / imageAspect else containerHeight
        )
    } else {
        FittedMediaDimensions(
            widthPx = containerHeight * imageAspect,
            heightPx = containerHeight
        )
    }
}

/**
 * Executes uniform shared-element scale, translation, and rounded crop interpolation
 * back to [targetBounds] (or graceful fallback fade/scale when bounds are unavailable).
 */
internal fun Modifier.viewerSharedElementTransition(
    transitionProgress: Float,
    isCurrentPage: Boolean,
    targetBounds: Rect?,
    fittedWidthPx: Float,
    fittedHeightPx: Float,
    containerWidthPx: Float,
    containerHeightPx: Float,
    dismissOffsetY: Float
): Modifier = graphicsLayer {
    val p = transitionProgress.coerceIn(0f, 1f)
    val bounds = if (isCurrentPage) targetBounds else null

    if (bounds != null && fittedWidthPx > 0f && fittedHeightPx > 0f && containerWidthPx > 0f && containerHeightPx > 0f) {
        val thumbW = bounds.width
        val thumbH = bounds.height
        val thumbCenterX = bounds.left + thumbW / 2f
        val thumbCenterY = bounds.top + thumbH / 2f
        val screenCenterX = containerWidthPx / 2f
        val screenCenterY = containerHeightPx / 2f

        val sx = thumbW / fittedWidthPx
        val sy = thumbH / fittedHeightPx
        val targetScale = maxOf(sx, sy).coerceIn(0.001f, 10f)
        val currentScale = targetScale + (1f - targetScale) * p

        val destTransX = thumbCenterX - screenCenterX
        val destTransY = thumbCenterY - screenCenterY
        val currentTransX = destTransX * (1f - p)
        val currentTransY = destTransY * (1f - p) + dismissOffsetY * p

        val currentPhysClipW = thumbW + (fittedWidthPx - thumbW) * p
        val currentPhysClipH = thumbH + (fittedHeightPx - thumbH) * p

        val localClipW = (currentPhysClipW / currentScale).coerceIn(1f, fittedWidthPx)
        val localClipH = (currentPhysClipH / currentScale).coerceIn(1f, fittedHeightPx)

        val targetLocalCornerRadiusPx = 10.dp.toPx() / targetScale
        val currentLocalCornerRadiusPx = targetLocalCornerRadiusPx * (1f - p)

        val clipLeft = (fittedWidthPx - localClipW) / 2f
        val clipTop = (fittedHeightPx - localClipH) / 2f
        val clipRect = Rect(
            left = clipLeft,
            top = clipTop,
            right = clipLeft + localClipW,
            bottom = clipTop + localClipH
        )

        translationX = currentTransX
        translationY = currentTransY
        scaleX = currentScale
        scaleY = currentScale

        clip = p < 0.999f
        shape = object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline = Outline.Rounded(
                RoundRect(
                    rect = clipRect,
                    cornerRadius = CornerRadius(currentLocalCornerRadiusPx)
                )
            )
        }
    } else {
        val currentScale = 0.85f + 0.15f * p
        val exitY = if (dismissOffsetY > 0f) {
            dismissOffsetY + (1f - p) * (containerHeightPx * 0.35f)
        } else {
            0f
        }
        translationX = 0f
        translationY = exitY
        scaleX = currentScale
        scaleY = currentScale
        alpha = if (isCurrentPage) p.coerceIn(0f, 1f) else if (p < 0.99f) 0f else 1f
        clip = p < 0.999f
        shape = androidx.compose.foundation.shape.RoundedCornerShape(((1f - p) * 16f).dp)
    }
}

/**
 * Continuously interpolates image scale factors from cover/crop at the thumbnail end
 * of the shared-element transition to contain/fit at the fullscreen end.
 */
internal fun transitionContentScale(progress: Float): ContentScale {
    val fraction = progress.coerceIn(0f, 1f)
    return object : ContentScale {
        override fun computeScaleFactor(
            srcSize: Size,
            dstSize: Size
        ): ScaleFactor {
            val crop = ContentScale.Crop.computeScaleFactor(srcSize, dstSize)
            val fit = ContentScale.Fit.computeScaleFactor(srcSize, dstSize)
            return ScaleFactor(
                scaleX = crop.scaleX + (fit.scaleX - crop.scaleX) * fraction,
                scaleY = crop.scaleY + (fit.scaleY - crop.scaleY) * fraction
            )
        }
    }
}
