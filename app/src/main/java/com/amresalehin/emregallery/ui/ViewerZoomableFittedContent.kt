package com.amresalehin.emregallery.ui

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.size.Size
import com.amresalehin.emregallery.data.MediaItem
import kotlin.math.roundToInt


@Composable
internal fun ZoomableFittedMediaContent(
    item: MediaItem,
    imageRequest: Any,
    isCurrentPage: Boolean,
    isVideo: Boolean,
    videoState: ViewerVideoState?,
    intrinsicWidth: Int,
    intrinsicHeight: Int,
    onIntrinsicSizeChange: (Int, Int) -> Unit,
    containerWidth: Float,
    containerHeight: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    transitionProgress: Float,
    targetBounds: androidx.compose.ui.geometry.Rect?,
    dismissOffsetY: Float
) {
    val density = LocalDensity.current
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

            val fittedW: Float
            val fittedH: Float
            if (containerWidth > 0f && containerHeight > 0f) {
                if (imageAspect >= containerAspect) {
                    fittedW = containerWidth
                    fittedH = if (imageAspect > 0f) containerWidth / imageAspect else containerHeight
                } else {
                    fittedH = containerHeight
                    fittedW = containerHeight * imageAspect
                }
            } else {
                fittedW = 0f
                fittedH = 0f
            }

            val fittedWidthDp = if (fittedW > 0f) with(density) { fittedW.toDp() } else 0.dp
            val fittedHeightDp = if (fittedH > 0f) with(density) { fittedH.toDp() } else 0.dp

            // Outer container for user pinch-to-zoom and pan gestures
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },
                contentAlignment = Alignment.Center
            ) {
                val fittedModifier = if (fittedW > 0f && fittedH > 0f) {
                    Modifier.size(fittedWidthDp, fittedHeightDp)
                } else {
                    Modifier.fillMaxSize()
                }

                // Inner fitted container executing uniform shared-element scale, position, and crop to thumbnail
                Box(
                    modifier = fittedModifier
                        .graphicsLayer {
                            val p = transitionProgress.coerceIn(0f, 1f)
                            val bounds = if (isCurrentPage) targetBounds else null

                            if (bounds != null && fittedW > 0f && fittedH > 0f && containerWidth > 0f && containerHeight > 0f) {
                                val thumbW = bounds.width
                                val thumbH = bounds.height
                                val thumbCenterX = bounds.left + thumbW / 2f
                                val thumbCenterY = bounds.top + thumbH / 2f
                                val screenCenterX = containerWidth / 2f
                                val screenCenterY = containerHeight / 2f

                                // Calculate uniform scale to cover the target thumbnail without aspect ratio distortion
                                val sx = thumbW / fittedW
                                val sy = thumbH / fittedH
                                val targetScale = maxOf(sx, sy).coerceIn(0.001f, 10f)

                                val currentScale = targetScale + (1f - targetScale) * p

                                // Translate center to target thumbnail center
                                val destTransX = thumbCenterX - screenCenterX
                                val destTransY = thumbCenterY - screenCenterY
                                val currentTransX = destTransX * (1f - p)
                                val currentTransY = destTransY * (1f - p) + dismissOffsetY * p

                                // Physical clip window on screen interpolating from fitted size down to thumbnail bounds
                                val currentPhysClipW = thumbW + (fittedW - thumbW) * p
                                val currentPhysClipH = thumbH + (fittedH - thumbH) * p

                                // Local unscaled clip window within this Box
                                val localClipW = (currentPhysClipW / currentScale).coerceIn(1f, fittedW)
                                val localClipH = (currentPhysClipH / currentScale).coerceIn(1f, fittedH)

                                // Local corner radius scaling to exactly 10.dp on screen at p = 0
                                val targetLocalCornerRadiusPx = 10.dp.toPx() / targetScale
                                val currentLocalCornerRadiusPx = targetLocalCornerRadiusPx * (1f - p)

                                val clipLeft = (fittedW - localClipW) / 2f
                                val clipTop = (fittedH - localClipH) / 2f
                                val clipRect = androidx.compose.ui.geometry.Rect(
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
                                shape = object : androidx.compose.ui.graphics.Shape {
                                    override fun createOutline(
                                        size: androidx.compose.ui.geometry.Size,
                                        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                                        density: androidx.compose.ui.unit.Density
                                    ): androidx.compose.ui.graphics.Outline {
                                        return androidx.compose.ui.graphics.Outline.Rounded(
                                            androidx.compose.ui.geometry.RoundRect(
                                                rect = clipRect,
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(currentLocalCornerRadiusPx)
                                            )
                                        )
                                    }
                                }
                            } else {
                                val currentScale = 0.85f + 0.15f * p
                                val exitY = if (dismissOffsetY > 0f) {
                                    dismissOffsetY + (1f - p) * (containerHeight * 0.35f)
                                } else {
                                    0f
                                }
                                translationX = 0f
                                translationY = exitY
                                scaleX = currentScale
                                scaleY = currentScale
                                alpha = if (isCurrentPage) p.coerceIn(0f, 1f) else if (p < 0.99f) 0f else 1f
                                clip = p < 0.999f
                                shape = RoundedCornerShape(((1f - p) * 16f).dp)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = item.name,
                        onSuccess = { state ->
                            val d = state.result.drawable
                            val w = if (d.intrinsicWidth > 0) d.intrinsicWidth.toFloat() else state.painter.intrinsicSize.width
                            val h = if (d.intrinsicHeight > 0) d.intrinsicHeight.toFloat() else state.painter.intrinsicSize.height
                            if (w > 0f && h > 0f && !w.isNaN() && !h.isNaN()) {
                                onIntrinsicSizeChange(w.roundToInt(), h.roundToInt())
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    // Video Surface when current item is a video
                    if (isVideo && videoState != null) {
                        VideoPlayerSurface(videoState = videoState)
                    }
                }
            }
}

internal fun transitionContentScale(progress: Float): ContentScale {
    val fraction = progress.coerceIn(0f, 1f)
    return object : ContentScale {
        override fun computeScaleFactor(
            srcSize: androidx.compose.ui.geometry.Size,
            dstSize: androidx.compose.ui.geometry.Size
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

internal fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

internal fun shareMediaItem(context: Context, item: MediaItem) {
    runCatching {
        val parsedUri = Uri.parse(item.uri)
        val mimeType = item.mimeType ?: if (item.isVideo) "video/*" else "image/*"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, parsedUri)
            clipData = ClipData.newUri(context.contentResolver, item.name, parsedUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "Share media").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    }
}
