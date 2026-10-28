package com.amresalehin.emregallery.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import java.util.LinkedHashMap

/**
 * Tracks on-screen bounding boxes of thumbnails across gallery screens.
 *
 * Only mounted thumbnails retain LayoutCoordinates. Bounds are captured on demand when a thumbnail
 * is opened and stored in a small LRU so shared-element viewer transitions can return to a recently
 * visible thumbnail without keeping every scrolled-past composition alive or measuring rows on disposal.
 */
object ThumbnailBoundsTracker {
    private const val MAX_CACHED_BOUNDS = 64

    // Bound both maps independently. An access-ordered map keeps currently/recently placed cells,
    // but never grows with the total number of photos visited during a long scroll session.
    private val coordinatesMap = object : LinkedHashMap<String, LayoutCoordinates>(MAX_CACHED_BOUNDS + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LayoutCoordinates>?): Boolean =
            size > MAX_CACHED_BOUNDS
    }
    private val cachedBounds = LinkedHashMap<String, Rect>(MAX_CACHED_BOUNDS + 1, 0.75f, true)

    /** Currently active viewer media item URI; hides the duplicate source thumbnail during transitions. */
    var activeViewerUri by mutableStateOf<String?>(null)

    fun registerCoordinates(uri: String, coordinates: LayoutCoordinates) {
        coordinatesMap[uri] = coordinates
    }

    /**
     * Called when the source thumbnail leaves composition. Do not calculate bounds here: lazy-list
     * disposal happens during scroll, so doing coordinate transforms for every recycled row would
     * put avoidable work back on the frame-critical path. Click handlers snapshot the selected item's
     * bounds before navigation.
     */
    fun unregisterCoordinates(uri: String, coordinates: LayoutCoordinates) {
        if (coordinatesMap[uri] === coordinates) coordinatesMap.remove(uri)
    }

    fun updateBounds(uri: String, bounds: Rect) {
        if (bounds.width > 5f && bounds.height > 5f) cacheBounds(uri, bounds)
    }

    /** Snapshot the source bounds before a click navigates away and disposes its cell. */
    fun captureBounds(uri: String): Rect? = getBounds(uri)

    fun getBounds(uri: String): Rect? {
        val coords = coordinatesMap[uri]
        if (coords != null && coords.isAttached) {
            val rootBounds = runCatching { coords.boundsInRoot() }.getOrNull()
            if (rootBounds != null && rootBounds.width > 5f && rootBounds.height > 5f) {
                cacheBounds(uri, rootBounds)
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

    private fun cacheBounds(uri: String, bounds: Rect) {
        cachedBounds[uri] = bounds
        while (cachedBounds.size > MAX_CACHED_BOUNDS) {
            val eldest = cachedBounds.entries.iterator()
            if (eldest.hasNext()) {
                eldest.next()
                eldest.remove()
            } else {
                break
            }
        }
    }
}

/**
 * Track thumbnail coordinates with a single placement callback. Avoid Modifier.composed and per-cell
 * lifecycle effects in this hot path; the tracker itself bounds coordinate references to 64 entries.
 */
fun Modifier.trackThumbnailBounds(uri: String): Modifier = this.onPlaced { coordinates ->
    if (coordinates.isAttached) {
        ThumbnailBoundsTracker.registerCoordinates(uri, coordinates)
    }
}

/** Coordinated top bar entrance modifier with progress provider to avoid recomposing caller. */
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

/** Coordinated bottom bar entrance modifier with progress provider. */
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
