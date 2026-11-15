package com.amresalehin.emregallery.data

import android.content.Context
import com.amresalehin.emregallery.face.FaceClusterStore

/**
 * Coordinates State C query execution by chaining [SearchMediaFilterPipeline],
 * [SearchMatchScorer], and [SearchDynamicChipExtractor].
 */
internal class SearchMediaQueryExecutor(
    context: Context,
    faceStore: FaceClusterStore,
    locationCache: PhotoMapLocationCache
) {

    private val filterPipeline = SearchMediaFilterPipeline(context, faceStore, locationCache)
    private val matchScorer = SearchMatchScorer(context)

    fun execute(
        query: String,
        media: List<MediaItem>,
        filter: SearchFilterState,
        sortMode: SearchSortMode,
        customAlbums: List<CustomAlbum>,
        customTagsMap: Map<String, Set<String>>,
        customPeopleMap: Map<String, Set<String>>,
        favourites: Set<String>
    ): SearchResultData {
        val q = query.trim()
        val candidates = filterPipeline.filter(
            media = media,
            filter = filter,
            customAlbums = customAlbums,
            customTagsMap = customTagsMap,
            customPeopleMap = customPeopleMap,
            favourites = favourites
        )

        val sortedList = matchScorer.matchAndSort(
            candidates = candidates,
            query = q,
            sortMode = sortMode,
            customAlbums = customAlbums,
            customTagsMap = customTagsMap,
            customPeopleMap = customPeopleMap
        )

        val dynamicChips = SearchDynamicChipExtractor.extract(
            results = sortedList,
            customAlbums = customAlbums,
            customTagsMap = customTagsMap,
            customPeopleMap = customPeopleMap
        )

        return SearchResultData(
            query = q,
            items = sortedList,
            dynamicChips = dynamicChips,
            totalPhotosCount = sortedList.count { !it.isVideo },
            totalVideosCount = sortedList.count { it.isVideo }
        )
    }
}
