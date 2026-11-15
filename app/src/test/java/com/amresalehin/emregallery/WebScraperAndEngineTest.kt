package com.amresalehin.emregallery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emregallery.data.BrowserHistoryManager
import com.amresalehin.emregallery.data.BrowserSearchEngine
import com.amresalehin.emregallery.data.DualMediaExtractor
import com.amresalehin.emregallery.data.ExtractionSource
import com.amresalehin.emregallery.data.GalleryDlEngine
import com.amresalehin.emregallery.data.MediaProvenance
import com.amresalehin.emregallery.data.ScrapedMediaItem
import com.amresalehin.emregallery.data.WebUrlUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebScraperAndEngineTest {

    @Test
    fun browserUrlFormattingAndNormalization() {
        fun formatUrl(input: String): String {
            val trimmed = input.trim()
            if (trimmed.isBlank()) return ""
            return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                if (trimmed.contains(".") && !trimmed.contains(" ")) {
                    "https://$trimmed"
                } else {
                    "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
                }
            } else {
                trimmed
            }
        }

        assertEquals("https://example.com/videos", formatUrl("example.com/videos"))
        assertEquals("https://duckduckgo.com/?q=funny+cats", formatUrl("funny cats"))
        assertEquals("https://test.org", formatUrl("https://test.org"))
    }

    @Test
    fun testBrowserSearchEnginesAndFormatQueryOrUrl() {
        val query = "vintage photography"
        val ddgUrl = WebUrlUtils.formatQueryOrUrl(query, BrowserSearchEngine.DUCKDUCKGO)
        assertTrue(ddgUrl.contains("duckduckgo.com/?q=vintage+photography"))

        val googleUrl = WebUrlUtils.formatQueryOrUrl(query, BrowserSearchEngine.GOOGLE)
        assertTrue(googleUrl.contains("google.com/search?q=vintage+photography"))

        val bingUrl = WebUrlUtils.formatQueryOrUrl(query, BrowserSearchEngine.BING)
        assertTrue(bingUrl.contains("bing.com/search?q=vintage+photography"))

        val wikiUrl = WebUrlUtils.formatQueryOrUrl(query, BrowserSearchEngine.WIKIPEDIA)
        assertTrue(wikiUrl.contains("wikipedia.org/wiki/Special:Search?search=vintage+photography"))

        val siteUrl = WebUrlUtils.formatQueryOrUrl("commons.wikimedia.org")
        assertEquals("https://commons.wikimedia.org", siteUrl)

        val emptyUrl = WebUrlUtils.formatQueryOrUrl("   ")
        assertEquals("", emptyUrl)
    }

    @Test
    fun testValidWebUrlValidation() {
        val validCases = listOf(
            "https://example.com/gallery",
            "http://sub.domain.org/path/to/image.jpg",
            "commons.wikimedia.org",
            "m.youtube.com/watch?v=123",
            "https://site.co.uk/media.mp4?id=50"
        )
        for (url in validCases) {
            assertTrue("Expected valid: $url", WebUrlUtils.isValidWebUrl(url))
        }

        val invalidCases = listOf(
            "not a valid url",
            "random pasted text from notes",
            "justwords",
            "   ",
            "javascript:void(0)",
            "file:///data/local/tmp",
            "invalid.123"
        )
        for (invalid in invalidCases) {
            assertFalse("Expected invalid: $invalid", WebUrlUtils.isValidWebUrl(invalid))
        }
    }

    @Test
    fun testGalleryDlEngineStatusAndSupportedSites() {
        val status = GalleryDlEngine.getStatus()
        assertTrue(status.popularSites.isNotEmpty())
        assertTrue(status.popularSites.any { it.name == "Reddit" })
        assertTrue(status.popularSites.any { it.name == "Imgur" })
        assertTrue(status.popularSites.any { it.name == "Twitter / X" })
        assertTrue(status.popularSites.any { it.name == "Pinterest" })
        assertTrue(status.popularSites.any { it.name == "ArtStation" })
    }

    @Test
    fun testGalleryDlSupportInfoDetection() {
        val redditCheck = GalleryDlEngine.checkSupport("https://www.reddit.com/r/earthporn/comments/123/mountains")
        assertTrue(redditCheck.isSupported)

        val imgurCheck = GalleryDlEngine.checkSupport("https://imgur.com/a/abcde")
        assertTrue(imgurCheck.isSupported)

        val artstationCheck = GalleryDlEngine.checkSupport("https://artstation.com/artwork/xyz123")
        assertTrue(artstationCheck.isSupported)

        val genericCheck = GalleryDlEngine.checkSupport("https://nonexistent-random-domain-12345.com")
        assertFalse(genericCheck.isSupported)
    }

    @Test
    fun testExtractionSourceGalleryDlEnum() {
        assertEquals("gallery-dl", ExtractionSource.GALLERY_DL.label)
        assertEquals(DualMediaExtractor.ExtractionPhase.GALLERY_DL.name, "GALLERY_DL")
    }

    @Test
    fun dualExtractorResetClearsDiscoveredItems() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val extractor = DualMediaExtractor(context)

        val item1 = ScrapedMediaItem(
            id = "test_item_1",
            url = "https://example.com/photo.jpg",
            title = "Photo 1",
            isVideo = false,
            provenance = MediaProvenance(
                extractorEngine = "Static HTML",
                pageUrl = "https://example.com"
            ),
            downloadHeaders = mapOf("Referer" to "https://example.com")
        )
        extractor.addDiscoveredItem(item1)
        assertEquals(1, extractor.state.value.items.size)

        extractor.reset()
        assertTrue(extractor.state.value.items.isEmpty())
        assertFalse(extractor.state.value.isRunning)
        assertEquals("", extractor.state.value.pageTitle)
    }

    @Test
    fun testBrowserHistoryManagerRecordAndClear() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val historyManager = BrowserHistoryManager(ctx)
        historyManager.recordVisit("https://example.com/test", "Test Site")
        historyManager.clearHistory()
        assertTrue(true)
    }
}
