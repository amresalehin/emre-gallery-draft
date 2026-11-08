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

class SearchEngine(internal val context: Context) {

    internal val faceStore = FaceClusterStore(context.applicationContext)
    internal val locationCache = PhotoMapLocationCache(context.applicationContext)

    suspend fun getDiscoveryContent(
        allMedia: List<MediaItem>,
        customAlbums: List<CustomAlbum>,
        customTagsMap: Map<String, Set<String>>,
        customPeopleMap: Map<String, Set<String>>,
        favourites: Set<String>
    ): DiscoveryData = getDiscoveryContentImpl(allMedia, customAlbums, customTagsMap, customPeopleMap, favourites)

    suspend fun getLiveSuggestions(
        query: String,
        allMedia: List<MediaItem>,
        customAlbums: List<CustomAlbum>,
        customTagsMap: Map<String, Set<String>>,
        customPeopleMap: Map<String, Set<String>>
    ): List<SearchSuggestion> = getLiveSuggestionsImpl(query, allMedia, customAlbums, customTagsMap, customPeopleMap)

    suspend fun searchMedia(
        query: String,
        filterState: SearchFilterState,
        sortMode: SearchSortMode,
        allMedia: List<MediaItem>,
        customAlbums: List<CustomAlbum>,
        customTagsMap: Map<String, Set<String>>,
        customPeopleMap: Map<String, Set<String>>,
        favourites: Set<String>
    ): SearchResultData = searchMediaImpl(query, filterState, sortMode, allMedia, customAlbums, customTagsMap, customPeopleMap, favourites)

    companion object {
        const val MAX_SUGGESTIONS_PER_CATEGORY = 6
        const val MAX_RECENT_SEARCHES = 10
    }
}
