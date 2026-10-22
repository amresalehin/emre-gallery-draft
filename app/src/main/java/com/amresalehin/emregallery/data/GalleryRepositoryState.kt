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
}
