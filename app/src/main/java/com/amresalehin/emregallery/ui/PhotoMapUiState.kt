package com.amresalehin.emregallery.ui

import com.amresalehin.emregallery.data.MediaItem
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * State representing the Photo Map screen.
 */
data class PhotoMapUiState(
    val locatedMedia: List<Pair<MediaItem, GeoPoint>> = emptyList(),
    val loading: Boolean = true,
    val scanProgress: Int = 0,
    val scanTotal: Int = 0,
    val mapLayer: String = "Standard",
    val mapAppearance: String = "Day",
    val showMapLayerMenu: Boolean = false,
    val visibleBounds: BoundingBox? = null,
    val canReadOriginalLocation: Boolean = true
) {
    /**
     * Photos currently within the active visible map viewport bounds.
     */
    val viewportMedia: List<Pair<MediaItem, GeoPoint>> by lazy {
        val bounds = visibleBounds ?: return@lazy locatedMedia
        locatedMedia.filter { (_, point) ->
            point.latitude <= bounds.latNorth && point.latitude >= bounds.latSouth &&
                if (bounds.lonWest <= bounds.lonEast) {
                    point.longitude >= bounds.lonWest && point.longitude <= bounds.lonEast
                } else {
                    point.longitude >= bounds.lonWest || point.longitude <= bounds.lonEast
                }
        }
    }

    /**
     * Viewport photos grouped by day of the week, sorted in descending order of capture/creation.
     */
    val dayGroups: List<Pair<String, List<Pair<MediaItem, GeoPoint>>>> by lazy {
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        viewportMedia.groupBy { (item, _) ->
            val millis = if (item.dateTakenMillis > 0) item.dateTakenMillis else item.dateAddedSeconds * 1000
            dayFormat.format(Date(millis))
        }.entries
            .sortedByDescending { entry ->
                entry.value.maxOfOrNull { (item, _) ->
                    if (item.dateTakenMillis > 0) item.dateTakenMillis else item.dateAddedSeconds * 1000
                } ?: 0L
            }
            .map { it.key to it.value }
    }
}
