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

internal suspend fun SearchEngine.getLiveSuggestionsImpl(
    query: String,
    media: List<MediaItem>,
    recentSearches: List<String>,
    customAlbums: List<CustomAlbum>,
    customTagsMap: Map<String, Set<String>>,
    customPeopleMap: Map<String, Set<String>>
): List<SearchSuggestion> = withContext(Dispatchers.IO) {
    val q = query.trim()
    if (q.isEmpty()) return@withContext emptyList()

    val suggestions = mutableListOf<SearchSuggestion>()

    // 1. Recent matching searches
    val matchingRecents = recentSearches.filter { it.contains(q, ignoreCase = true) && !it.equals(q, ignoreCase = true) }.take(2)
    for (recent in matchingRecents) {
        suggestions.add(
            SearchSuggestion(
                id = "recent_$recent",
                type = SuggestionType.RECENT,
                title = recent,
                subtitle = "Recent search",
                targetQuery = recent
            )
        )
    }

    // 2. Matching People (from FaceClusterStore & customPeopleMap)
    val personSummaries = runCatching {
        faceStore.personSummaries(includeHidden = false, engineId = BuffaloLFaceEngine.ENGINE_ID)
    }.getOrDefault(emptyList())
    val matchingPeople = personSummaries.filter {
        it.displayName.isNotBlank() && it.displayName.contains(q, ignoreCase = true) && it.faceCount > 0
    }.take(2)

    for (person in matchingPeople) {
        suggestions.add(
            SearchSuggestion(
                id = "person_${person.id}",
                type = SuggestionType.PERSON,
                title = person.displayName,
                subtitle = "${person.faceCount} photos",
                thumbnailUri = person.sampleUri,
                isPersonPortrait = true,
                personFaceIndex = person.portraitFaceIndex,
                targetQuery = person.displayName,
                targetFilter = SearchFilterState(selectedPersonId = person.id)
            )
        )
    }

    // Custom people check if fewer than 2
    if (matchingPeople.size < 2) {
        for ((_, names) in customPeopleMap) {
            if (suggestions.count { it.type == SuggestionType.PERSON } >= 2) break
            for (name in names) {
                if (name.contains(q, ignoreCase = true) && suggestions.none { it.title.equals(name, ignoreCase = true) }) {
                    val count = media.count { customPeopleMap[it.uri]?.contains(name) == true }
                    val sample = media.firstOrNull { customPeopleMap[it.uri]?.contains(name) == true }?.uri
                    suggestions.add(
                        SearchSuggestion(
                            id = "custom_person_$name",
                            type = SuggestionType.PERSON,
                            title = name,
                            subtitle = "$count photos",
                            thumbnailUri = sample,
                            isPersonPortrait = true,
                            targetQuery = name,
                            targetFilter = SearchFilterState(selectedPersonId = "name:$name")
                        )
                    )
                    if (suggestions.count { it.type == SuggestionType.PERSON } >= 2) break
                }
            }
        }
    }

    // 3. Matching Places
    val cachedLocations = runCatching { locationCache.loadAll() }.getOrDefault(emptyMap())
    val matchingPlaces = mutableMapOf<String, MutableList<MediaItem>>()
    for (item in media) {
        val placeName = PlaceLocationResolver.getCachedPlace(item.uri)
        if (!placeName.isNullOrBlank() && placeName.contains(q, ignoreCase = true)) {
            matchingPlaces.getOrPut(placeName) { mutableListOf() }.add(item)
        }
    }
    for ((placeName, items) in matchingPlaces.entries.take(2)) {
        suggestions.add(
            SearchSuggestion(
                id = "place_$placeName",
                type = SuggestionType.PLACE,
                title = placeName,
                subtitle = "${items.size} photos · Location",
                thumbnailUri = items.firstOrNull()?.uri,
                targetQuery = placeName,
                targetFilter = SearchFilterState(selectedPlace = placeName)
            )
        )
    }

    // 4. Matching Albums
    val matchingAlbums = customAlbums.filter { it.name.contains(q, ignoreCase = true) && it.mediaUris.isNotEmpty() }.take(2)
    for (album in matchingAlbums) {
        suggestions.add(
            SearchSuggestion(
                id = "album_${album.id}",
                type = SuggestionType.ALBUM,
                title = album.name,
                subtitle = "${album.mediaUris.size} photos · Album",
                thumbnailUri = album.mediaUris.firstOrNull(),
                targetQuery = album.name,
                targetFilter = SearchFilterState(selectedAlbumId = album.id)
            )
        )
    }

    // 5. Matching Files (matching by filename or relative folder path)
    val matchingFiles = media.filter {
        it.name.contains(q, ignoreCase = true) || it.relativePath.contains(q, ignoreCase = true)
    }.take(2)
    for (file in matchingFiles) {
        val dateStr = formatItemDate(file)
        suggestions.add(
            SearchSuggestion(
                id = "file_${file.id}",
                type = SuggestionType.FILE,
                title = file.name,
                subtitle = if (dateStr.isNotBlank()) "$dateStr · ${file.relativePath}" else file.relativePath,
                thumbnailUri = file.uri,
                targetQuery = file.name
            )
        )
    }

    // 6. Matching Tags
    val matchingTags = customTagsMap.values.flatten().toSet().filter { it.contains(q, ignoreCase = true) }.take(2)
    for (tag in matchingTags) {
        val tagMedia = media.filter { customTagsMap[it.uri]?.contains(tag) == true }
        suggestions.add(
            SearchSuggestion(
                id = "tag_$tag",
                type = SuggestionType.TAG,
                title = "#$tag",
                subtitle = "${tagMedia.size} items · Tag",
                thumbnailUri = tagMedia.firstOrNull()?.uri,
                targetQuery = tag,
                targetFilter = SearchFilterState(selectedTag = tag)
            )
        )
    }

    // 7. Matching Documents (OCR text matches)
    var docMatches = 0
    for (item in media) {
        if (docMatches >= 2) break
        val ocrText = TesseractOcr.readCachedResult(context, Uri.parse(item.uri))
        if (!ocrText.isNullOrBlank() && ocrText.contains(q, ignoreCase = true)) {
            val snippet = ocrText.lineSequence().firstOrNull { it.contains(q, ignoreCase = true) }?.trim()?.take(40)
            suggestions.add(
                SearchSuggestion(
                    id = "ocr_${item.id}",
                    type = SuggestionType.DOCUMENT,
                    title = item.name,
                    subtitle = snippet ?: "Text found in image",
                    thumbnailUri = item.uri,
                    targetQuery = q
                )
            )
            docMatches++
        }
    }

    suggestions
}

/**
 * Executes real multi-criteria search filtering and ranking for State C.
 */
