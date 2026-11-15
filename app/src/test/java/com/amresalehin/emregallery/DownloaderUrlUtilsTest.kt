package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.WebUrlUtils
import com.amresalehin.emregallery.ui.DetectedPlatform
import com.amresalehin.emregallery.ui.detectPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for URL validation, extraction, sanitization, domain parsing, and platform detection.
 */
class DownloaderUrlUtilsTest {

    @Test
    fun testPlatformDetectionFromVariousUrls() {
        // YouTube detection
        assertEquals(DetectedPlatform.YOUTUBE, detectPlatform("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(DetectedPlatform.YOUTUBE, detectPlatform("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(DetectedPlatform.YOUTUBE, detectPlatform("https://m.youtube.com/shorts/12345"))

        // Instagram detection
        assertEquals(DetectedPlatform.INSTAGRAM, detectPlatform("https://instagram.com/reel/C123456/"))
        assertEquals(DetectedPlatform.INSTAGRAM, detectPlatform("https://www.instagram.com/p/B987654/"))

        // TikTok detection
        assertEquals(DetectedPlatform.TIKTOK, detectPlatform("https://www.tiktok.com/@creator/video/987654321"))
        assertEquals(DetectedPlatform.TIKTOK, detectPlatform("https://vm.tiktok.com/ZM8abcde/"))

        // Twitter / X detection
        assertEquals(DetectedPlatform.TWITTER, detectPlatform("https://x.com/user/status/1234567890"))
        assertEquals(DetectedPlatform.TWITTER, detectPlatform("https://twitter.com/user/status/9876543210"))

        // Reddit detection
        assertEquals(DetectedPlatform.REDDIT, detectPlatform("https://www.reddit.com/r/Android/comments/abc123/new_feature/"))
        assertEquals(DetectedPlatform.REDDIT, detectPlatform("https://redd.it/xyz987"))

        // Pinterest detection
        assertEquals(DetectedPlatform.PINTEREST, detectPlatform("https://pinterest.com/pin/123456789/"))
        assertEquals(DetectedPlatform.PINTEREST, detectPlatform("https://pin.it/abc1234"))

        // Facebook detection
        assertEquals(DetectedPlatform.FACEBOOK, detectPlatform("https://www.facebook.com/watch/?v=123456"))
        assertEquals(DetectedPlatform.FACEBOOK, detectPlatform("https://fb.watch/xyz123/"))

        // Direct media links
        assertEquals(DetectedPlatform.DIRECT_MEDIA, detectPlatform("https://cdn.example.com/videos/stream.m3u8"))
        assertEquals(DetectedPlatform.DIRECT_MEDIA, detectPlatform("https://cdn.example.com/videos/manifest.mpd"))
        assertEquals(DetectedPlatform.DIRECT_MEDIA, detectPlatform("https://cdn.example.com/media/clip.mp4"))
        assertEquals(DetectedPlatform.DIRECT_MEDIA, detectPlatform("https://cdn.example.com/images/hero.jpg"))
        assertEquals(DetectedPlatform.DIRECT_MEDIA, detectPlatform("https://cdn.example.com/images/banner.webp"))
        assertEquals(DetectedPlatform.DIRECT_MEDIA, detectPlatform("https://cdn.example.com/audio/podcast.mp3"))

        // Generic websites
        assertEquals(DetectedPlatform.GENERIC_WEB, detectPlatform("https://en.wikipedia.org/wiki/Kotlin"))
        assertEquals(DetectedPlatform.GENERIC_WEB, detectPlatform("https://github.com/trending"))
    }

    @Test
    fun testUrlValidationForSearchAndFetch() {
        assertTrue(WebUrlUtils.isValidWebUrl("https://youtube.com/watch?v=123"))
        assertTrue(WebUrlUtils.isValidWebUrl("http://example.com/gallery"))
        assertTrue(WebUrlUtils.isValidWebUrl("instagram.com/p/abc"))
        assertTrue(WebUrlUtils.isValidWebUrl("www.reddit.com/r/pics"))

        assertFalse(WebUrlUtils.isValidWebUrl("just random words"))
        assertFalse(WebUrlUtils.isValidWebUrl("   "))
        assertFalse(WebUrlUtils.isValidWebUrl(""))
        assertFalse(WebUrlUtils.isValidWebUrl("javascript:alert(1)"))
    }

    @Test
    fun testExtractFirstUrlFromSharedContent() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            WebUrlUtils.extractFirstUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        )

        assertEquals(
            "https://twitter.com/user/status/1234567890",
            WebUrlUtils.extractFirstUrl("Check out this tweet: https://twitter.com/user/status/1234567890 right now!")
        )

        assertEquals(
            "https://instagram.com/reel/C123456/",
            WebUrlUtils.extractFirstUrl("Look at this reel https://instagram.com/reel/C123456/.")
        )

        val multiLineShare = """
            Awesome tutorial on Jetpack Compose!
            https://medium.com/@dev/compose-guide-2026
            Shared from Chrome
        """.trimIndent()
        assertEquals(
            "https://medium.com/@dev/compose-guide-2026",
            WebUrlUtils.extractFirstUrl(multiLineShare)
        )

        assertEquals(
            "https://wikipedia.org/wiki/Taj_Mahal",
            WebUrlUtils.extractFirstUrl("wikipedia.org/wiki/Taj_Mahal")
        )

        assertEquals(null, WebUrlUtils.extractFirstUrl("Hello there how are you"))
        assertEquals(null, WebUrlUtils.extractFirstUrl(""))
        assertEquals(null, WebUrlUtils.extractFirstUrl(null))
    }

    @Test
    fun testSanitizeUrl() {
        val youtubeWithTracking = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=abcdef123&feature=shared&utm_source=share"
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            WebUrlUtils.sanitizeUrl(youtubeWithTracking)
        )

        val instagramWithTracking = "https://www.instagram.com/reel/C123456/?igshid=MzRlODBiNWFlZA==&utm_source=ig_web_copy_link"
        assertEquals(
            "https://www.instagram.com/reel/C123456/",
            WebUrlUtils.sanitizeUrl(instagramWithTracking)
        )

        val articleWithFbclid = "https://example.com/article?id=42&fbclid=IwAR123456"
        assertEquals(
            "https://example.com/article?id=42",
            WebUrlUtils.sanitizeUrl(articleWithFbclid)
        )

        val googleRedirect = "https://www.google.com/url?q=https://example.com/target-page&sa=D&sntz=1"
        assertEquals(
            "https://example.com/target-page",
            WebUrlUtils.sanitizeUrl(googleRedirect)
        )

        val quotedUrl = "   \"https://x.com/user/status/123456?s=20&utm_source=share\"   "
        assertEquals(
            "https://x.com/user/status/123456?s=20",
            WebUrlUtils.sanitizeUrl(quotedUrl)
        )

        assertEquals(
            "instagram.com",
            WebUrlUtils.cleanDomainFromUrl("https://www.instagram.com/p/123")
        )
        assertEquals(
            "youtu.be",
            WebUrlUtils.cleanDomainFromUrl("https://youtu.be/dQw4w9WgXcQ")
        )
    }
}
