package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.CustomAlbum
import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.data.SearchEngine
import com.amresalehin.emregallery.data.SearchFilterState
import com.amresalehin.emregallery.data.SearchResultData
import com.amresalehin.emregallery.data.SearchSortMode

internal object SearchEngineTestFixtures {

    const val EVEREST_URI = "content://media/external/images/media/1"
    const val SUNSET_VIDEO_URI = "content://media/external/images/media/2"
    const val BEACH_URI = "content://media/external/images/media/3"
    const val PARTY_GIF_URI = "content://media/external/images/media/4"

    val sampleMedia = listOf(
        MediaItem(
            id = 1L,
            uri = EVEREST_URI,
            name = "Mount_Everest.jpg",
            mimeType = "image/jpeg",
            dateAddedSeconds = 1700000000L,
            dateTakenMillis = 1700000000000L,
            sizeBytes = 2048L,
            width = 1920,
            height = 1080,
            relativePath = "DCIM/Camera",
            isVideo = false
        ),
        MediaItem(
            id = 2L,
            uri = SUNSET_VIDEO_URI,
            name = "Mountain_Sunset.mp4",
            mimeType = "video/mp4",
            dateAddedSeconds = 1710000000L,
            dateTakenMillis = 1710000000000L,
            sizeBytes = 10485760L,
            width = 1920,
            height = 1080,
            relativePath = "DCIM/Camera",
            isVideo = true
        ),
        MediaItem(
            id = 3L,
            uri = BEACH_URI,
            name = "Beach_Day.jpg",
            mimeType = "image/jpeg",
            dateAddedSeconds = 1690000000L,
            dateTakenMillis = 1690000000000L,
            sizeBytes = 4096L,
            width = 1080,
            height = 1920,
            relativePath = "Pictures/Vacation",
            isVideo = false
        ),
        MediaItem(
            id = 4L,
            uri = PARTY_GIF_URI,
            name = "Party_Animation.gif",
            mimeType = "image/gif",
            dateAddedSeconds = 1715000000L,
            dateTakenMillis = 1715000000000L,
            sizeBytes = 204800L,
            width = 500,
            height = 500,
            relativePath = "Pictures/Gifs",
            isVideo = false
        )
    )

    val sampleAlbums = listOf(
        CustomAlbum(
            id = "album_mountains",
            name = "Mountains Trip",
            parentPath = "",
            mediaUris = setOf(EVEREST_URI, SUNSET_VIDEO_URI)
        )
    )

    val sampleTags = mapOf(
        EVEREST_URI to setOf("trekking", "nature"),
        BEACH_URI to setOf("beach", "summer")
    )

    val samplePeople = mapOf(
        EVEREST_URI to setOf("John Doe")
    )
}

internal suspend fun SearchEngine.searchForTest(
    query: String = "",
    media: List<MediaItem> = SearchEngineTestFixtures.sampleMedia,
    filter: SearchFilterState = SearchFilterState(),
    sortMode: SearchSortMode = SearchSortMode.RELEVANCE,
    customAlbums: List<CustomAlbum> = emptyList(),
    customTagsMap: Map<String, Set<String>> = emptyMap(),
    customPeopleMap: Map<String, Set<String>> = emptyMap(),
    favourites: Set<String> = emptySet()
): SearchResultData {
    return searchMedia(
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
