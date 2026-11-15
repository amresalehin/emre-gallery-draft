package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.MediaCategory
import com.amresalehin.emregallery.data.MediaInboxItem
import com.amresalehin.emregallery.data.MediaInboxStack
import com.amresalehin.emregallery.data.MediaProvenance
import com.amresalehin.emregallery.data.ScrapeResult
import com.amresalehin.emregallery.data.ScrapedMediaItem
import com.amresalehin.emregallery.ui.Destination
import com.amresalehin.emregallery.ui.DetectedPlatform
import com.amresalehin.emregallery.ui.DownloaderViewMode
import com.amresalehin.emregallery.ui.NavBackStackEntry
import com.amresalehin.emregallery.ui.detectPlatform
import com.amresalehin.emregallery.ui.passesDimensionFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaDownloaderTest {

    @Test
    fun destinationEnumContainsMediaInbox() {
        assertEquals("MEDIA_INBOX", Destination.MEDIA_INBOX.name)
    }

    @Test
    fun navBackStackEntrySupportsMediaInbox() {
        val entry = NavBackStackEntry(
            destination = Destination.MEDIA_INBOX
        )
        assertEquals(Destination.MEDIA_INBOX, entry.destination)
    }

    @Test
    fun mediaInboxStackModelProperties() {
        val item1 = MediaInboxItem(
            id = "item_1",
            title = "scraped_image.jpg",
            localUri = "/data/downloads/scraped_image.jpg",
            sourceUrl = "https://example.com/scraped_image.jpg",
            isVideo = false,
            sizeBytes = 204800L
        )
        val stack = MediaInboxStack(
            id = "stack_1",
            title = "Example Site",
            sourceDomain = "example.com",
            sourceUrl = "https://example.com/gallery",
            items = listOf(item1)
        )

        assertEquals("stack_1", stack.id)
        assertEquals("Example Site", stack.title)
        assertEquals("example.com", stack.sourceDomain)
        assertEquals(1, stack.items.size)
        assertEquals("scraped_image.jpg", stack.items.first().title)
    }

    @Test
    fun scrapeResultFiltering() {
        val items = listOf(
            ScrapedMediaItem("1", "https://site.com/1.jpg", "Photo1", isVideo = false),
            ScrapedMediaItem("2", "https://site.com/2.mp4", "Video1", isVideo = true),
            ScrapedMediaItem("3", "https://site.com/3.png", "Photo2", isVideo = false)
        )
        val result = ScrapeResult(
            sourceUrl = "https://site.com",
            pageTitle = "My Site",
            domain = "site.com",
            items = items
        )

        assertEquals(3, result.items.size)
        val imagesOnly = result.items.filter { !it.isVideo && !it.isAudio }
        val videosOnly = result.items.filter { it.isVideo }
        val audioOnly = result.items.filter { it.isAudio }

        assertEquals(2, imagesOnly.size)
        assertEquals(1, videosOnly.size)
        assertEquals(0, audioOnly.size)
    }

    @Test
    fun scrapeResultAudioFiltering() {
        val items = listOf(
            ScrapedMediaItem("1", "https://site.com/track.mp3", "Track 1", isVideo = false, isAudio = true),
            ScrapedMediaItem("2", "https://site.com/video.mp4", "Video 1", isVideo = true, isAudio = false),
            ScrapedMediaItem("3", "https://site.com/photo.jpg", "Photo 1", isVideo = false, isAudio = false)
        )
        val result = ScrapeResult("https://site.com", "Site", "site.com", items)
        val audios = result.items.filter { it.isAudio }
        assertEquals(1, audios.size)
        assertEquals("audio/mpeg", audios.first().mimeType)
    }

    @Test
    fun mediaSelectionBatchOperations() {
        var selectedIds = setOf("1", "2")
        val toggleId = "3"

        selectedIds = selectedIds + toggleId
        assertTrue(selectedIds.contains("3"))
        assertEquals(3, selectedIds.size)

        selectedIds = selectedIds - "1"
        assertFalse(selectedIds.contains("1"))
        assertEquals(2, selectedIds.size)
    }

    @Test
    fun selectAllAndSortLogic() {
        val items = listOf(
            ScrapedMediaItem("1", "https://site.com/b.jpg", "Beta", isVideo = false),
            ScrapedMediaItem("2", "https://site.com/a.mp4", "Alpha", isVideo = true)
        )
        val allIds = items.map { it.id }.toSet()
        assertEquals(2, allIds.size)

        val sortedByTitle = items.sortedBy { it.title }
        assertEquals("Alpha", sortedByTitle[0].title)
        assertEquals("Beta", sortedByTitle[1].title)
    }

    @Test
    fun scrapedMediaItemUnifiedCandidateModelHasProvenanceAndHeaders() {
        val provenance = MediaProvenance(
            extractorEngine = "Static HTML (img)",
            pageUrl = "https://example.com/gallery",
            domSelector = "img"
        )
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0",
            "Referer" to "https://example.com/gallery"
        )
        val item = ScrapedMediaItem(
            id = "scraped_item_99",
            url = "https://example.com/media/hero.jpg",
            title = "Hero Image",
            isVideo = false,
            category = MediaCategory.IMAGES,
            format = "JPG",
            provenance = provenance,
            metadata = mapOf("resolution" to "1920x1080"),
            downloadHeaders = headers
        )

        assertEquals("Static HTML (img)", item.provenance.extractorEngine)
        assertEquals("https://example.com/gallery", item.provenance.pageUrl)
        assertEquals("img", item.provenance.domSelector)
        assertEquals("1920x1080", item.metadata["resolution"])
        assertEquals("https://example.com/gallery", item.downloadHeaders["Referer"])
    }

    @Test
    fun testStrictDimensionFilteringAndAudioPreservation() {
        val imgSmall = ScrapedMediaItem("img1", "https://site.com/sm.jpg", "Small", isVideo = false, isAudio = false, category = MediaCategory.IMAGES)
        val imgMid = ScrapedMediaItem("img2", "https://site.com/mid.jpg", "Mid", isVideo = false, isAudio = false, category = MediaCategory.IMAGES)
        val imgLarge = ScrapedMediaItem("img3", "https://site.com/lg.jpg", "Large", isVideo = false, isAudio = false, category = MediaCategory.IMAGES)
        val imgUnknown = ScrapedMediaItem("img4", "https://site.com/unk.jpg", "Unknown", isVideo = false, isAudio = false, category = MediaCategory.IMAGES)
        val audioItem = ScrapedMediaItem("aud1", "https://site.com/song.mp3", "Song", isVideo = false, isAudio = true, category = MediaCategory.AUDIO)

        val dims = mapOf(
            "img1" to Pair(400, 300),
            "img2" to Pair(800, 500),
            "img3" to Pair(1200, 900)
        )

        assertFalse(passesDimensionFilter(imgSmall, 800, 600, dims))
        assertFalse(passesDimensionFilter(imgMid, 800, 600, dims))
        assertTrue(passesDimensionFilter(imgLarge, 800, 600, dims))
        assertFalse(passesDimensionFilter(imgUnknown, 800, 600, dims))
        assertTrue(passesDimensionFilter(audioItem, 800, 600, dims))

        assertTrue(passesDimensionFilter(imgSmall, 0, 0, dims))
        assertTrue(passesDimensionFilter(imgMid, 0, 0, dims))
        assertTrue(passesDimensionFilter(imgLarge, 0, 0, dims))
        assertTrue(passesDimensionFilter(imgUnknown, 0, 0, dims))
        assertTrue(passesDimensionFilter(audioItem, 0, 0, dims))
    }

    @Test
    fun testVideosExcludedFromDimensionFiltering() {
        val videoMp4 = ScrapedMediaItem("vid1", "https://site.com/video.mp4", "Video", isVideo = true, isAudio = false, category = MediaCategory.VIDEOS)
        val streamHls = ScrapedMediaItem("str1", "https://site.com/stream.m3u8", "Stream", isVideo = true, isAudio = false, isStream = true, category = MediaCategory.STREAMS)
        val videoWebm = ScrapedMediaItem("vid2", "https://site.com/clip.webm", "Clip", isVideo = true, isAudio = false, category = MediaCategory.VIDEOS)

        val emptyDims = emptyMap<String, Pair<Int, Int>>()

        assertTrue(passesDimensionFilter(videoMp4, 3840, 2160, emptyDims))
        assertTrue(passesDimensionFilter(streamHls, 3840, 2160, emptyDims))
        assertTrue(passesDimensionFilter(videoWebm, 1920, 1080, emptyDims))
    }

    @Test
    fun testDownloaderViewModeEnum() {
        assertEquals("SEARCH_PAGE", DownloaderViewMode.SEARCH_PAGE.name)
        assertEquals("DOWNLOADER_PAGE", DownloaderViewMode.DOWNLOADER_PAGE.name)
        assertEquals("SAVED_LIBRARY", DownloaderViewMode.SAVED_LIBRARY.name)
    }

    @Test
    fun testPlatformDetectionFromUrls() {
        assertEquals(DetectedPlatform.YOUTUBE, detectPlatform("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(DetectedPlatform.YOUTUBE, detectPlatform("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(DetectedPlatform.INSTAGRAM, detectPlatform("https://instagram.com/reel/Cx123456/"))
        assertEquals(DetectedPlatform.TIKTOK, detectPlatform("https://www.tiktok.com/@user/video/1234567890"))
        assertEquals(DetectedPlatform.TWITTER, detectPlatform("https://x.com/user/status/123456"))
        assertEquals(DetectedPlatform.TWITTER, detectPlatform("https://twitter.com/user/status/123456"))
        assertEquals(DetectedPlatform.REDDIT, detectPlatform("https://reddit.com/r/pics/comments/xyz123"))
        assertEquals(DetectedPlatform.PINTEREST, detectPlatform("https://pinterest.com/pin/123456789/"))
        assertEquals(DetectedPlatform.FACEBOOK, detectPlatform("https://www.facebook.com/watch/?v=123456"))
        assertEquals(DetectedPlatform.DIRECT_MEDIA, detectPlatform("https://example.com/videos/stream.m3u8"))
        assertEquals(DetectedPlatform.DIRECT_MEDIA, detectPlatform("https://example.com/clip.mp4"))
        assertEquals(DetectedPlatform.DIRECT_MEDIA, detectPlatform("https://example.com/photo.jpg"))
        assertEquals(DetectedPlatform.GENERIC_WEB, detectPlatform("https://wikipedia.org/wiki/Earth"))
    }
}
