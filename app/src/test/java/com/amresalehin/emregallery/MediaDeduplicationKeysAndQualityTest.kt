package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.MediaDeduplicator
import com.amresalehin.emregallery.data.MediaQualityEvaluator
import com.amresalehin.emregallery.data.OriginalMediaResolver
import com.amresalehin.emregallery.data.ScrapedMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for CDN/resize deduplication keys and media quality ranking.
 */
class MediaDeduplicationKeysAndQualityTest {

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

        val expectedKey = "pinimg:${hash.lowercase()}"
        assertEquals(expectedKey, MediaDeduplicator.deduplicationKey(url757))
        assertEquals(expectedKey, MediaDeduplicator.deduplicationKey(url736))
        assertEquals(expectedKey, MediaDeduplicator.deduplicationKey(url474))
        assertEquals(expectedKey, MediaDeduplicator.deduplicationKey(url236))
        assertEquals(expectedKey, MediaDeduplicator.deduplicationKey(urlOrig))

        val res736 = OriginalMediaResolver.resolve(url736)
        assertEquals("https://i.pinimg.com/originals/$p1/$p2/$p3/$hash.jpg", res736.mediaUrl)
        assertEquals(url736, res736.thumbnailUrl)
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

        assertTrue(MediaQualityEvaluator.compareQuality(highRes, lowRes) > 0)
        assertTrue(MediaQualityEvaluator.compareQuality(lowRes, highRes) < 0)
    }

    @Test
    fun testCloudflareAndCdnTransformsDeduplication() {
        val cdnOriginal = "https://images.example.com/products/watch.png"
        val cdnOptimized = "https://images.example.com/cdn-cgi/image/width=800,quality=75,format=auto/products/watch.png"
        val cdnThumb = "https://images.example.com/cdn-cgi/image/w_200,h_200,f_auto/products/watch.png"

        val keyOrig = MediaDeduplicator.deduplicationKey(cdnOriginal)
        val keyOpt = MediaDeduplicator.deduplicationKey(cdnOptimized)
        val keyThumb = MediaDeduplicator.deduplicationKey(cdnThumb)

        assertEquals(keyOrig, keyOpt)
        assertEquals(keyOrig, keyThumb)
    }
}
