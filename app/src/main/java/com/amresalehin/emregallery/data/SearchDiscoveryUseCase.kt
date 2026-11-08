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

internal suspend fun SearchEngine.getDiscoveryContentImpl(
    media: List<MediaItem>,
    customAlbums: List<CustomAlbum>,
    customTagsMap: Map<String, Set<String>>,
    customPeopleMap: Map<String, Set<String>>,
    favourites: Set<String>
): DiscoveryData = withContext(Dispatchers.IO) {
    val nonTrashMedia = media.filterNot { it.isTrashed }

    // 1. Confirmed / Recognized People from FaceClusterStore + customPeopleMap
    val personSummaries = runCatching {
        faceStore.personSummaries(includeHidden = false, engineId = BuffaloLFaceEngine.ENGINE_ID)
    }.getOrDefault(emptyList()).filter { it.faceCount > 0 && it.sampleUri != null }

    val peopleItems = mutableListOf<DiscoveryEntityItem>()
    for (summary in personSummaries.take(8)) {
        val name = summary.displayName.ifBlank { "Person ${summary.id.take(4)}" }
        peopleItems.add(
            DiscoveryEntityItem(
                id = summary.id,
                title = name,
                subtitle = "${summary.faceCount} photos",
                thumbnailUri = summary.sampleUri,
                isPerson = true,
                personFaceIndex = summary.portraitFaceIndex,
                queryAction = name,
                filterAction = SearchFilterState(selectedPersonId = summary.id)
            )
        )
    }

    // Custom people if not in cluster store
    for ((_, names) in customPeopleMap) {
        for (name in names) {
            if (name.isNotBlank() && peopleItems.none { it.title.equals(name, ignoreCase = true) }) {
                val matchingPhotos = nonTrashMedia.filter { customPeopleMap[it.uri]?.contains(name) == true }
                if (matchingPhotos.isNotEmpty()) {
                    peopleItems.add(
                        DiscoveryEntityItem(
                            id = "custom_person_$name",
                            title = name,
                            subtitle = "${matchingPhotos.size} photos",
                            thumbnailUri = matchingPhotos.firstOrNull()?.uri,
                            isPerson = true,
                            queryAction = name,
                            filterAction = SearchFilterState(selectedPersonId = "name:$name")
                        )
                    )
                }
            }
        }
    }

    // 2. Real Places from PhotoMapLocationCache & folder locations
    val cachedLocations = runCatching { locationCache.loadAll() }.getOrDefault(emptyMap())
    val locatedMedia = nonTrashMedia.filter { item -> cachedLocations[item.uri]?.point != null }
    val placeItems = mutableListOf<DiscoveryEntityItem>()

    // Check if any have reverse-geocoded places
    val placeGroups = mutableMapOf<String, MutableList<MediaItem>>()
    for (item in locatedMedia) {
        val point = cachedLocations[item.uri]?.point ?: continue
        val placeName = PlaceLocationResolver.resolvePlace(context, item.uri, point.latitude, point.longitude)
        if (!placeName.isNullOrBlank()) {
            placeGroups.getOrPut(placeName) { mutableListOf() }.add(item)
        }
    }

    for ((placeName, items) in placeGroups.entries.sortedByDescending { it.value.size }.take(6)) {
        placeItems.add(
            DiscoveryEntityItem(
                id = "place_$placeName",
                title = placeName,
                subtitle = "${items.size} photos",
                thumbnailUri = items.firstOrNull()?.uri,
                queryAction = placeName,
                filterAction = SearchFilterState(selectedPlace = placeName)
            )
        )
    }

    // 3. Real Albums
    val albumItems = mutableListOf<DiscoveryEntityItem>()
    for (album in customAlbums.filter { it.mediaUris.isNotEmpty() }) {
        val sampleUri = album.mediaUris.firstOrNull()
        albumItems.add(
            DiscoveryEntityItem(
                id = album.id,
                title = album.name,
                subtitle = "${album.mediaUris.size} items",
                thumbnailUri = sampleUri,
                queryAction = album.name,
                filterAction = SearchFilterState(selectedAlbumId = album.id)
            )
        )
    }

    // Top device folders if custom albums are few
    val folderGroups = nonTrashMedia.groupBy {
        it.relativePath.substringBeforeLast('/', "").ifBlank { "Camera" }
    }.filter { it.value.isNotEmpty() }

    for ((folderName, folderMedia) in folderGroups.entries.sortedByDescending { it.value.size }.take(4)) {
        if (albumItems.none { it.title.equals(folderName, ignoreCase = true) }) {
            albumItems.add(
                DiscoveryEntityItem(
                    id = "folder_$folderName",
                    title = folderName,
                    subtitle = "${folderMedia.size} items",
                    thumbnailUri = folderMedia.firstOrNull()?.uri,
                    queryAction = folderName,
                    filterAction = SearchFilterState(selectedAlbumId = "folder:$folderName")
                )
            )
        }
    }

    // 4. Things / Tags
    val tagCounts = mutableMapOf<String, MutableList<MediaItem>>()
    for (item in nonTrashMedia) {
        val tags = customTagsMap[item.uri] ?: emptySet()
        for (t in tags) {
            if (t.isNotBlank()) {
                tagCounts.getOrPut(t) { mutableListOf() }.add(item)
            }
        }
    }

    val tagItems = tagCounts.entries.sortedByDescending { it.value.size }.take(8).map { (tag, items) ->
        DiscoveryEntityItem(
            id = "tag_$tag",
            title = tag,
            subtitle = "${items.size} items",
            thumbnailUri = items.firstOrNull()?.uri,
            queryAction = tag,
            filterAction = SearchFilterState(selectedTag = tag)
        )
    }

    // 5. Category Shortcuts: Photos, Videos, Documents (OCR text items), Favourites
    val categories = mutableListOf<DiscoveryCategory>()
    val photos = nonTrashMedia.filterNot { it.isVideo }
    if (photos.isNotEmpty()) {
        categories.add(
            DiscoveryCategory(
                id = "cat_photos",
                title = "Photos",
                itemCount = photos.size,
                sampleUris = photos.take(3).map { it.uri },
                filterAction = SearchFilterState(mediaType = MediaTypeFilter.PHOTOS)
            )
        )
    }

    val videos = nonTrashMedia.filter { it.isVideo }
    if (videos.isNotEmpty()) {
        categories.add(
            DiscoveryCategory(
                id = "cat_videos",
                title = "Videos",
                itemCount = videos.size,
                sampleUris = videos.take(3).map { it.uri },
                filterAction = SearchFilterState(mediaType = MediaTypeFilter.VIDEOS)
            )
        )
    }

    // Documents: items with cached OCR text
    val documentItems = nonTrashMedia.filter {
        TesseractOcr.hasCachedResult(context, Uri.parse(it.uri))
    }
    if (documentItems.isNotEmpty()) {
        categories.add(
            DiscoveryCategory(
                id = "cat_docs",
                title = "Documents",
                itemCount = documentItems.size,
                sampleUris = documentItems.take(3).map { it.uri },
                queryAction = "document"
            )
        )
    }

    if (favourites.isNotEmpty()) {
        val favItems = nonTrashMedia.filter { it.uri in favourites }
        if (favItems.isNotEmpty()) {
            categories.add(
                DiscoveryCategory(
                    id = "cat_favs",
                    title = "Favorites",
                    itemCount = favItems.size,
                    sampleUris = favItems.take(3).map { it.uri },
                    filterAction = SearchFilterState(favoritesOnly = true)
                )
            )
        }
    }

    DiscoveryData(
        categories = categories,
        people = peopleItems,
        places = placeItems,
        albums = albumItems,
        tags = tagItems
    )
}

/**
 * Computes live suggestions (State B) while actively typing.
 * Rule: at most 1–2 suggestions per category, and only categories that have real matches.
 */
