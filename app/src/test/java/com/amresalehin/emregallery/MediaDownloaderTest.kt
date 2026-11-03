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
}
