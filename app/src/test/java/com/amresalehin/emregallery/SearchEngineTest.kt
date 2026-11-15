package com.amresalehin.emregallery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emregallery.data.ChipKind
import com.amresalehin.emregallery.data.MediaTypeFilter
import com.amresalehin.emregallery.data.SearchEngine
import com.amresalehin.emregallery.data.SearchFilterState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchEngineTest {

    private lateinit var searchEngine: SearchEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        searchEngine = SearchEngine(context)
    }

    @Test
    fun testSearchMedia_filtersAndMatching() = runBlocking {
        val results = searchEngine.searchForTest(
            query = "Mount",
            customAlbums = SearchEngineTestFixtures.sampleAlbums,
            customTagsMap = SearchEngineTestFixtures.sampleTags,
            customPeopleMap = SearchEngineTestFixtures.samplePeople
        )

        assertEquals("Should match the 2 mountain items", 2, results.items.size)
        assertTrue(results.items.any { it.name == "Mount_Everest.jpg" })
        assertTrue(results.items.any { it.name == "Mountain_Sunset.mp4" })

        val videoResults = searchEngine.searchForTest(
            query = "Mount",
            filter = SearchFilterState(mediaType = MediaTypeFilter.VIDEOS),
            customAlbums = SearchEngineTestFixtures.sampleAlbums,
            customTagsMap = SearchEngineTestFixtures.sampleTags,
            customPeopleMap = SearchEngineTestFixtures.samplePeople
        )
        assertEquals(1, videoResults.items.size)
        assertTrue(videoResults.items.first().isVideo)
        assertEquals("Mountain_Sunset.mp4", videoResults.items.first().name)

        // Dynamic chips should include matching album
        assertTrue(results.dynamicChips.any { it.kind == ChipKind.ALBUM && it.label == "Mountains Trip" })
    }

    @Test
    fun testSearchMedia_tagMatching() = runBlocking {
        val results = searchEngine.searchForTest(
            query = "trekking",
            customAlbums = SearchEngineTestFixtures.sampleAlbums,
            customTagsMap = SearchEngineTestFixtures.sampleTags,
            customPeopleMap = SearchEngineTestFixtures.samplePeople
        )

        assertEquals(1, results.items.size)
        assertEquals("Mount_Everest.jpg", results.items.first().name)
    }
}
