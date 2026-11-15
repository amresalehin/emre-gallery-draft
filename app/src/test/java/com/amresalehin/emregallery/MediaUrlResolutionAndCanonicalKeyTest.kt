package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.MediaDeduplicator
import com.amresalehin.emregallery.data.SrcsetParser
import com.amresalehin.emregallery.data.WebUrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for relative/protocol-relative URL resolution, srcset parsing,
 * streaming slice parameter stripping, and tracking-parameter canonicalization.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaUrlResolutionAndCanonicalKeyTest {

    @Test
    fun mediaClassifierCanonicalDeduplicationStripsTrackingParams() {
        val urlWithTracking = "https://example.com/image.jpg?utm_source=twitter&utm_medium=social&id=42&fbclid=xyz"
        val canonical = MediaDeduplicator.canonicalKey(urlWithTracking)
        assertTrue(canonical.contains("id=42"))
        assertFalse(canonical.contains("utm_source"))
        assertFalse(canonical.contains("fbclid"))

        val urlDifferentTracking = "https://example.com/image.jpg?fbclid=different&id=42&utm_campaign=winter"
        val canonical2 = MediaDeduplicator.canonicalKey(urlDifferentTracking)
        assertEquals(canonical, canonical2)
    }

    @Test
    fun mediaClassifierUrlResolution() {
        val base = "https://example.com/articles/index.html"
        val relative = WebUrlUtils.resolveUrl("/images/hero.png", base)
        assertEquals("https://example.com/images/hero.png", relative)

        val protocolRelative = WebUrlUtils.resolveUrl("//cdn.example.com/video.mp4", base)
        assertEquals("https://cdn.example.com/video.mp4", protocolRelative)
    }

    @Test
    fun mediaClassifierSrcsetParsing() {
        val srcset = "thumb.jpg 320w, medium.jpg 640w, large.jpg 1280w"
        val parsed = SrcsetParser.parse(srcset, "https://example.com/")
        assertEquals(3, parsed.size)
        assertEquals("https://example.com/thumb.jpg", parsed[0].first)
        assertEquals("320w", parsed[0].second)
        assertEquals("https://example.com/large.jpg", parsed[2].first)
        assertEquals("1280w", parsed[2].second)
    }

    @Test
    fun testVideoStreamingSliceCleaningAndCanonicalDeduplication() {
        val slice1 = "https://rr1---sn.googlevideo.com/videoplayback?expire=123&id=abc&itag=22&range=0-1048575&rn=1&sq=1"
        val slice2 = "https://rr1---sn.googlevideo.com/videoplayback?expire=123&id=abc&itag=22&range=1048576-2097151&rn=2&sq=2"

        val key1 = MediaDeduplicator.canonicalKey(slice1)
        val key2 = MediaDeduplicator.canonicalKey(slice2)
        assertEquals(key1, key2)

        val cleaned = WebUrlUtils.cleanMediaUrl(slice1)
        assertFalse(cleaned.contains("range="))
        assertFalse(cleaned.contains("rn="))
        assertFalse(cleaned.contains("sq="))
        assertTrue(cleaned.contains("id=abc"))
        assertTrue(cleaned.contains("itag=22"))
    }
}
