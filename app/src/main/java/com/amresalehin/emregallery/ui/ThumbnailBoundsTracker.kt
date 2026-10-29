package com.amresalehin.emregallery.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Result data of isotropic shared element transform calculation.
 */
data class SharedElementTransform(
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
    val clipShape: Shape,
    val isClipped: Boolean
)

/**
 * Dynamic RoundRect clip bounds shape that smoothly expands from the thumbnail's exact
 * bounding box and corner radius to the full-screen viewport.
 */
class SharedElementClipShape(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val cornerRadius: Float
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val rect = Rect(
            left = left.coerceIn(0f, size.width),
            top = top.coerceIn(0f, size.height),
            right = right.coerceIn(0f, size.width),
            bottom = bottom.coerceIn(0f, size.height)
        )
        return if (cornerRadius > 0.5f) {
            Outline.Rounded(
                RoundRect(
                    rect = rect,
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )
            )
        } else {
            Outline.Rectangle(rect)
        }
    }
}

/**
 * Calculates isotropic, uniform scaling (scaleX == scaleY) and dynamic clip bounds shape
 * for shared-element transition between thumbnail's centerCrop and full-screen's fitCenter (ContentScale.Fit).
 *
 * Eliminates non-uniform axis scaling distortion and mathematically matches the thumbnail's
 * centerCrop dimensions and offset at progress 0f down to sub-pixel accuracy.
 */
fun calculateSharedElementTransform(
    progress: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    thumbBounds: Rect?,
    imageWidth: Int = 0,
    imageHeight: Int = 0,
    thumbCornerRadiusPx: Float = 0f,
    dismissOffsetY: Float = 0f
): SharedElementTransform {
    val p = progress.coerceIn(0f, 1f)
    if (thumbBounds == null || thumbBounds.width <= 5f || thumbBounds.height <= 5f || viewportWidth <= 0f || viewportHeight <= 0f) {
        val fallbackScale = 0.85f + 0.15f * p
        val fallbackRadius = (1f - p) * 16f
        return SharedElementTransform(
            scale = fallbackScale,
            translationX = 0f,
            translationY = dismissOffsetY * p,
            clipShape = RoundedCornerShape(fallbackRadius.coerceAtLeast(0f)),
            isClipped = p < 0.999f || abs(dismissOffsetY) > 5f
        )
    }

    val thumbW = thumbBounds.width
    val thumbH = thumbBounds.height
    val thumbCenterX = thumbBounds.left + thumbW / 2f
    val thumbCenterY = thumbBounds.top + thumbH / 2f
    val screenCenterX = viewportWidth / 2f
    val screenCenterY = viewportHeight / 2f

    val imgW = if (imageWidth > 0) imageWidth.toFloat() else thumbW
    val imgH = if (imageHeight > 0) imageHeight.toFloat() else thumbH

    val fitScale = min(viewportWidth / imgW, viewportHeight / imgH)
    val cropScale = max(thumbW / imgW, thumbH / imgH)

    val uniformThumbScale = if (fitScale > 0.0001f) {
        (cropScale / fitScale).coerceIn(0.001f, 1f)
    } else {
        (thumbW / viewportWidth).coerceIn(0.001f, 1f)
    }

    // Isotropic, uniform scale factor across all axes:
    val curScale = uniformThumbScale + (1f - uniformThumbScale) * p
    val targetTransX = thumbCenterX - screenCenterX
    val targetTransY = thumbCenterY - screenCenterY
    val curTransX = targetTransX * (1f - p)
    val curTransY = targetTransY * (1f - p) + dismissOffsetY * p

    // Container local coordinates corresponding to thumbnail bounds at p = 0:
    val localW0 = thumbW / uniformThumbScale
    val localH0 = thumbH / uniformThumbScale
    val localLeft0 = screenCenterX - localW0 / 2f
    val localTop0 = screenCenterY - localH0 / 2f
    val localRight0 = screenCenterX + localW0 / 2f
    val localBottom0 = screenCenterY + localH0 / 2f

    // Container local coordinates at p = 1 (full viewport):
    val localLeft1 = 0f
    val localTop1 = 0f
    val localRight1 = viewportWidth
    val localBottom1 = viewportHeight

    val curLeft = localLeft0 + (localLeft1 - localLeft0) * p
    val curTop = localTop0 + (localTop1 - localTop0) * p
    val curRight = localRight0 + (localRight1 - localRight0) * p
    val curBottom = localBottom0 + (localBottom1 - localBottom0) * p

    val effectiveRadiusPx = if (thumbCornerRadiusPx > 0f) thumbCornerRadiusPx else 28f
    val localRadius0 = effectiveRadiusPx / uniformThumbScale
    val curRadius = (localRadius0 * (1f - p)).coerceAtLeast(0f)

    val isClipped = p < 0.999f || abs(dismissOffsetY) > 5f
    val clipShape = SharedElementClipShape(
        left = curLeft,
        top = curTop,
        right = curRight,
        bottom = curBottom,
        cornerRadius = curRadius
    )

    return SharedElementTransform(
        scale = curScale,
        translationX = curTransX,
        translationY = curTransY,
        clipShape = clipShape,
        isClipped = isClipped
    )
}

