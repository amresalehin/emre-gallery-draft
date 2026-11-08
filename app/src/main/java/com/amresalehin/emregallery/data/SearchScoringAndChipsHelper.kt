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

internal fun SearchEngine.computeMatchScore(
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

    // Custom tags
    val tags = customTagsMap[item.uri] ?: emptySet()
    if (tags.any { it.equals(q, ignoreCase = true) }) score += 80
    else if (tags.any { it.contains(q, ignoreCase = true) }) score += 40

    // Custom people
    val people = customPeopleMap[item.uri] ?: emptySet()
    if (people.any { it.equals(q, ignoreCase = true) }) score += 90
    else if (people.any { it.contains(q, ignoreCase = true) }) score += 45

    // Custom album
    val matchingAlbum = customAlbums.any { it.mediaUris.contains(item.uri) && it.name.contains(q, ignoreCase = true) }
    if (matchingAlbum) score += 50

    // Cached Place
    val place = PlaceLocationResolver.getCachedPlace(item.uri)
    if (place != null && place.contains(q, ignoreCase = true)) score += 70

    // Cached OCR text
    val ocr = TesseractOcr.readCachedResult(context, Uri.parse(item.uri))
    if (!ocr.isNullOrBlank() && ocr.contains(q, ignoreCase = true)) score += 40

    return score
}

internal fun SearchEngine.extractDynamicChips(
    results: List<MediaItem>,
    customAlbums: List<CustomAlbum>,
    customTagsMap: Map<String, Set<String>>,
    customPeopleMap: Map<String, Set<String>>
): List<DynamicEntityChip> {
    val chips = mutableListOf<DynamicEntityChip>()
    if (results.isEmpty()) return chips

    val resultUris = results.map { it.uri }.toSet()

    // Real Albums occurring in result set
    for (album in customAlbums) {
        val count = album.mediaUris.count { it in resultUris }
        if (count > 0) {
            chips.add(DynamicEntityChip("album_${album.id}", album.name, count, ChipKind.ALBUM, album.id))
        }
    }

    // Real People occurring in result set
    val personCounts = mutableMapOf<String, Int>()
    for (item in results) {
        val people = customPeopleMap[item.uri] ?: emptySet()
        for (p in people) {
            personCounts[p] = (personCounts[p] ?: 0) + 1
        }
    }
    for ((personName, count) in personCounts) {
        if (count > 0) {
            chips.add(DynamicEntityChip("person_$personName", personName, count, ChipKind.PERSON, "name:$personName"))
        }
    }

    // Real Places occurring in result set
    val placeCounts = mutableMapOf<String, Int>()
    for (item in results) {
        val place = PlaceLocationResolver.getCachedPlace(item.uri)
        if (!place.isNullOrBlank()) {
            placeCounts[place] = (placeCounts[place] ?: 0) + 1
        }
    }
    for ((placeName, count) in placeCounts) {
        if (count > 0) {
            chips.add(DynamicEntityChip("place_$placeName", placeName, count, ChipKind.PLACE, placeName))
        }
    }

    // Real Tags occurring in result set
    val tagCounts = mutableMapOf<String, Int>()
    for (item in results) {
        val tags = customTagsMap[item.uri] ?: emptySet()
        for (t in tags) {
            tagCounts[t] = (tagCounts[t] ?: 0) + 1
        }
    }
    for ((tag, count) in tagCounts) {
        if (count > 0) {
            chips.add(DynamicEntityChip("tag_$tag", "#$tag", count, ChipKind.TAG, tag))
        }
    }

    return chips.take(6)
}

internal fun SearchEngine.getItemTimestamp(item: MediaItem): Long {
    return if (item.dateTakenMillis > 0) item.dateTakenMillis else item.dateAddedSeconds * 1000L
}

internal fun SearchEngine.formatItemDate(item: MediaItem): String {
    val t = getItemTimestamp(item)
    if (t <= 0) return ""
    val fmt = SimpleDateFormat("MMM yyyy", Locale.getDefault())
    return fmt.format(Date(t))
}

