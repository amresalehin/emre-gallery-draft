package com.amresalehin.emregallery.data

import android.content.Context
import android.net.Uri
import com.amresalehin.emregallery.face.BuffaloLFaceEngine
import com.amresalehin.emregallery.face.FaceClusterStore
import com.amresalehin.emregallery.ocr.TesseractOcr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Computes State B live typeahead suggestions while typing (at most 1–2 suggestions per category,
 * limited to categories with real matches).
 */
internal class SearchSuggestionsBuilder(
    private val context: Context,
    private val faceStore: FaceClusterStore,
    private val locationCache: PhotoMapLocationCache
) {

    fun build(
        query: String,
        media: List<MediaItem>,
        recentSearches: List<String>,
        customAlbums: List<CustomAlbum>,
        customTagsMap: Map<String, Set<String>>,
        customPeopleMap: Map<String, Set<String>>
    ): List<SearchSuggestion> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val suggestions = mutableListOf<SearchSuggestion>()
        addRecentSuggestions(q, recentSearches, suggestions)
        addPeopleSuggestions(q, media, customPeopleMap, suggestions)
        addPlaceSuggestions(q, media, suggestions)
        addAlbumSuggestions(q, customAlbums, suggestions)
        addFileSuggestions(q, media, suggestions)
        addTagSuggestions(q, media, customTagsMap, suggestions)
        addDocumentSuggestions(q, media, suggestions)
        return suggestions
    }

    private fun addRecentSuggestions(
        q: String,
        recentSearches: List<String>,
        out: MutableList<SearchSuggestion>
    ) {
        val matchingRecents = recentSearches
            .filter { it.contains(q, ignoreCase = true) && !it.equals(q, ignoreCase = true) }
            .take(2)
        for (recent in matchingRecents) {
            out.add(
                SearchSuggestion(
                    id = "recent_$recent",
                    type = SuggestionType.RECENT,
                    title = recent,
                    subtitle = "Recent search",
                    targetQuery = recent
                )
            )
        }
    }

    private fun addPeopleSuggestions(
        q: String,
        media: List<MediaItem>,
        customPeopleMap: Map<String, Set<String>>,
        out: MutableList<SearchSuggestion>
    ) {
        val personSummaries = runCatching {
            faceStore.personSummaries(includeHidden = false, engineId = BuffaloLFaceEngine.ENGINE_ID)
        }.getOrDefault(emptyList())
        val matchingPeople = personSummaries.filter {
            it.displayName.isNotBlank() && it.displayName.contains(q, ignoreCase = true) && it.faceCount > 0
        }.take(2)

        for (person in matchingPeople) {
            out.add(
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

        if (matchingPeople.size < 2) {
            for ((_, names) in customPeopleMap) {
                if (out.count { it.type == SuggestionType.PERSON } >= 2) break
                for (name in names) {
                    if (name.contains(q, ignoreCase = true) && out.none { it.title.equals(name, ignoreCase = true) }) {
                        val count = media.count { customPeopleMap[it.uri]?.contains(name) == true }
                        val sample = media.firstOrNull { customPeopleMap[it.uri]?.contains(name) == true }?.uri
                        out.add(
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
                        if (out.count { it.type == SuggestionType.PERSON } >= 2) break
                    }
                }
            }
        }
    }

    private fun addPlaceSuggestions(
        q: String,
        media: List<MediaItem>,
        out: MutableList<SearchSuggestion>
    ) {
        runCatching { locationCache.loadAll() }
        val matchingPlaces = mutableMapOf<String, MutableList<MediaItem>>()
        for (item in media) {
            val placeName = PlaceLocationResolver.getCachedPlace(item.uri)
            if (!placeName.isNullOrBlank() && placeName.contains(q, ignoreCase = true)) {
                matchingPlaces.getOrPut(placeName) { mutableListOf() }.add(item)
            }
        }
        for ((placeName, items) in matchingPlaces.entries.take(2)) {
            out.add(
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
    }

    private fun addAlbumSuggestions(
        q: String,
        customAlbums: List<CustomAlbum>,
        out: MutableList<SearchSuggestion>
    ) {
        val matchingAlbums = customAlbums
            .filter { it.name.contains(q, ignoreCase = true) && it.mediaUris.isNotEmpty() }
            .take(2)
        for (album in matchingAlbums) {
            out.add(
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
    }

    private fun addFileSuggestions(
        q: String,
        media: List<MediaItem>,
        out: MutableList<SearchSuggestion>
    ) {
        val matchingFiles = media.filter {
            it.name.contains(q, ignoreCase = true) || it.relativePath.contains(q, ignoreCase = true)
        }.take(2)
        for (file in matchingFiles) {
            val dateStr = formatItemDate(file)
            out.add(
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
    }

    private fun addTagSuggestions(
        q: String,
        media: List<MediaItem>,
        customTagsMap: Map<String, Set<String>>,
        out: MutableList<SearchSuggestion>
    ) {
        val matchingTags = customTagsMap.values.flatten().toSet()
            .filter { it.contains(q, ignoreCase = true) }
            .take(2)
        for (tag in matchingTags) {
            val tagMedia = media.filter { customTagsMap[it.uri]?.contains(tag) == true }
            out.add(
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
    }

    private fun addDocumentSuggestions(
        q: String,
        media: List<MediaItem>,
        out: MutableList<SearchSuggestion>
    ) {
        var docMatches = 0
        for (item in media) {
            if (docMatches >= 2) break
            val ocrText = TesseractOcr.readCachedResult(context, Uri.parse(item.uri))
            if (!ocrText.isNullOrBlank() && ocrText.contains(q, ignoreCase = true)) {
                val snippet = ocrText.lineSequence()
                    .firstOrNull { it.contains(q, ignoreCase = true) }
                    ?.trim()
                    ?.take(40)
                out.add(
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
    }

    private fun formatItemDate(item: MediaItem): String {
        val t = if (item.dateTakenMillis > 0) item.dateTakenMillis else item.dateAddedSeconds * 1000L
        if (t <= 0) return ""
        return SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(t))
    }
}
