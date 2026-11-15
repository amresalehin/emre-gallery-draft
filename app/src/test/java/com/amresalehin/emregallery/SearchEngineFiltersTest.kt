package com.amresalehin.emregallery

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emregallery.data.FaceFilter
import com.amresalehin.emregallery.data.GpsFilter
import com.amresalehin.emregallery.data.MediaTypeFilter
import com.amresalehin.emregallery.data.OcrTextFilter
import com.amresalehin.emregallery.data.PhotoMapLocationCache
import com.amresalehin.emregallery.data.SearchEngine
import com.amresalehin.emregallery.data.SearchFilterState
import com.amresalehin.emregallery.ocr.TesseractOcr
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchEngineFiltersTest {

    private lateinit var context: Context
    private lateinit var searchEngine: SearchEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        searchEngine = SearchEngine(context)
    }

    @Test
    fun testSearchMedia_mediaTypeFilters_imagesVideosGifs() = runBlocking {
        val imageResults = searchEngine.searchForTest(
            filter = SearchFilterState(mediaType = MediaTypeFilter.PHOTOS)
        )
        assertEquals(2, imageResults.items.size)
        assertTrue(imageResults.items.all { !it.isVideo && it.name.endsWith(".jpg") })

        val videoResults = searchEngine.searchForTest(
            filter = SearchFilterState(mediaType = MediaTypeFilter.VIDEOS)
        )
        assertEquals(1, videoResults.items.size)
        assertEquals("Mountain_Sunset.mp4", videoResults.items.first().name)

        val gifResults = searchEngine.searchForTest(
            filter = SearchFilterState(mediaType = MediaTypeFilter.GIFS)
        )
        assertEquals(1, gifResults.items.size)
        assertEquals("Party_Animation.gif", gifResults.items.first().name)
    }

    @Test
    fun testSearchMedia_gpsFilters() = runBlocking {
        val locationCache = PhotoMapLocationCache(context)
        locationCache.save(SearchEngineTestFixtures.EVEREST_URI, GeoPoint(27.9881, 86.9250))
        locationCache.save(SearchEngineTestFixtures.BEACH_URI, null)

        val gpsResults = searchEngine.searchForTest(
            filter = SearchFilterState(gpsFilter = GpsFilter.HAS_GPS)
        )
        assertTrue(gpsResults.items.any { it.uri == SearchEngineTestFixtures.EVEREST_URI })
        assertFalse(gpsResults.items.any { it.uri == SearchEngineTestFixtures.BEACH_URI })

        val noGpsResults = searchEngine.searchForTest(
            filter = SearchFilterState(gpsFilter = GpsFilter.NO_GPS)
        )
        assertTrue(noGpsResults.items.any { it.uri == SearchEngineTestFixtures.BEACH_URI })
        assertFalse(noGpsResults.items.any { it.uri == SearchEngineTestFixtures.EVEREST_URI })
    }

    @Test
    fun testSearchMedia_ocrTextFilters() = runBlocking {
        TesseractOcr.saveCachedResult(
            context,
            Uri.parse(SearchEngineTestFixtures.EVEREST_URI),
            "Welcome to Everest Base Camp"
        )

        val textResults = searchEngine.searchForTest(
            filter = SearchFilterState(ocrTextFilter = OcrTextFilter.HAS_TEXT)
        )
        assertTrue(textResults.items.any { it.uri == SearchEngineTestFixtures.EVEREST_URI })
        assertFalse(textResults.items.any { it.uri == SearchEngineTestFixtures.BEACH_URI })

        val withoutTextResults = searchEngine.searchForTest(
            filter = SearchFilterState(ocrTextFilter = OcrTextFilter.NO_TEXT)
        )
        assertTrue(withoutTextResults.items.any { it.uri == SearchEngineTestFixtures.BEACH_URI })
        assertFalse(withoutTextResults.items.any { it.uri == SearchEngineTestFixtures.EVEREST_URI })
    }

    @Test
    fun testSearchMedia_faceFilters() = runBlocking {
        val faceResults = searchEngine.searchForTest(
            filter = SearchFilterState(faceFilter = FaceFilter.HAS_FACE),
            customPeopleMap = SearchEngineTestFixtures.samplePeople
        )
        assertTrue(faceResults.items.any { it.uri == SearchEngineTestFixtures.EVEREST_URI })
        assertFalse(faceResults.items.any { it.uri == SearchEngineTestFixtures.BEACH_URI })

        val noFaceResults = searchEngine.searchForTest(
            filter = SearchFilterState(faceFilter = FaceFilter.NO_FACE),
            customPeopleMap = SearchEngineTestFixtures.samplePeople
        )
        assertTrue(noFaceResults.items.any { it.uri == SearchEngineTestFixtures.BEACH_URI })
        assertFalse(noFaceResults.items.any { it.uri == SearchEngineTestFixtures.EVEREST_URI })
    }
}
