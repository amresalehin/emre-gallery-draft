package com.amresalehin.emregallery.data

import android.content.Context
import android.net.Uri
import com.amresalehin.emregallery.ocr.TesseractOcr
import java.util.Locale

/**
 * Ranks filtered [MediaItem] candidates against a text query and applies the requested sort order.
 */
internal class SearchMatchScorer(private val context: Context) {

    fun matchAndSort(
        candidates: List<MediaItem>,
        query: String,
        sortMode: SearchSortMode,
        customAlbums: List<CustomAlbum>,
        customTagsMap: Map<String, Set<String>>,
        customPeopleMap: Map<String, Set<String>>
    ): List<MediaItem> {
        val matched = if (query.isBlank()) {
            candidates
        } else {
            candidates.mapNotNull { item ->
                val score = computeMatchScore(item, query, customAlbums, customTagsMap, customPeopleMap)
                if (score > 0) item to score else null
            }.sortedByDescending { it.second }.map { it.first }
        }

        return when (sortMode) {
            SearchSortMode.RELEVANCE -> matched
            SearchSortMode.NEWEST_FIRST -> matched.sortedByDescending { itemTimestamp(it) }
            SearchSortMode.OLDEST_FIRST -> matched.sortedBy { itemTimestamp(it) }
            SearchSortMode.NAME -> matched.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }

    private fun computeMatchScore(
        item: MediaItem,
        query: String,
        customAlbums: List<CustomAlbum>,
        customTagsMap: Map<String, Set<String>>,
        customPeopleMap: Map<String, Set<String>>
    ): Int {
        val q = query.lowercase(Locale.ROOT)
        val name = item.name.lowercase(Locale.ROOT)
        val path = item.relativePath.lowercase(Locale.ROOT)

        var score = 0
        if (name.startsWith(q)) score += 100
        else if (name.contains(q)) score += 60

        if (path.contains(q)) score += 30

        val tags = customTagsMap[item.uri] ?: emptySet()
        if (tags.any { it.equals(q, ignoreCase = true) }) score += 80
        else if (tags.any { it.contains(q, ignoreCase = true) }) score += 40

        val people = customPeopleMap[item.uri] ?: emptySet()
        if (people.any { it.equals(q, ignoreCase = true) }) score += 90
        else if (people.any { it.contains(q, ignoreCase = true) }) score += 45

        val matchingAlbum = customAlbums.any { it.mediaUris.contains(item.uri) && it.name.contains(q, ignoreCase = true) }
        if (matchingAlbum) score += 50

        val place = PlaceLocationResolver.getCachedPlace(item.uri)
        if (place != null && place.contains(q, ignoreCase = true)) score += 70

        val ocr = TesseractOcr.readCachedResult(context, Uri.parse(item.uri))
        if (!ocr.isNullOrBlank() && ocr.contains(q, ignoreCase = true)) score += 40

        return score
    }

    private fun itemTimestamp(item: MediaItem): Long {
        return if (item.dateTakenMillis > 0) item.dateTakenMillis else item.dateAddedSeconds * 1000L
    }
}
