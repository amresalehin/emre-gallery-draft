package com.amresalehin.emregallery.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.vector.ImageVector
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class SuggestionType {
    RECENT,
    PERSON,
    PLACE,
    ALBUM,
    FILE,
    TAG,
    DOCUMENT
}

data class SearchSuggestion(
    val id: String,
    val type: SuggestionType,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUri: String? = null,
    val isPersonPortrait: Boolean = false,
    val personFaceIndex: Int? = null,
    val targetQuery: String? = null,
    val targetFilter: SearchFilterState? = null
)

data class DiscoveryCategory(
    val id: String,
    val title: String,
    val itemCount: Int,
    val sampleUris: List<String>,
    val queryAction: String? = null,
    val filterAction: SearchFilterState? = null
)

data class DiscoveryEntityItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUri: String? = null,
    val isPerson: Boolean = false,
    val personFaceIndex: Int? = null,
    val queryAction: String? = null,
    val filterAction: SearchFilterState? = null
)

enum class MediaTypeFilter(val label: String) {
    ALL("All media"),
    PHOTOS("Images"),
    VIDEOS("Videos"),
    GIFS("Gifs");

    companion object {
        val IMAGES = PHOTOS
    }
}

enum class GpsFilter(val label: String) {
    ALL("All"),
    HAS_GPS("GPS"),
    NO_GPS("No GPS")
}

enum class OcrTextFilter(val label: String) {
    ALL("All"),
    HAS_TEXT("Text"),
    NO_TEXT("Without Text")
}

enum class FaceFilter(val label: String) {
    ALL("All"),
    HAS_FACE("Face"),
    NO_FACE("No Face")
}

sealed class TimeRangeFilter(val label: String) {
    object AllTime : TimeRangeFilter("Time")
    object Last30Days : TimeRangeFilter("Last 30 days")
    object Last90Days : TimeRangeFilter("Last 90 days")
    data class Custom(val startMillis: Long, val endMillis: Long) : TimeRangeFilter("Custom date")
}

enum class SearchSortMode(val label: String) {
    RELEVANCE("Most relevant"),
    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first"),
    NAME("Name A–Z")
}

enum class SearchViewMode(val label: String) {
    MASONRY("Masonry"),
    GRID("Grid (3×3)"),
    LARGE_GRID("Large grid (2×2)"),
    LIST("List")
}

data class SearchFilterState(
    val mediaType: MediaTypeFilter = MediaTypeFilter.ALL,
    val timeRange: TimeRangeFilter = TimeRangeFilter.AllTime,
    val favoritesOnly: Boolean = false,
    val selectedAlbumId: String? = null,
    val selectedPersonId: String? = null,
    val selectedPlace: String? = null,
    val selectedTag: String? = null,
    val gpsFilter: GpsFilter = GpsFilter.ALL,
    val ocrTextFilter: OcrTextFilter = OcrTextFilter.ALL,
    val faceFilter: FaceFilter = FaceFilter.ALL
) {
    val isDefault: Boolean
        get() = mediaType == MediaTypeFilter.ALL &&
                timeRange is TimeRangeFilter.AllTime &&
                !favoritesOnly &&
                selectedAlbumId == null &&
                selectedPersonId == null &&
                selectedPlace == null &&
                selectedTag == null &&
                gpsFilter == GpsFilter.ALL &&
                ocrTextFilter == OcrTextFilter.ALL &&
                faceFilter == FaceFilter.ALL

    val activeFilterCount: Int
        get() {
            var count = 0
            if (mediaType != MediaTypeFilter.ALL) count++
            if (timeRange !is TimeRangeFilter.AllTime) count++
            if (favoritesOnly) count++
            if (selectedAlbumId != null) count++
            if (selectedPersonId != null) count++
            if (selectedPlace != null) count++
            if (selectedTag != null) count++
            if (gpsFilter != GpsFilter.ALL) count++
            if (ocrTextFilter != OcrTextFilter.ALL) count++
            if (faceFilter != FaceFilter.ALL) count++
            return count
        }
}

/**
 * Cache and helper for geocoded place names associated with MediaItem URIs.
 */
object PlaceLocationResolver {
    private val uriToPlaceCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun resolvePlace(context: Context, uri: String, lat: Double, lon: Double): String? {
        uriToPlaceCache[uri]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    val addr = addresses?.firstOrNull()
                    if (addr != null) {
                        val placeName = addr.locality
                            ?: addr.subAdminArea
                            ?: addr.adminArea
                            ?: addr.featureName
                            ?: addr.countryName
                        if (!placeName.isNullOrBlank()) {
                            uriToPlaceCache[uri] = placeName
                            return@withContext placeName
                        }
                    }
                }
                null
            }.getOrNull()
        }
    }

    fun getCachedPlace(uri: String): String? = uriToPlaceCache[uri]
}

/**
 * Fast in-memory and database-backed search indexing and matching engine.
 */

enum class ChipKind {
    ALBUM, PERSON, PLACE, TAG
}

data class DynamicEntityChip(
    val id: String,
    val label: String,
    val count: Int,
    val kind: ChipKind,
    val value: String
)

data class DiscoveryData(
    val categories: List<DiscoveryCategory>,
    val people: List<DiscoveryEntityItem>,
    val places: List<DiscoveryEntityItem>,
    val albums: List<DiscoveryEntityItem>,
    val tags: List<DiscoveryEntityItem>
)

data class SearchResultData(
    val query: String,
    val items: List<MediaItem>,
    val dynamicChips: List<DynamicEntityChip>,
    val totalPhotosCount: Int,
    val totalVideosCount: Int
)
