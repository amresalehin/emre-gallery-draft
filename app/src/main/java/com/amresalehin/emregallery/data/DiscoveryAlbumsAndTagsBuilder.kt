package com.amresalehin.emregallery.data

/**
 * Builds State A discovery items for custom albums, top device folders, and tags.
 */
internal object DiscoveryAlbumsAndTagsBuilder {

    fun buildAlbums(
        nonTrashMedia: List<MediaItem>,
        customAlbums: List<CustomAlbum>
    ): List<DiscoveryEntityItem> {
        val albumItems = mutableListOf<DiscoveryEntityItem>()
        for (album in customAlbums.filter { it.mediaUris.isNotEmpty() }) {
            albumItems.add(
                DiscoveryEntityItem(
                    id = album.id,
                    title = album.name,
                    subtitle = "${album.mediaUris.size} items",
                    thumbnailUri = album.mediaUris.firstOrNull(),
                    queryAction = album.name,
                    filterAction = SearchFilterState(selectedAlbumId = album.id)
                )
            )
        }

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
        return albumItems
    }

    fun buildTags(
        nonTrashMedia: List<MediaItem>,
        customTagsMap: Map<String, Set<String>>
    ): List<DiscoveryEntityItem> {
        val tagCounts = mutableMapOf<String, MutableList<MediaItem>>()
        for (item in nonTrashMedia) {
            val tags = customTagsMap[item.uri] ?: emptySet()
            for (t in tags) {
                if (t.isNotBlank()) {
                    tagCounts.getOrPut(t) { mutableListOf() }.add(item)
                }
            }
        }

        return tagCounts.entries.sortedByDescending { it.value.size }.take(8).map { (tag, items) ->
            DiscoveryEntityItem(
                id = "tag_$tag",
                title = tag,
                subtitle = "${items.size} items",
                thumbnailUri = items.firstOrNull()?.uri,
                queryAction = tag,
                filterAction = SearchFilterState(selectedTag = tag)
            )
        }
    }
}
