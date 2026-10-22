package com.amresalehin.emregallery.data

/** Pure filtering helpers shared by tests and local library screens. */
object GalleryRepositoryState {
    fun filterByQuery(items: List<MediaItem>, query: String): List<MediaItem> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return items
        return items.filter { it.name.contains(normalized, ignoreCase = true) || it.relativePath.contains(normalized, ignoreCase = true) }
    }

    fun filterVideos(items: List<MediaItem>, videosOnly: Boolean): List<MediaItem> =
        if (videosOnly) items.filter(MediaItem::isVideo) else items

    fun groupByFolder(items: List<MediaItem>): Map<String, List<MediaItem>> =
        items.groupBy { it.relativePath.substringBeforeLast('/', "Internal storage").ifBlank { "Internal storage" } }

    fun sortMedia(items: List<MediaItem>, sortOrder: String): List<MediaItem> = when (sortOrder) {
        "date_asc" -> items.sortedWith(compareBy({ if (it.dateTakenMillis > 0) it.dateTakenMillis else it.dateAddedSeconds * 1000L }, { it.id }))
        "name_asc" -> items.sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
        "size_desc" -> items.sortedWith(compareByDescending<MediaItem> { it.sizeBytes }.thenBy { it.id })
        else -> items.sortedWith(compareByDescending<MediaItem> { if (it.dateTakenMillis > 0) it.dateTakenMillis else it.dateAddedSeconds * 1000L }.thenBy { it.id })
    }
}
