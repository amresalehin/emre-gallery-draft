package com.amresalehin.emregallery.data

import android.content.Context
import com.amresalehin.emregallery.face.FaceClusterStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Facade for fast in-memory and database-backed search discovery, live suggestions,
 * and multi-criteria filtering/ranking.
 */
class SearchEngine(context: Context) {

    private val appContext = context.applicationContext
    private val faceStore = FaceClusterStore(appContext)
    private val locationCache = PhotoMapLocationCache(appContext)

    private val discoveryBuilder = SearchDiscoveryBuilder(appContext, faceStore, locationCache)
    private val suggestionsBuilder = SearchSuggestionsBuilder(appContext, faceStore, locationCache)
    private val queryExecutor = SearchMediaQueryExecutor(appContext, faceStore, locationCache)

    /**
     * Builds real discovery sections for State A (Search opened, no query entered).
     * Omits categories and sections for which the library has no data.
     */
    suspend fun getDiscoveryContent(
        media: List<MediaItem>,
        customAlbums: List<CustomAlbum>,
        customTagsMap: Map<String, Set<String>>,
        customPeopleMap: Map<String, Set<String>>,
        favourites: Set<String>
    ): DiscoveryData = withContext(Dispatchers.IO) {
        discoveryBuilder.build(
            media = media,
            customAlbums = customAlbums,
            customTagsMap = customTagsMap,
            customPeopleMap = customPeopleMap,
            favourites = favourites
        )
    }

    /**
     * Computes live suggestions (State B) while actively typing.
     * At most 1–2 suggestions per category, limited to categories with real matches.
     */
    suspend fun getLiveSuggestions(
        query: String,
        media: List<MediaItem>,
        recentSearches: List<String>,
        customAlbums: List<CustomAlbum>,
        customTagsMap: Map<String, Set<String>>,
        customPeopleMap: Map<String, Set<String>>
    ): List<SearchSuggestion> = withContext(Dispatchers.IO) {
        suggestionsBuilder.build(
            query = query,
            media = media,
            recentSearches = recentSearches,
            customAlbums = customAlbums,
            customTagsMap = customTagsMap,
            customPeopleMap = customPeopleMap
        )
    }

    /**
     * Executes real multi-criteria search filtering and ranking for State C.
     */
    suspend fun searchMedia(
        query: String,
        media: List<MediaItem>,
        filter: SearchFilterState,
        sortMode: SearchSortMode,
        customAlbums: List<CustomAlbum>,
        customTagsMap: Map<String, Set<String>>,
        customPeopleMap: Map<String, Set<String>>,
        favourites: Set<String>
    ): SearchResultData = withContext(Dispatchers.Default) {
        queryExecutor.execute(
            query = query,
            media = media,
            filter = filter,
            sortMode = sortMode,
            customAlbums = customAlbums,
            customTagsMap = customTagsMap,
            customPeopleMap = customPeopleMap,
            favourites = favourites
        )
    }

    companion object {
        fun isGif(item: MediaItem): Boolean =
            item.mimeType.equals("image/gif", ignoreCase = true) ||
                item.name.endsWith(".gif", ignoreCase = true)
    }
}
