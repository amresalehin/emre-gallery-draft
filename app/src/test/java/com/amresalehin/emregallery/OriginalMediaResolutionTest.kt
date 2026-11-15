package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.MediaDeduplicator
import com.amresalehin.emregallery.data.OriginalMediaResolver
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for resolving thumbnails and CDN resized URLs (Wikimedia, MediaWiki,
 * Reddit, Imgur, Twitter/X, WordPress Photon, and Pinterest) to full-resolution originals.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OriginalMediaResolutionTest {

    @Test
    fun testWikimediaThumbnailResolvesToFullResolutionOriginal() {
        val thumbUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/Taj_Mahal_%28Edited%29.jpeg/500px-Taj_Mahal_%28Edited%29.jpeg"
        val resolved = OriginalMediaResolver.resolve(thumbUrl)

        assertEquals("https://upload.wikimedia.org/wikipedia/commons/1/1d/Taj_Mahal_%28Edited%29.jpeg", resolved.mediaUrl)
        assertEquals(thumbUrl, resolved.thumbnailUrl)
        assertEquals("Taj_Mahal_(Edited).jpeg", resolved.cleanTitle)

        val protoRelative = "//thumb.wikimedia.org/wikipedia/commons/thumb/b/ba/Taj_Mahal-10_%28cropped%29.jpg/330px-Taj_Mahal-10_%28cropped%29.jpg"
        val resolvedProto = OriginalMediaResolver.resolve(protoRelative)
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/b/ba/Taj_Mahal-10_%28cropped%29.jpg", resolvedProto.mediaUrl)
        assertEquals("Taj_Mahal-10_(cropped).jpg", resolvedProto.cleanTitle)
    }

    @Test
    fun testMediaWikiThumbResolvesWithCustomUploadPath() {
        val customWikiUrl = "https://mywiki.example.org/custom_folder/thumb/9/91/Diagram.png/400px-Diagram.png"
        val resolved = OriginalMediaResolver.resolve(customWikiUrl)
        assertEquals("https://mywiki.example.org/custom_folder/9/91/Diagram.png", resolved.mediaUrl)
        assertEquals(customWikiUrl, resolved.thumbnailUrl)
        assertEquals("Diagram.png", resolved.cleanTitle)

        val wikimediaUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/12/Taj_Mahal.jpg/500px-Taj_Mahal.jpg"
        val resolvedWm = OriginalMediaResolver.resolve(wikimediaUrl)
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/1/12/Taj_Mahal.jpg", resolvedWm.mediaUrl)
        assertEquals(wikimediaUrl, resolvedWm.thumbnailUrl)
        assertEquals("Taj_Mahal.jpg", resolvedWm.cleanTitle)
    }

    @Test
    fun testMultiHostCdnNormalizationAndDeduplication() {
        val redditPreview = "https://preview.redd.it/abcdef123.jpg?width=640&crop=smart&auto=webp&s=xyz789"
        val redditResolved = OriginalMediaResolver.resolve(redditPreview)
        assertEquals("https://i.redd.it/abcdef123.jpg", redditResolved.mediaUrl)
        assertEquals(redditPreview, redditResolved.thumbnailUrl)

        val redditKey1 = MediaDeduplicator.deduplicationKey(redditPreview)
        val redditKey2 = MediaDeduplicator.deduplicationKey("https://i.redd.it/abcdef123.jpg")
        assertEquals(redditKey1, redditKey2)

        val imgurThumb = "https://i.imgur.com/vAbCdEm.jpg"
        val imgurResolved = OriginalMediaResolver.resolve(imgurThumb)
        assertEquals("https://i.imgur.com/vAbCdE.jpg", imgurResolved.mediaUrl)

        val imgurKey1 = MediaDeduplicator.deduplicationKey(imgurThumb)
        val imgurKey2 = MediaDeduplicator.deduplicationKey("https://i.imgur.com/vAbCdE.jpg")
        assertEquals(imgurKey1, imgurKey2)

        val twitterThumb = "https://pbs.twimg.com/media/F123456?format=jpg&name=small"
        val twitterResolved = OriginalMediaResolver.resolve(twitterThumb)
        assertEquals("https://pbs.twimg.com/media/F123456?format=jpg&name=orig", twitterResolved.mediaUrl)

        val twitterKey1 = MediaDeduplicator.deduplicationKey(twitterThumb)
        val twitterKey2 = MediaDeduplicator.deduplicationKey("https://pbs.twimg.com/media/F123456?format=jpg&name=orig")
        assertEquals(twitterKey1, twitterKey2)

        val wpThumb = "https://i0.wp.com/example.com/wp-content/uploads/2026/04/photo.jpg?resize=300%2C200"
        val wpResolved = OriginalMediaResolver.resolve(wpThumb)
        assertEquals("https://example.com/wp-content/uploads/2026/04/photo.jpg", wpResolved.mediaUrl)

        val wpKey1 = MediaDeduplicator.deduplicationKey(wpThumb)
        val wpKey2 = MediaDeduplicator.deduplicationKey("https://example.com/wp-content/uploads/2026/04/photo.jpg")
        assertEquals(wpKey1, wpKey2)
    }

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
}