/**
 * Tracks on-screen bounding boxes of thumbnails across the gallery screens (Home, Albums, Search, Favourites, Bin).
 * Used to coordinate smooth shared-element return animations when opening or closing the media viewer.
 *
 * Optimization: Coordinates are stored onPlaced (only upon item placement) rather than onGloballyPositioned.
 * Bounding boxes are computed strictly on-demand when the media viewer opens or closes, completely eliminating
 * coordinate matrix calculations and Rect allocations during active scrolling.
 */
object ThumbnailBoundsTracker {
    private val coordinatesMap = mutableMapOf<String, LayoutCoordinates>()
    private val cachedBounds = mutableMapOf<String, Rect>()

    /**
     * Currently active viewer media item URI.
     * When non-null, the corresponding source thumbnail in the grid can suppress its foreground image
     * to prevent visual duplication during the opening or return animation.
     */
    var activeViewerUri by mutableStateOf<String?>(null)

    fun registerCoordinates(uri: String, coordinates: LayoutCoordinates) {
        coordinatesMap[uri] = coordinates
    }

    fun updateBounds(uri: String, bounds: Rect) {
        if (bounds.width > 5f && bounds.height > 5f) {
            cachedBounds[uri] = bounds
        }
    }

    fun getBounds(uri: String): Rect? {
        val coords = coordinatesMap[uri]
        if (coords != null && coords.isAttached) {
            val rootBounds = coords.boundsInRoot()
            if (rootBounds.width > 5f && rootBounds.height > 5f) {
                cachedBounds[uri] = rootBounds
                return rootBounds
            }
        }
        return cachedBounds[uri]
    }

    fun removeBounds(uri: String) {
        coordinatesMap.remove(uri)
        cachedBounds.remove(uri)
    }

    fun clear() {
        coordinatesMap.clear()
        cachedBounds.clear()
        activeViewerUri = null
    }
}

/**
 * Modifier extension to track the window/root coordinates of a media thumbnail.
 * Stores lightweight layout coordinates on placement without calculating root bounds until queried.
 */
fun Modifier.trackThumbnailBounds(uri: String): Modifier = this.onPlaced { coordinates ->
    if (coordinates.isAttached) {
        ThumbnailBoundsTracker.registerCoordinates(uri, coordinates)
    }
}

/**
 * Coordinated top bar entrance modifier with progress provider to avoid recomposing caller.
 */
fun Modifier.destinationTopBarEntrance(progressProvider: () -> Float): Modifier = this.graphicsLayer {
    val progress = progressProvider()
    if (progress > 0.001f) {
        translationY = -(size.height + 60.dp.toPx()) * progress
        alpha = (1f - progress * 1.15f).coerceIn(0f, 1f)
    }
}

fun Modifier.destinationTopBarEntrance(progress: Float): Modifier = this.graphicsLayer {
    if (progress > 0.001f) {
        translationY = -(size.height + 60.dp.toPx()) * progress
        alpha = (1f - progress * 1.15f).coerceIn(0f, 1f)
    }
}

/**
 * Coordinated bottom bar entrance modifier with progress provider.
 */
fun Modifier.destinationBottomBarEntrance(progressProvider: () -> Float): Modifier = this.graphicsLayer {
    val progress = progressProvider()
    if (progress > 0.001f) {
        translationY = (size.height + 60.dp.toPx()) * progress
        alpha = (1f - progress * 1.15f).coerceIn(0f, 1f)
    }
}

fun Modifier.destinationBottomBarEntrance(progress: Float): Modifier = this.graphicsLayer {
    if (progress > 0.001f) {
        translationY = (size.height + 60.dp.toPx()) * progress
        alpha = (1f - progress * 1.15f).coerceIn(0f, 1f)
    }
}
