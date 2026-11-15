package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.MediaDeduplicator
import com.amresalehin.emregallery.data.MediaProvenance
import com.amresalehin.emregallery.data.OriginalMediaResolver
import com.amresalehin.emregallery.data.ScrapedMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for cross-scraper item merging and collapsing resolution/format/transcode variants
 * during [MediaDeduplicator.deduplicateScrapedItems].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaResolutionAndDeduplicationTest {

    @Test
    fun testWikimediaTranscodedVideoResolvesToOriginalMasterFileAndDeduplicates() {
        val masterUrl = "https://upload.wikimedia.org/wikipedia/commons/1/1a/Taj_Mahal_-_Mausoleum_der_Liebe_und_Weltwunder_aus_Marmor.webm"
        val transcode480p = "https://upload.wikimedia.org/wikipedia/commons/transcoded/1/1a/Taj_Mahal_-_Mausoleum_der_Liebe_und_Weltwunder_aus_Marmor.webm/Taj_Mahal_-_Mausoleum_der_Liebe_und_Weltwunder_aus_Marmor.webm.480p.vp9.webm"
        val transcode240p = "https://upload.wikimedia.org/wikipedia/commons/transcoded/1/1a/Taj_Mahal_-_Mausoleum_der_Liebe_und_Weltwunder_aus_Marmor.webm/Taj_Mahal_-_Mausoleum_der_Liebe_und_Weltwunder_aus_Marmor.webm.240p.vp9.webm"

        val resolved480 = OriginalMediaResolver.resolve(transcode480p)
        assertEquals(masterUrl, resolved480.mediaUrl)
        assertEquals(transcode480p, resolved480.thumbnailUrl)
        assertEquals("Taj_Mahal_-_Mausoleum_der_Liebe_und_Weltwunder_aus_Marmor.webm", resolved480.cleanTitle)

        val keyMaster = MediaDeduplicator.deduplicationKey(masterUrl)
        val key480 = MediaDeduplicator.deduplicationKey(transcode480p)
        val key240 = MediaDeduplicator.deduplicationKey(transcode240p)
        assertEquals(keyMaster, key480)
        assertEquals(keyMaster, key240)

        val domItem = ScrapedMediaItem(
            id = "dom_1",
            url = transcode240p,
            title = "Taj_Mahal_-_Mausoleum_der_Liebe",
            isVideo = true,
            resolutionHint = "250x141",
            thumbnailUrl = transcode240p,
            provenance = MediaProvenance(extractorEngine = "Static HTML (DOM Scrape)")
        )
        val netItem = ScrapedMediaItem(
            id = "net_1",
            url = transcode480p,
            title = "Taj_Mahal_-_Mausoleum_der_Liebe",
            isVideo = true,
            resolutionHint = "Network",
            thumbnailUrl = transcode480p,
            provenance = MediaProvenance(extractorEngine = "Browser Chromium (Network Intercept)")
        )

        val deduplicated = MediaDeduplicator.deduplicateScrapedItems(listOf(domItem, netItem))
        assertEquals(1, deduplicated.size)
        val singleItem = deduplicated[0]
        assertEquals(masterUrl, singleItem.url)
        assertEquals("250x141", singleItem.resolutionHint)
        assertTrue(singleItem.provenance.extractorEngine.contains("DOM Scrape"))
        assertTrue(singleItem.provenance.extractorEngine.contains("Network Intercept"))
    }

    @Test
    fun deduplicateScrapedItemsAcrossMultipleScrapers() {
        val itemFromStaticHtml = ScrapedMediaItem(
            id = "static_1",
            url = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/10/Taj_Mahal-10_%28cropped%29.jpg/500px-Taj_Mahal-10_%28cropped%29.jpg",
            title = "Taj Mahal Thumbnail",
            isVideo = false,
            thumbnailUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/10/Taj_Mahal-10_%28cropped%29.jpg/500px-Taj_Mahal-10_%28cropped%29.jpg",
            provenance = MediaProvenance(
                extractorEngine = "Static HTML",
                pageUrl = "https://en.wikipedia.org/wiki/Taj_Mahal"
            ),
            downloadHeaders = mapOf("Referer" to "https://en.wikipedia.org/wiki/Taj_Mahal")
        )

        val itemFromBrowser = ScrapedMediaItem(
            id = "browser_1",
            url = "https://upload.wikimedia.org/wikipedia/commons/1/10/Taj_Mahal-10_(cropped).jpg",
            title = "Taj Mahal Original",
            isVideo = false,
            resolutionHint = "2048x1536",
            provenance = MediaProvenance(
                extractorEngine = "Browser",
                pageUrl = "https://en.wikipedia.org/wiki/Taj_Mahal"
            ),
            downloadHeaders = mapOf("User-Agent" to "Mozilla/5.0")
        )

        val distinctItem = ScrapedMediaItem(
            id = "static_2",
            url = "https://upload.wikimedia.org/wikipedia/commons/2/22/Red_Fort.jpg",
            title = "Red Fort",
            isVideo = false,
            provenance = MediaProvenance(
                extractorEngine = "Static HTML",
                pageUrl = "https://en.wikipedia.org/wiki/Taj_Mahal"
            )
        )

        val deduplicated = MediaDeduplicator.deduplicateScrapedItems(listOf(itemFromStaticHtml, itemFromBrowser, distinctItem))
        assertEquals(2, deduplicated.size)

        val mergedTaj = deduplicated.first { it.url.contains("Taj_Mahal-10") }
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/1/10/Taj_Mahal-10_(cropped).jpg", mergedTaj.url)
        assertNotNull(mergedTaj.thumbnailUrl)
        assertTrue(mergedTaj.provenance.extractorEngine.contains("Static HTML"))
        assertTrue(mergedTaj.provenance.extractorEngine.contains("Browser"))
        assertTrue(mergedTaj.downloadHeaders.containsKey("Referer"))
        assertTrue(mergedTaj.downloadHeaders.containsKey("User-Agent"))
    }

    @Test
    fun testResponsiveRasterFormatVariantsDeduplication() {
        val base = "https://example.com/wp-content/uploads/2024/05/hero"
        val wpJpg = "$base-736x918.jpg"
        val wpWebp = "$base-757x945.webp"
        val wpOrig = "$base.jpeg"

        val key1 = MediaDeduplicator.deduplicationKey(wpJpg)
        val key2 = MediaDeduplicator.deduplicationKey(wpWebp)
        val key3 = MediaDeduplicator.deduplicationKey(wpOrig)

        assertEquals(key3, key1)
        assertEquals(key3, key2)

        val cdnThumb = "https://images.example.com/236x/products/shoe.webp?w=236&format=webp"
        val cdnLarge = "https://images.example.com/736x/products/shoe.jpg?w=736&format=jpg"
        assertEquals(
            MediaDeduplicator.deduplicationKey(cdnLarge),
            MediaDeduplicator.deduplicationKey(cdnThumb)
        )
    }

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
}
