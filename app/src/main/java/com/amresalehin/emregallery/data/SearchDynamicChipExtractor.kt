package com.amresalehin.emregallery.data

/**
 * Extracts dynamic filter chips from entities strictly present in the current search result set.
 */
internal object SearchDynamicChipExtractor {

    fun extract(
        results: List<MediaItem>,
        customAlbums: List<CustomAlbum>,
        customTagsMap: Map<String, Set<String>>,
        customPeopleMap: Map<String, Set<String>>
    ): List<DynamicEntityChip> {
        if (results.isEmpty()) return emptyList()
        val chips = mutableListOf<DynamicEntityChip>()
        val resultUris = results.map { it.uri }.toSet()

        for (album in customAlbums) {
            val count = album.mediaUris.count { it in resultUris }
            if (count > 0) {
                chips.add(DynamicEntityChip("album_${album.id}", album.name, count, ChipKind.ALBUM, album.id))
            }
        }

        val personCounts = mutableMapOf<String, Int>()
        val placeCounts = mutableMapOf<String, Int>()
        val tagCounts = mutableMapOf<String, Int>()

        for (item in results) {
            customPeopleMap[item.uri].orEmpty().forEach { p ->
                personCounts[p] = (personCounts[p] ?: 0) + 1
            }
            val place = PlaceLocationResolver.getCachedPlace(item.uri)
            if (!place.isNullOrBlank()) {
                placeCounts[place] = (placeCounts[place] ?: 0) + 1
            }
            customTagsMap[item.uri].orEmpty().forEach { t ->
                tagCounts[t] = (tagCounts[t] ?: 0) + 1
            }
        }

        personCounts.forEach { (personName, count) ->
            if (count > 0) {
                chips.add(DynamicEntityChip("person_$personName", personName, count, ChipKind.PERSON, "name:$personName"))
            }
        }
        placeCounts.forEach { (placeName, count) ->
            if (count > 0) {
                chips.add(DynamicEntityChip("place_$placeName", placeName, count, ChipKind.PLACE, placeName))
            }
        }
        tagCounts.forEach { (tag, count) ->
            if (count > 0) {
                chips.add(DynamicEntityChip("tag_$tag", "#$tag", count, ChipKind.TAG, tag))
            }
        }

        return chips.take(6)
    }
}
