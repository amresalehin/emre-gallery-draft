package com.amresalehin.emregallery.data

import java.util.Locale

object GalleryRepositoryState {
    fun filterByQuery(items: List<MediaItem>, query: String): List<MediaItem> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return items
        return items.filter {
            it.name.contains(normalized, ignoreCase = true) ||
            it.relativePath.contains(normalized, ignoreCase = true)
        }
    }

    fun filterVideos(items: List<MediaItem>, videosOnly: Boolean): List<MediaItem> {
        return if (videosOnly) items.filter { it.isVideo } else items
    }

    fun groupByFolder(items: List<MediaItem>): Map<String, List<MediaItem>> {
        return items.groupBy { it.relativePath.ifBlank { "Root" } }
    }

    fun sortMedia(items: List<MediaItem>, sortOrder: String): List<MediaItem> {
        return when (sortOrder) {
            "name_asc" -> items.sortedWith(compareBy({ it.name.lowercase(Locale.ROOT) }, { it.id }))
            "date_asc" -> items.sortedWith(compareBy({ if (it.dateTakenMillis > 0) it.dateTakenMillis else it.dateAddedSeconds * 1000L }, { it.id }))
            "size_desc" -> items.sortedWith(compareByDescending<MediaItem> { it.sizeBytes }.thenBy { it.id })
            else -> items.sortedWith(compareByDescending<MediaItem> { if (it.dateTakenMillis > 0) it.dateTakenMillis else it.dateAddedSeconds * 1000L }.thenBy { it.id })
        }
    }
}
