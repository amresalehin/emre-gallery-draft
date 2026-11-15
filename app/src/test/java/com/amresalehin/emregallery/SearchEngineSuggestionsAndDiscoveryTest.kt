package com.amresalehin.emregallery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emregallery.data.SearchEngine
import com.amresalehin.emregallery.data.SuggestionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchEngineSuggestionsAndDiscoveryTest {

    private lateinit var searchEngine: SearchEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        searchEngine = SearchEngine(context)
    }

    @Test
    fun testLiveSuggestions_maxMatchesPerCategory() = runBlocking {
        val suggestions = searchEngine.getLiveSuggestions(
            query = "Mount",
            media = SearchEngineTestFixtures.sampleMedia,
            recentSearches = listOf("Mountain hike"),
            customAlbums = SearchEngineTestFixtures.sampleAlbums,
            customTagsMap = SearchEngineTestFixtures.sampleTags,
            customPeopleMap = SearchEngineTestFixtures.samplePeople
        )

        assertTrue("Should have suggestions for 'Mount'", suggestions.isNotEmpty())

        // Verify max 2 per category rule
        val byType = suggestions.groupBy { it.type }
        for ((type, items) in byType) {
            assertTrue("Category $type must have at most 2 suggestions but had ${items.size}", items.size <= 2)
        }

        // Verify matching items only
        assertTrue(
            "Album suggestion should match query",
            suggestions.any { it.type == SuggestionType.ALBUM && it.title.contains("Mount") }
        )
        assertTrue(
            "File suggestion should match query",
            suggestions.any { it.type == SuggestionType.FILE && it.title.contains("Mount") }
        )
    }

    @Test
    fun testDiscoveryContent_realEntitiesOnly() = runBlocking {
        val discovery = searchEngine.getDiscoveryContent(
            media = SearchEngineTestFixtures.sampleMedia,
            customAlbums = SearchEngineTestFixtures.sampleAlbums,
            customTagsMap = SearchEngineTestFixtures.sampleTags,
            customPeopleMap = SearchEngineTestFixtures.samplePeople,
            favourites = setOf(SearchEngineTestFixtures.EVEREST_URI)
        )

        assertTrue(discovery.categories.isNotEmpty())
        assertTrue(discovery.albums.any { it.title == "Mountains Trip" })
        assertTrue(discovery.tags.any { it.title == "trekking" })
        assertTrue(discovery.people.any { it.title == "John Doe" })
    }
}
