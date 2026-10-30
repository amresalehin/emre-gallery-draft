package com.amresalehin.emregallery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emregallery.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchEngineTest {

    private lateinit var context: Context
    private lateinit var searchEngine: SearchEngine

    private val sampleMedia = listOf(
        MediaItem(
            id = 1L,
            uri = "content://media/external/images/media/1",
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
            uri = "content://media/external/images/media/2",
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
            uri = "content://media/external/images/media/3",
            name = "Beach_Day.jpg",
            mimeType = "image/jpeg",
            dateAddedSeconds = 1690000000L,
            dateTakenMillis = 1690000000000L,
            sizeBytes = 4096L,
            width = 1080,
            height = 1920,
            relativePath = "Pictures/Vacation",
            isVideo = false
        )
    )

    private val sampleAlbums = listOf(
        CustomAlbum(
            id = "album_mountains",
            name = "Mountains Trip",
            parentPath = "",
            mediaUris = setOf(
                "content://media/external/images/media/1",
                "content://media/external/images/media/2"
            )
        )
    )

    private val sampleTags = mapOf(
        "content://media/external/images/media/1" to setOf("trekking", "nature"),
        "content://media/external/images/media/3" to setOf("beach", "summer")
    )

    private val samplePeople = mapOf(
        "content://media/external/images/media/1" to setOf("John Doe")
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        searchEngine = SearchEngine(context)
    }

    @Test
    fun testLiveSuggestions_maxMatchesPerCategory() = runBlocking {
        val query = "Mount"
        val suggestions = searchEngine.getLiveSuggestions(
            query = query,
            media = sampleMedia,
            recentSearches = listOf("Mountain hike"),
            customAlbums = sampleAlbums,
            customTagsMap = sampleTags,
            customPeopleMap = samplePeople
        )

        assertTrue("Should have suggestions for 'Mount'", suggestions.isNotEmpty())

        // Verify max 2 per category rule
        val byType = suggestions.groupBy { it.type }
        for ((type, items) in byType) {
            assertTrue("Category $type must have at most 2 suggestions but had ${items.size}", items.size <= 2)
        }

        // Verify matching items only
        assertTrue("Album suggestion should match query", suggestions.any { it.type == SuggestionType.ALBUM && it.title.contains("Mount") })
        assertTrue("File suggestion should match query", suggestions.any { it.type == SuggestionType.FILE && it.title.contains("Mount") })
    }

    @Test
    fun testSearchMedia_filtersAndMatching() = runBlocking {
        // Query "Mount"
        val results = searchEngine.searchMedia(
            query = "Mount",
            media = sampleMedia,
            filter = SearchFilterState(),
            sortMode = SearchSortMode.RELEVANCE,
            customAlbums = sampleAlbums,
            customTagsMap = sampleTags,
            customPeopleMap = samplePeople,
            favourites = emptySet()
        )

        assertEquals("Should match the 2 mountain items", 2, results.items.size)
        assertTrue(results.items.any { it.name == "Mount_Everest.jpg" })
        assertTrue(results.items.any { it.name == "Mountain_Sunset.mp4" })

        // Filter by Videos Only
        val videoResults = searchEngine.searchMedia(
            query = "Mount",
            media = sampleMedia,
            filter = SearchFilterState(mediaType = MediaTypeFilter.VIDEOS),
            sortMode = SearchSortMode.RELEVANCE,
            customAlbums = sampleAlbums,
            customTagsMap = sampleTags,
            customPeopleMap = samplePeople,
            favourites = emptySet()
        )
        assertEquals(1, videoResults.items.size)
        assertTrue(videoResults.items.first().isVideo)
        assertEquals("Mountain_Sunset.mp4", videoResults.items.first().name)

        // Dynamic chips should include matching album
        assertTrue(results.dynamicChips.any { it.kind == ChipKind.ALBUM && it.label == "Mountains Trip" })
    }

    @Test
    fun testSearchMedia_tagMatching() = runBlocking {
        val results = searchEngine.searchMedia(
            query = "trekking",
            media = sampleMedia,
            filter = SearchFilterState(),
            sortMode = SearchSortMode.RELEVANCE,
            customAlbums = sampleAlbums,
            customTagsMap = sampleTags,
            customPeopleMap = samplePeople,
            favourites = emptySet()
        )

        assertEquals(1, results.items.size)
        assertEquals("Mount_Everest.jpg", results.items.first().name)
    }

    @Test
    fun testDiscoveryContent_realEntitiesOnly() = runBlocking {
        val discovery = searchEngine.getDiscoveryContent(
            media = sampleMedia,
            customAlbums = sampleAlbums,
            customTagsMap = sampleTags,
            customPeopleMap = samplePeople,
            favourites = setOf("content://media/external/images/media/1")
        )

        // Non-empty sections exist
        assertTrue(discovery.categories.isNotEmpty())
        assertTrue(discovery.albums.any { it.title == "Mountains Trip" })
        assertTrue(discovery.tags.any { it.title == "trekking" })
        assertTrue(discovery.people.any { it.title == "John Doe" })
    }
}
