package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.MediaClassifier
import com.amresalehin.emregallery.data.MediaProvenance
import com.amresalehin.emregallery.data.ScrapedMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDeduplicationPureUnitTest {

    @Test
    fun testPinterestResolutionAndFormatVariantDeduplication() {
        val hash = "f732ca2684db4685ff263b65cb466ff4"
        val p1 = "f7"
        val p2 = "32"
        val p3 = "ca"

        val url757 = "https://i.pinimg.com/757x945/$p1/$p2/$p3/$hash.jpg"
        val url736 = "https://i.pinimg.com/736x/$p1/$p2/$p3/$hash.jpeg"
        val url474 = "https://i.pinimg.com/474x/$p1/$p2/$p3/$hash.webp"
        val url236 = "https://i.pinimg.com/236x/$p1/$p2/$p3/$hash.jpg"
        val urlOrig = "https://i.pinimg.com/originals/$p1/$p2/$p3/$hash.jpg"

        val key757 = MediaClassifier.deduplicationKey(url757)
        val key736 = MediaClassifier.deduplicationKey(url736)
        val key474 = MediaClassifier.deduplicationKey(url474)
        val key236 = MediaClassifier.deduplicationKey(url236)
        val keyOrig = MediaClassifier.deduplicationKey(urlOrig)

        val expectedKey = "pinimg:${hash.lowercase()}"
        assertEquals(expectedKey, key757)
        assertEquals(expectedKey, key736)
        assertEquals(expectedKey, key474)
        assertEquals(expectedKey, key236)
        assertEquals(expectedKey, keyOrig)

        // All should resolve to originals
        val res736 = MediaClassifier.resolveOriginalMedia(url736)
        assertEquals("https://i.pinimg.com/originals/$p1/$p2/$p3/$hash.jpg", res736.mediaUrl)
        assertEquals(url736, res736.thumbnailUrl)
    }

    @Test
    fun testResponsiveRasterFormatVariantsDeduplication() {
        // WordPress / Shopify / CDN resize suffixes and format variants
        val base = "https://example.com/wp-content/uploads/2024/05/hero"
        val wpJpg = "$base-736x918.jpg"
        val wpWebp = "$base-757x945.webp"
        val wpOrig = "$base.jpeg"

        val key1 = MediaClassifier.deduplicationKey(wpJpg)
        val key2 = MediaClassifier.deduplicationKey(wpWebp)
        val key3 = MediaClassifier.deduplicationKey(wpOrig)

        assertEquals(key3, key1)
        assertEquals(key3, key2)

        // CDN folder dimensions
        val cdnThumb = "https://images.example.com/236x/products/shoe.webp?w=236&format=webp"
        val cdnLarge = "https://images.example.com/736x/products/shoe.jpg?w=736&format=jpg"
        val cdnKey1 = MediaClassifier.deduplicationKey(cdnThumb)
        val cdnKey2 = MediaClassifier.deduplicationKey(cdnLarge)
        assertEquals(cdnKey2, cdnKey1)
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

        val deduplicated = MediaClassifier.deduplicateScrapedItems(listOf(item1, item2, item3))
        assertEquals(1, deduplicated.size)

        val winner = deduplicated[0]
        // Master original URL should be resolved
        assertEquals("https://i.pinimg.com/originals/2d/4c/7a/$hash.jpg", winner.url)
        // Highest resolution hint should be preserved
        assertEquals("736x1227", winner.resolutionHint)
        // Provenance should be merged
        assertTrue(winner.provenance.extractorEngine.contains("DOM Scrape"))
        assertTrue(winner.provenance.extractorEngine.contains("Network Intercept"))
        // Thumbnail should be present
        assertNotNull(winner.thumbnailUrl)
    }

    @Test
    fun testUserScreenshotExactDuplicateItemsAreCollapsed() {
        // From user screenshot:
        // Item 1: f732ca2684db4685ff26... JPG 757x945
        // Item 2: f732ca2684db4685ff26... JPEG 736x918
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

        val deduplicated = MediaClassifier.deduplicateScrapedItems(listOf(item1, item2))
        assertEquals(1, deduplicated.size)
        val winner = deduplicated.first()
        assertEquals("https://i.pinimg.com/originals/f7/32/ca/$hash.jpg", winner.url)
        assertEquals("757x945", winner.resolutionHint)
    }

    @Test
    fun testCompareMediaQualityPrefersOriginalsAndHigherResolution() {
        val lowRes = ScrapedMediaItem(
            id = "low",
            url = "https://example.com/images/hero-236x150.webp",
            title = "Hero",
            resolutionHint = "236x150",
            sizeBytes = 15000L
        )
        val highRes = ScrapedMediaItem(
            id = "high",
            url = "https://example.com/images/hero-1920x1080.jpg",
            title = "Hero",
            resolutionHint = "1920x1080",
            sizeBytes = 350000L
        )

        // highRes quality should be strictly greater than lowRes
        assertTrue(MediaClassifier.compareMediaQuality(highRes, lowRes) > 0)
        assertTrue(MediaClassifier.compareMediaQuality(lowRes, highRes) < 0)
    }

    @Test
    fun testCloudflareAndCdnTransformsDeduplication() {
        val cdnOriginal = "https://images.example.com/products/watch.png"
        val cdnOptimized = "https://images.example.com/cdn-cgi/image/width=800,quality=75,format=auto/products/watch.png"
        val cdnThumb = "https://images.example.com/cdn-cgi/image/w_200,h_200,f_auto/products/watch.png"

        val keyOrig = MediaClassifier.deduplicationKey(cdnOriginal)
        val keyOpt = MediaClassifier.deduplicationKey(cdnOptimized)
        val keyThumb = MediaClassifier.deduplicationKey(cdnThumb)

        assertEquals(keyOrig, keyOpt)
        assertEquals(keyOrig, keyThumb)
    }
}
