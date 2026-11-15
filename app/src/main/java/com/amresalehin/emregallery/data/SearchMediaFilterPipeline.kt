package com.amresalehin.emregallery.data

import android.content.Context
import android.net.Uri
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.ocr.TesseractOcr

/**
 * Applies multi-criteria media, time, entity, GPS, OCR text, and face filters
 * to non-trashed library items.
 */
internal class SearchMediaFilterPipeline(
    private val context: Context,
    private val faceStore: FaceClusterStore,
    private val locationCache: PhotoMapLocationCache
) {

    fun filter(
        media: List<MediaItem>,
        filter: SearchFilterState,
        customAlbums: List<CustomAlbum>,
        customTagsMap: Map<String, Set<String>>,
        customPeopleMap: Map<String, Set<String>>,
        favourites: Set<String>
    ): List<MediaItem> {
        var candidates = media.filterNot { it.isTrashed }
        candidates = applyMediaAndTimeFilters(candidates, filter, favourites)
        candidates = applyEntityFilters(candidates, filter, customAlbums, customTagsMap, customPeopleMap)
        candidates = applyMetadataFilters(candidates, filter, customPeopleMap)
        return candidates
    }

    private fun applyMediaAndTimeFilters(
        items: List<MediaItem>,
        filter: SearchFilterState,
        favourites: Set<String>
    ): List<MediaItem> {
        var result = when (filter.mediaType) {
            MediaTypeFilter.ALL -> items
            MediaTypeFilter.PHOTOS -> items.filterNot { it.isVideo || SearchEngine.isGif(it) }
            MediaTypeFilter.VIDEOS -> items.filter { it.isVideo }
            MediaTypeFilter.GIFS -> items.filter { SearchEngine.isGif(it) }
        }

        if (filter.favoritesOnly) {
            result = result.filter { it.uri in favourites }
        }

        val now = System.currentTimeMillis()
        return when (val time = filter.timeRange) {
            is TimeRangeFilter.AllTime -> result
            is TimeRangeFilter.Last30Days -> {
                val cutoff = now - 30L * 24 * 60 * 60 * 1000L
                result.filter { itemTimestamp(it) >= cutoff }
            }
            is TimeRangeFilter.Last90Days -> {
                val cutoff = now - 90L * 24 * 60 * 60 * 1000L
                result.filter { itemTimestamp(it) >= cutoff }
            }
            is TimeRangeFilter.Custom -> {
                result.filter { itemTimestamp(it) in time.startMillis..time.endMillis }
            }
        }
    }

    private fun applyEntityFilters(
        items: List<MediaItem>,
        filter: SearchFilterState,
        customAlbums: List<CustomAlbum>,
        customTagsMap: Map<String, Set<String>>,
        customPeopleMap: Map<String, Set<String>>
    ): List<MediaItem> {
        var result = items

        if (filter.selectedAlbumId != null) {
            if (filter.selectedAlbumId.startsWith("folder:")) {
                val folderName = filter.selectedAlbumId.removePrefix("folder:")
                result = result.filter { it.relativePath.contains(folderName, ignoreCase = true) }
            } else {
                val album = customAlbums.find { it.id == filter.selectedAlbumId }
                if (album != null) {
                    result = result.filter { it.uri in album.mediaUris }
                }
            }
        }

        if (filter.selectedPersonId != null) {
            if (filter.selectedPersonId.startsWith("name:")) {
                val personName = filter.selectedPersonId.removePrefix("name:")
                result = result.filter { customPeopleMap[it.uri]?.contains(personName) == true }
            } else {
                val faces = runCatching { faceStore.facesForCluster(filter.selectedPersonId) }.getOrDefault(emptyList())
                val personUris = faces.map { it.mediaUri }.toSet()
                result = result.filter { it.uri in personUris }
            }
        }

        if (filter.selectedPlace != null) {
            result = result.filter { item ->
                PlaceLocationResolver.getCachedPlace(item.uri)?.equals(filter.selectedPlace, ignoreCase = true) == true
            }
        }

        if (filter.selectedTag != null) {
            result = result.filter { customTagsMap[it.uri]?.contains(filter.selectedTag) == true }
        }

        return result
    }

    private fun applyMetadataFilters(
        items: List<MediaItem>,
        filter: SearchFilterState,
        customPeopleMap: Map<String, Set<String>>
    ): List<MediaItem> {
        var result = when (filter.gpsFilter) {
            GpsFilter.ALL -> items
            GpsFilter.HAS_GPS -> filterByGpsPresence(items, expectHasGps = true)
            GpsFilter.NO_GPS -> filterByGpsPresence(items, expectHasGps = false)
        }

        result = when (filter.ocrTextFilter) {
            OcrTextFilter.ALL -> result
            OcrTextFilter.HAS_TEXT -> result.filter { item ->
                !item.isVideo && !TesseractOcr.readCachedResult(context, Uri.parse(item.uri)).isNullOrBlank()
            }
            OcrTextFilter.NO_TEXT -> result.filter { item ->
                !item.isVideo && TesseractOcr.readCachedResult(context, Uri.parse(item.uri)).isNullOrBlank()
            }
        }

        return when (filter.faceFilter) {
            FaceFilter.ALL -> result
            FaceFilter.HAS_FACE -> {
                val mediaWithFaces = runCatching { faceStore.getMediaUrisWithFaces() }.getOrDefault(emptySet())
                result.filter { item ->
                    item.uri in mediaWithFaces || customPeopleMap[item.uri]?.isNotEmpty() == true
                }
            }
            FaceFilter.NO_FACE -> {
                val mediaWithFaces = runCatching { faceStore.getMediaUrisWithFaces() }.getOrDefault(emptySet())
                result.filter { item ->
                    item.uri !in mediaWithFaces && customPeopleMap[item.uri].isNullOrEmpty()
                }
            }
        }
    }

    private fun filterByGpsPresence(items: List<MediaItem>, expectHasGps: Boolean): List<MediaItem> {
        val cachedLocations = runCatching { locationCache.loadAll() }.getOrDefault(emptyMap())
        return items.filter { item ->
            val cached = cachedLocations[item.uri]
            val hasPoint = if (cached != null) {
                cached.point != null
            } else {
                val pt = PhotoMapLocationCache.extractLocation(context, item.uri, isVideo = item.isVideo)
                runCatching { locationCache.save(item.uri, pt) }
                pt != null
            }
            if (expectHasGps) hasPoint else !hasPoint
        }
    }

    private fun itemTimestamp(item: MediaItem): Long {
        return if (item.dateTakenMillis > 0) item.dateTakenMillis else item.dateAddedSeconds * 1000L
    }
}
