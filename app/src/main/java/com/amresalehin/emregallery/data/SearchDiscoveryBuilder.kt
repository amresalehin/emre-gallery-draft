package com.amresalehin.emregallery.data

import android.content.Context
import com.amresalehin.emregallery.face.FaceClusterStore

/**
 * Coordinates State A discovery section assembly across people, places,
 * albums/folders, tags, and category shortcuts.
 */
internal class SearchDiscoveryBuilder(
    context: Context,
    faceStore: FaceClusterStore,
    locationCache: PhotoMapLocationCache
) {

    private val peopleAndPlacesBuilder = DiscoveryPeopleAndPlacesBuilder(context, faceStore, locationCache)
    private val categoryShortcutsBuilder = DiscoveryCategoryShortcutsBuilder(context)

    suspend fun build(
        media: List<MediaItem>,
        customAlbums: List<CustomAlbum>,
        customTagsMap: Map<String, Set<String>>,
        customPeopleMap: Map<String, Set<String>>,
        favourites: Set<String>
    ): DiscoveryData {
        val nonTrashMedia = media.filterNot { it.isTrashed }

        return DiscoveryData(
            categories = categoryShortcutsBuilder.buildCategories(nonTrashMedia, favourites),
            people = peopleAndPlacesBuilder.buildPeople(nonTrashMedia, customPeopleMap),
            places = peopleAndPlacesBuilder.buildPlaces(nonTrashMedia),
            albums = DiscoveryAlbumsAndTagsBuilder.buildAlbums(nonTrashMedia, customAlbums),
            tags = DiscoveryAlbumsAndTagsBuilder.buildTags(nonTrashMedia, customTagsMap)
        )
    }
}
