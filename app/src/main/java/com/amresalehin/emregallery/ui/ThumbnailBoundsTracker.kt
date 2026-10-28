package com.amresalehin.emregallery.ui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
 * Only mounted thumbnails retain LayoutCoordinates. Bounds are computed on demand (or just before
 * the source cell is disposed) and stored in a small LRU so shared-element viewer transitions can
 * still return to a recently visible thumbnail without keeping every scrolled-past composition alive.
 */
object ThumbnailBoundsTracker {
    private const val MAX_CACHED_BOUNDS = 64

    private val coordinatesMap = mutableMapOf<String, LayoutCoordinates>()
    private val cachedBounds = LinkedHashMap<String, Rect>(MAX_CACHED_BOUNDS + 1, 0.75f, true)

    /** Currently active viewer media item URI; hides the duplicate source thumbnail during transitions. */
    var activeViewerUri by mutableStateOf<String?>(null)

    fun registerCoordinates(uri: String, coordinates: LayoutCoordinates) {
        coordinatesMap[uri] = coordinates
    }

    /**
     * Called when the source thumbnail leaves composition. Save its final bounds when still attached,
     * but always release the strong LayoutCoordinates reference to avoid accumulating old list items.
     */
    fun unregisterCoordinates(uri: String, coordinates: LayoutCoordinates) {
        if (coordinatesMap[uri] !== coordinates) return

        if (coordinates.isAttached) {
            runCatching { coordinates.boundsInRoot() }
                .getOrNull()
                ?.takeIf { it.width > 5f && it.height > 5f }
                ?.let { cacheBounds(uri, it) }
        }
        coordinatesMap.remove(uri)
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
 * Track coordinates only while this thumbnail is composed. The holder is deliberately not Compose
 * state, so placement updates do not trigger recomposition.
 */
fun Modifier.trackThumbnailBounds(uri: String): Modifier = composed {
    val coordinatesRef = remember(uri) { arrayOfNulls<LayoutCoordinates>(1) }

    DisposableEffect(uri) {
        onDispose {
            coordinatesRef[0]?.let { ThumbnailBoundsTracker.unregisterCoordinates(uri, it) }
            coordinatesRef[0] = null
        }
    }

    this.onPlaced { coordinates ->
        if (coordinates.isAttached) {
            coordinatesRef[0] = coordinates
            ThumbnailBoundsTracker.registerCoordinates(uri, coordinates)
        }
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
