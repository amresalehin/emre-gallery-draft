package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.MediaInboxItem
import com.amresalehin.emregallery.data.MediaInboxStack
import com.amresalehin.emregallery.data.ScrapedMediaItem
import com.amresalehin.emregallery.data.ScrapeResult
import com.amresalehin.emregallery.ui.Destination
import com.amresalehin.emregallery.ui.NavBackStackEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

        // Add
        selectedIds = selectedIds + toggleId
        assertTrue(selectedIds.contains("3"))
        assertEquals(3, selectedIds.size)

        // Remove
        selectedIds = selectedIds - "1"
        assertEquals(false, selectedIds.contains("1"))
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
    fun mediaClassifierDetectsStreamsAndFormats() {
        val hls = com.amresalehin.emregallery.data.MediaClassifier.classify("https://example.com/live/playlist.m3u8")
        assertEquals(com.amresalehin.emregallery.data.MediaCategory.STREAMS, hls.category)
        assertTrue(hls.isStream)
        assertTrue(hls.isVideo)
        assertEquals("HLS (m3u8)", hls.format)

        val dash = com.amresalehin.emregallery.data.MediaClassifier.classify("https://example.com/video/manifest.mpd")
        assertEquals(com.amresalehin.emregallery.data.MediaCategory.STREAMS, dash.category)
        assertTrue(dash.isStream)
        assertEquals("DASH (mpd)", dash.format)

        val mp4 = com.amresalehin.emregallery.data.MediaClassifier.classify("https://example.com/clip.mp4")
        assertEquals(com.amresalehin.emregallery.data.MediaCategory.VIDEOS, mp4.category)
        assertEquals("MP4", mp4.format)

        val mp3 = com.amresalehin.emregallery.data.MediaClassifier.classify("https://example.com/audio.mp3")
        assertEquals(com.amresalehin.emregallery.data.MediaCategory.AUDIO, mp3.category)
        assertTrue(mp3.isAudio)

        val webp = com.amresalehin.emregallery.data.MediaClassifier.classify("https://example.com/photo.webp")
        assertEquals(com.amresalehin.emregallery.data.MediaCategory.IMAGES, webp.category)
        assertEquals("WEBP", webp.format)
    }

    @Test
    fun mediaClassifierCanonicalDeduplicationStripsTrackingParams() {
        val urlWithTracking = "https://example.com/image.jpg?utm_source=twitter&utm_medium=social&id=42&fbclid=xyz"
        val canonical = com.amresalehin.emregallery.data.MediaClassifier.canonicalKey(urlWithTracking)
        assertTrue(canonical.contains("id=42"))
        assertEquals(false, canonical.contains("utm_source"))
        assertEquals(false, canonical.contains("fbclid"))

        // Duplicate URL with different tracking params resolves to same canonical key
        val urlDifferentTracking = "https://example.com/image.jpg?fbclid=different&id=42&utm_campaign=winter"
        val canonical2 = com.amresalehin.emregallery.data.MediaClassifier.canonicalKey(urlDifferentTracking)
        assertEquals(canonical, canonical2)
    }

    @Test
    fun mediaClassifierUrlResolution() {
        val base = "https://example.com/articles/index.html"
        val relative = com.amresalehin.emregallery.data.MediaClassifier.resolveUrl("/images/hero.png", base)
        assertEquals("https://example.com/images/hero.png", relative)

        val protocolRelative = com.amresalehin.emregallery.data.MediaClassifier.resolveUrl("//cdn.example.com/video.mp4", base)
        assertEquals("https://cdn.example.com/video.mp4", protocolRelative)
    }

    @Test
    fun mediaClassifierSrcsetParsing() {
        val srcset = "thumb.jpg 320w, medium.jpg 640w, large.jpg 1280w"
        val parsed = com.amresalehin.emregallery.data.MediaClassifier.parseSrcset(srcset, "https://example.com/")
        assertEquals(3, parsed.size)
        assertEquals("https://example.com/thumb.jpg", parsed[0].first)
        assertEquals("320w", parsed[0].second)
        assertEquals("https://example.com/large.jpg", parsed[2].first)
        assertEquals("1280w", parsed[2].second)
    }
}
