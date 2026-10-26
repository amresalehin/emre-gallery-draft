package com.amresalehin.emregallery.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

/**
 * Tracks on-screen bounding boxes of thumbnails across the gallery screens (Home, Albums, Search, Favourites, Bin).
 * Used to coordinate smooth shared-element return animations when opening or closing the media viewer.
 */
object ThumbnailBoundsTracker {
    private val boundsMap = mutableStateMapOf<String, Rect>()
    private val openingBoundsMap = mutableMapOf<String, Rect?>()

    /**
     * Currently active viewer media item URI.
     * When non-null, the corresponding source thumbnail in the grid can suppress its foreground image
     * to prevent visual duplication during the opening or return animation.
     */
    var activeViewerUri by mutableStateOf<String?>(null)

    fun updateBounds(uri: String, bounds: Rect) {
        if (bounds.width > 5f && bounds.height > 5f) {
            boundsMap[uri] = bounds
        }
    }

    fun getBounds(uri: String): Rect? = boundsMap[uri]

    fun captureOpeningBounds(uri: String) {
        openingBoundsMap[uri] = boundsMap[uri]
    }

    fun getOpeningBounds(uri: String): Rect? {
        // Subscribe to live layout changes, but prefer the exact rectangle captured at tap time.
        val liveBounds = boundsMap[uri]
        return if (openingBoundsMap.containsKey(uri)) openingBoundsMap[uri] else liveBounds
    }

    fun clearOpeningBounds(uri: String) {
        openingBoundsMap.remove(uri)
    }

    fun removeBounds(uri: String) {
        boundsMap.remove(uri)
        openingBoundsMap.remove(uri)
    }

    fun clear() {
        boundsMap.clear()
        openingBoundsMap.clear()
        activeViewerUri = null
    }
}

/**
 * Modifier extension to track the window/root coordinates of a media thumbnail.
 */
fun Modifier.trackThumbnailBounds(uri: String): Modifier = this.onGloballyPositioned { coordinates ->
    if (coordinates.isAttached) {
        val bounds = coordinates.boundsInRoot()
        ThumbnailBoundsTracker.updateBounds(uri, bounds)
    }
}

/**
 * Coordinated top bar entrance modifier.
 * Progressive entrance downward from above screen edge as return progress decreases from 1f to 0f.
 */
fun Modifier.destinationTopBarEntrance(progress: Float): Modifier = this.graphicsLayer {
    if (progress > 0.001f) {
        translationY = -(size.height + 60.dp.toPx()) * progress
        alpha = (1f - progress * 1.15f).coerceIn(0f, 1f)
    }
}

/**
 * Coordinated bottom bar entrance modifier.
 * Progressive entrance upward from below screen edge as return progress decreases from 1f to 0f.
 */
fun Modifier.destinationBottomBarEntrance(progress: Float): Modifier = this.graphicsLayer {
    if (progress > 0.001f) {
        translationY = (size.height + 60.dp.toPx()) * progress
        alpha = (1f - progress * 1.15f).coerceIn(0f, 1f)
    }
}
