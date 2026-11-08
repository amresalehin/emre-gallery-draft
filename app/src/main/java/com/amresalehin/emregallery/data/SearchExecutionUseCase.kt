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

internal suspend fun SearchEngine.searchMediaImpl(
    query: String,
    media: List<MediaItem>,
    filter: SearchFilterState,
    sortMode: SearchSortMode,
    customAlbums: List<CustomAlbum>,
    customTagsMap: Map<String, Set<String>>,
    customPeopleMap: Map<String, Set<String>>,
    favourites: Set<String>
): SearchResultData = withContext(Dispatchers.Default) {
    val q = query.trim()
    val nonTrash = media.filterNot { it.isTrashed }

    // Filter by media type
    var candidateList = when (filter.mediaType) {
        MediaTypeFilter.ALL -> nonTrash
        MediaTypeFilter.PHOTOS -> nonTrash.filterNot { it.isVideo || isGif(it) }
        MediaTypeFilter.VIDEOS -> nonTrash.filter { it.isVideo }
        MediaTypeFilter.GIFS -> nonTrash.filter { isGif(it) }
    }

    // Filter by favorites
    if (filter.favoritesOnly) {
        candidateList = candidateList.filter { it.uri in favourites }
    }

    // Filter by time range
    val now = System.currentTimeMillis()
    candidateList = when (val time = filter.timeRange) {
        is TimeRangeFilter.AllTime -> candidateList
        is TimeRangeFilter.Last30Days -> {
            val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000L
            candidateList.filter { getItemTimestamp(it) >= thirtyDaysAgo }
        }
        is TimeRangeFilter.Last90Days -> {
            val ninetyDaysAgo = now - 90L * 24 * 60 * 60 * 1000L
            candidateList.filter { getItemTimestamp(it) >= ninetyDaysAgo }
        }
        is TimeRangeFilter.Custom -> {
            candidateList.filter {
                val t = getItemTimestamp(it)
                t in time.startMillis..time.endMillis
            }
        }
    }

    // Filter by Album
    if (filter.selectedAlbumId != null) {
        if (filter.selectedAlbumId.startsWith("folder:")) {
            val folderName = filter.selectedAlbumId.removePrefix("folder:")
            candidateList = candidateList.filter { it.relativePath.contains(folderName, ignoreCase = true) }
        } else {
            val album = customAlbums.find { it.id == filter.selectedAlbumId }
            if (album != null) {
                candidateList = candidateList.filter { it.uri in album.mediaUris }
            }
        }
    }

    // Filter by Person
    if (filter.selectedPersonId != null) {
        if (filter.selectedPersonId.startsWith("name:")) {
            val personName = filter.selectedPersonId.removePrefix("name:")
            candidateList = candidateList.filter { customPeopleMap[it.uri]?.contains(personName) == true }
        } else {
            val faces = runCatching { faceStore.facesForCluster(filter.selectedPersonId) }.getOrDefault(emptyList())
            val personUris = faces.map { it.mediaUri }.toSet()
            candidateList = candidateList.filter { it.uri in personUris }
        }
    }

    // Filter by Place
    if (filter.selectedPlace != null) {
        candidateList = candidateList.filter { item ->
            PlaceLocationResolver.getCachedPlace(item.uri)?.equals(filter.selectedPlace, ignoreCase = true) == true
        }
    }

    // Filter by Tag
    if (filter.selectedTag != null) {
        candidateList = candidateList.filter { customTagsMap[it.uri]?.contains(filter.selectedTag) == true }
    }

    // Filter by GPS
    candidateList = when (filter.gpsFilter) {
        GpsFilter.ALL -> candidateList
        GpsFilter.HAS_GPS -> {
            val cachedLocations = runCatching { locationCache.loadAll() }.getOrDefault(emptyMap())
            candidateList.filter { item ->
                val cached = cachedLocations[item.uri]
                if (cached != null) {
                    cached.point != null
                } else {
                    val pt = PhotoMapLocationCache.extractLocation(context, item.uri, isVideo = item.isVideo)
                    runCatching { locationCache.save(item.uri, pt) }
                    pt != null
                }
            }
        }
        GpsFilter.NO_GPS -> {
            val cachedLocations = runCatching { locationCache.loadAll() }.getOrDefault(emptyMap())
            candidateList.filter { item ->
                val cached = cachedLocations[item.uri]
                if (cached != null) {
                    cached.point == null
                } else {
                    val pt = PhotoMapLocationCache.extractLocation(context, item.uri, isVideo = item.isVideo)
                    runCatching { locationCache.save(item.uri, pt) }
                    pt == null
                }
            }
        }
    }

    // Filter by OCR Text
    candidateList = when (filter.ocrTextFilter) {
        OcrTextFilter.ALL -> candidateList
        OcrTextFilter.HAS_TEXT -> candidateList.filter { item ->
            if (item.isVideo) false else {
                val ocrText = TesseractOcr.readCachedResult(context, Uri.parse(item.uri))
                !ocrText.isNullOrBlank()
            }
        }
        OcrTextFilter.NO_TEXT -> candidateList.filter { item ->
            if (item.isVideo) false else {
                val ocrText = TesseractOcr.readCachedResult(context, Uri.parse(item.uri))
                ocrText.isNullOrBlank()
            }
        }
    }

    // Filter by Face
    candidateList = when (filter.faceFilter) {
        FaceFilter.ALL -> candidateList
        FaceFilter.HAS_FACE -> {
            val mediaWithFaces = runCatching { faceStore.getMediaUrisWithFaces() }.getOrDefault(emptySet())
            candidateList.filter { item ->
                item.uri in mediaWithFaces || customPeopleMap[item.uri]?.isNotEmpty() == true
            }
        }
        FaceFilter.NO_FACE -> {
            val mediaWithFaces = runCatching { faceStore.getMediaUrisWithFaces() }.getOrDefault(emptySet())
            candidateList.filter { item ->
                item.uri !in mediaWithFaces && customPeopleMap[item.uri].isNullOrEmpty()
            }
        }
    }

    // Match query against fields
    val matchedList = if (q.isBlank()) {
        candidateList
    } else {
        // Rank items:
        // 1. Exact/prefix match on name or confirmed person/place/album
        // 2. Token match on name/path
        // 3. Match on OCR text or tags
        val scoredItems = candidateList.mapNotNull { item ->
            val score = computeMatchScore(item, q, customAlbums, customTagsMap, customPeopleMap)
            if (score > 0) item to score else null
        }
        scoredItems.sortedByDescending { it.second }.map { it.first }
    }

    // Apply sorting
    val sortedList = when (sortMode) {
        SearchSortMode.RELEVANCE -> matchedList // Already relevance scored if query present, or date
        SearchSortMode.NEWEST_FIRST -> matchedList.sortedByDescending { getItemTimestamp(it) }
        SearchSortMode.OLDEST_FIRST -> matchedList.sortedBy { getItemTimestamp(it) }
        SearchSortMode.NAME -> matchedList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    // Extract dynamic entity chips strictly present in the CURRENT result set!
    val dynamicChips = extractDynamicChips(sortedList, customAlbums, customTagsMap, customPeopleMap)

    SearchResultData(
        query = q,
        items = sortedList,
        dynamicChips = dynamicChips,
        totalPhotosCount = sortedList.count { !it.isVideo },
        totalVideosCount = sortedList.count { it.isVideo }
    )
}

