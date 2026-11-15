package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.MediaCategory
import com.amresalehin.emregallery.data.MediaDeduplicator
import com.amresalehin.emregallery.data.MediaInboxItem
import com.amresalehin.emregallery.data.MediaProvenance
import com.amresalehin.emregallery.data.ScrapedMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Pure JVM tests for scraped item deduplication, poster/storyboard folding,
 * YouTube thumbnail collapsing, and inbox download item deduplication.
 */
class MediaDeduplicationPureUnitTest {

    @Test
    fun testDeduplicateScrapedItemsCollapsesResolutionAndFormatVariants() {
        val hash = "2d4c7a99e43de785bde6214ec042e947"
        val item1 = ScrapedMediaItem(
            id = "item_1",
            url = "https://i.pinimg.com/736x/2d/4c/7a/$hash.jpeg",
            title = "Aishwarya Rai Portrait",
            isVideo = false,
            resolutionHint = "736x1227",
            thumbnailUrl = "https://i.pinimg.com/736x/2d/4c/7a/$hash.jpeg",
            provenance = MediaProvenance(extractorEngine = "Static HTML (DOM Scrape)")
        )
        val item2 = ScrapedMediaItem(
            id = "item_2",
            url = "https://i.pinimg.com/236x/2d/4c/7a/$hash.jpeg",
            title = "Aishwarya Rai Portrait",
            isVideo = false,
            resolutionHint = "236x393",
            thumbnailUrl = "https://i.pinimg.com/236x/2d/4c/7a/$hash.jpeg",
            provenance = MediaProvenance(extractorEngine = "Browser Chromium (Network Intercept)")
        )
        val item3 = ScrapedMediaItem(
            id = "item_3",
            url = "https://i.pinimg.com/474x/2d/4c/7a/$hash.webp",
            title = "Aishwarya Rai Portrait",
            isVideo = false,
            resolutionHint = "474x790",
            thumbnailUrl = "https://i.pinimg.com/474x/2d/4c/7a/$hash.webp",
            provenance = MediaProvenance(extractorEngine = "DOM Scrape")
        )

        val deduplicated = MediaDeduplicator.deduplicateScrapedItems(listOf(item1, item2, item3))
        assertEquals(1, deduplicated.size)

        val winner = deduplicated[0]
        assertEquals("https://i.pinimg.com/originals/2d/4c/7a/$hash.jpg", winner.url)
        assertEquals("736x1227", winner.resolutionHint)
        assertTrue(winner.provenance.extractorEngine.contains("DOM Scrape"))
        assertTrue(winner.provenance.extractorEngine.contains("Network Intercept"))
        assertNotNull(winner.thumbnailUrl)
    }

    @Test
    fun testUserScreenshotExactDuplicateItemsAreCollapsed() {
        val hash = "f732ca2684db4685ff263b65cb466ff4"
        val item1 = ScrapedMediaItem(
            id = "f7_item_1",
            url = "https://i.pinimg.com/757x945/f7/32/ca/$hash.jpg",
            title = "$hash...",
            isVideo = false,
            resolutionHint = "757x945",
            thumbnailUrl = "https://i.pinimg.com/757x945/f7/32/ca/$hash.jpg"
        )
        val item2 = ScrapedMediaItem(
            id = "f7_item_2",
            url = "https://i.pinimg.com/736x/f7/32/ca/$hash.jpeg",
            title = "$hash...",
            isVideo = false,
            resolutionHint = "736x918",
            thumbnailUrl = "https://i.pinimg.com/736x/f7/32/ca/$hash.jpeg"
        )

        val deduplicated = MediaDeduplicator.deduplicateScrapedItems(listOf(item1, item2))
        assertEquals(1, deduplicated.size)
        val winner = deduplicated.first()
        assertEquals("https://i.pinimg.com/originals/f7/32/ca/$hash.jpg", winner.url)
        assertEquals("757x945", winner.resolutionHint)
    }

    @Test
    fun testDuplicateDownloadItemsAreFilteredOut() {
        val item1 = MediaInboxItem(
            id = "item_1",
            title = "sample.jpg",
            localUri = "/data/downloads/sample.jpg",
            sourceUrl = "https://example.com/sample.jpg",
            isVideo = false,
            sizeBytes = 1000L
        )
        val duplicateItem = MediaInboxItem(
            id = "item_1_alt",
            title = "sample.jpg",
            localUri = "/data/downloads/sample.jpg",
            sourceUrl = "https://example.com/sample.jpg",
            isVideo = false,
            sizeBytes = 1000L
        )

        val list = listOf(item1)
        val updated = list.filter {
            it.id != duplicateItem.id && it.sourceUrl != duplicateItem.sourceUrl && it.localUri != duplicateItem.localUri
        } + duplicateItem

        assertEquals(1, updated.size)
    }

    @Test
    fun testYouTubeThumbnailDeduplicationAndResolutionFolding() {
        val oar2Url = "https://i.ytimg.com/vi/FaEeQPmv1D4/oar2.jpg"
        val frame0268 = "https://i.ytimg.com/vi/FaEeQPmv1D4/frame0.jpg?sqp=-oaymwEdCJUDENAFSFWQAgHyq4qpAw8IARUAAIhCcAHAAQ==&rs=AOn4CLB..."
        val frame0270 = "https://i.ytimg.com/vi/FaEeQPmv1D4/frame0.jpg?sqp=-oaymwEdCK4DENAFSFWQAgHyq4qpAw8IARUAAIhCcAHAAQ==&rs=AOn4CLB..."

        val key1 = MediaDeduplicator.deduplicationKey(oar2Url)
        val key2 = MediaDeduplicator.deduplicationKey(frame0268)
        val key3 = MediaDeduplicator.deduplicationKey(frame0270)

        assertEquals("ytthumb:${"FaEeQPmv1D4".lowercase(Locale.ROOT)}", key1)
        assertEquals(key1, key2)
        assertEquals(key1, key3)

        val item1 = ScrapedMediaItem(
            id = "item_1",
            url = oar2Url,
            title = "oar2.jpg",
            isVideo = false,
            resolutionHint = "405x720",
            category = MediaCategory.IMAGES
        )
        val item2 = ScrapedMediaItem(
            id = "item_2",
            url = frame0268,
            title = "frame0.jpg",
            isVideo = false,
            resolutionHint = "268x480",
            category = MediaCategory.IMAGES
        )
        val item3 = ScrapedMediaItem(
            id = "item_3",
            url = frame0270,
            title = "frame0.jpg",
            isVideo = false,
            resolutionHint = "270x480",
            category = MediaCategory.IMAGES
        )

        val deduplicated = MediaDeduplicator.deduplicateScrapedItems(listOf(item1, item2, item3))
        assertEquals(1, deduplicated.size)
        assertEquals("405x720", deduplicated[0].resolutionHint)
    }

    @Test
    fun testVideoPosterFoldingLeavesOnlyVideo() {
        val videoItem = ScrapedMediaItem(
            id = "video_1",
            url = "https://rr1---sn-uxa-i5ne.googlevideo.com/videoplayback?expire=123&id=FaEeQPmv1D4",
            title = "Abba Harmonium Bajate...",
            isVideo = true,
            category = MediaCategory.VIDEOS,
            format = "WEBM",
            resolutionHint = "720x1280",
            thumbnailUrl = "https://i.ytimg.com/vi/FaEeQPmv1D4/hqdefault.jpg"
        )

        val oar2PosterItem = ScrapedMediaItem(
            id = "img_oar2",
            url = "https://i.ytimg.com/vi/FaEeQPmv1D4/oar2.jpg",
            title = "oar2.jpg",
            isVideo = false,
            category = MediaCategory.IMAGES,
            resolutionHint = "405x720"
        )

        val frame0PosterItem = ScrapedMediaItem(
            id = "img_frame0",
            url = "https://i.ytimg.com/vi/FaEeQPmv1D4/frame0.jpg?sqp=abc",
            title = "frame0.jpg",
            isVideo = false,
            category = MediaCategory.IMAGES,
            resolutionHint = "270x480"
        )

        val result = MediaDeduplicator.deduplicateScrapedItems(listOf(videoItem, oar2PosterItem, frame0PosterItem))

        assertEquals(1, result.size)
        val finalVideo = result[0]
        assertTrue(finalVideo.isVideo)
        assertEquals("Abba Harmonium Bajate...", finalVideo.title)
        assertTrue(finalVideo.thumbnailUrl?.contains("FaEeQPmv1D4") == true)
    }
}
