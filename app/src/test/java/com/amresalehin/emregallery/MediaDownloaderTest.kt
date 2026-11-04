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

    @Test
    fun isValidMediaRejectsWikiArticlesAndNonMediaPages() {
        // Wiki File and article pages should be rejected (they are HTML pages, not raw media files)
        assertEquals(false, com.amresalehin.emregallery.data.MediaClassifier.isValidMedia("https://en.wikipedia.org/wiki/File:Taj_Mahal.jpg"))
        assertEquals(false, com.amresalehin.emregallery.data.MediaClassifier.isValidMedia("https://en.wikipedia.org/wiki/Taj_Mahal"))
        assertEquals(false, com.amresalehin.emregallery.data.MediaClassifier.isValidMedia("https://commons.wikimedia.org/wiki/File:Mumtaz_Mahal.jpg"))
        assertEquals(false, com.amresalehin.emregallery.data.MediaClassifier.isValidMedia("https://example.com/w/index.php?title=File:Photo.png"))
        assertEquals(false, com.amresalehin.emregallery.data.MediaClassifier.isValidMedia("https://reddit.com/r/pics/comments/abc/pic.jpg"))

        // Genuine media endpoints on CDN/upload hosts should be accepted
        assertTrue(com.amresalehin.emregallery.data.MediaClassifier.isValidMedia("https://upload.wikimedia.org/wikipedia/commons/1/1d/Taj_Mahal_%28Edited%29.jpeg"))
        assertTrue(com.amresalehin.emregallery.data.MediaClassifier.isValidMedia("https://thumb.wikimedia.org/wikipedia/commons/thumb/1/1d/Taj_Mahal.jpeg/500px-Taj_Mahal.jpeg"))
        assertTrue(com.amresalehin.emregallery.data.MediaClassifier.isValidMedia("https://example.com/images/hero.jpg"))
    }

    @Test
    fun testWikimediaThumbnailResolvesToFullResolutionOriginal() {
        val thumbUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/Taj_Mahal_%28Edited%29.jpeg/500px-Taj_Mahal_%28Edited%29.jpeg"
        val resolved = com.amresalehin.emregallery.data.MediaClassifier.resolveOriginalMedia(thumbUrl)

        assertEquals("https://upload.wikimedia.org/wikipedia/commons/1/1d/Taj_Mahal_%28Edited%29.jpeg", resolved.mediaUrl)
        assertEquals(thumbUrl, resolved.thumbnailUrl)
        assertEquals("Taj_Mahal_(Edited).jpeg", resolved.cleanTitle)

        // Protocol-relative test
        val protoRelative = "//thumb.wikimedia.org/wikipedia/commons/thumb/b/ba/Taj_Mahal-10_%28cropped%29.jpg/330px-Taj_Mahal-10_%28cropped%29.jpg"
        val resolvedProto = com.amresalehin.emregallery.data.MediaClassifier.resolveOriginalMedia(protoRelative)
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/b/ba/Taj_Mahal-10_%28cropped%29.jpg", resolvedProto.mediaUrl)
        assertEquals("Taj_Mahal-10_(cropped).jpg", resolvedProto.cleanTitle)
    }

    @Test
    fun browserUrlFormattingAndNormalization() {
        fun formatUrl(input: String): String {
            val trimmed = input.trim()
            if (trimmed.isBlank()) return ""
            return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                if (trimmed.contains(".") && !trimmed.contains(" ")) {
                    "https://$trimmed"
                } else {
                    "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
                }
            } else {
                trimmed
            }
        }

        assertEquals("https://example.com/videos", formatUrl("example.com/videos"))
        assertEquals("https://duckduckgo.com/?q=funny+cats", formatUrl("funny cats"))
        assertEquals("https://test.org", formatUrl("https://test.org"))
    }

    @Test
    fun testBrowserSearchEnginesAndFormatQueryOrUrl() {
        val query = "vintage photography"
        val ddgUrl = com.amresalehin.emregallery.data.MediaClassifier.formatQueryOrUrl(
            query,
            com.amresalehin.emregallery.data.BrowserSearchEngine.DUCKDUCKGO
        )
        assertTrue(ddgUrl.contains("duckduckgo.com/?q=vintage+photography"))

        val googleUrl = com.amresalehin.emregallery.data.MediaClassifier.formatQueryOrUrl(
            query,
            com.amresalehin.emregallery.data.BrowserSearchEngine.GOOGLE
        )
        assertTrue(googleUrl.contains("google.com/search?q=vintage+photography"))

        val bingUrl = com.amresalehin.emregallery.data.MediaClassifier.formatQueryOrUrl(
            query,
            com.amresalehin.emregallery.data.BrowserSearchEngine.BING
        )
        assertTrue(bingUrl.contains("bing.com/search?q=vintage+photography"))

        val wikiUrl = com.amresalehin.emregallery.data.MediaClassifier.formatQueryOrUrl(
            query,
            com.amresalehin.emregallery.data.BrowserSearchEngine.WIKIPEDIA
        )
        assertTrue(wikiUrl.contains("wikipedia.org/wiki/Special:Search?search=vintage+photography"))

        // Standard URLs without http/https should add https://
        val siteUrl = com.amresalehin.emregallery.data.MediaClassifier.formatQueryOrUrl("commons.wikimedia.org")
        assertEquals("https://commons.wikimedia.org", siteUrl)

        // Empty string remains empty
        val emptyUrl = com.amresalehin.emregallery.data.MediaClassifier.formatQueryOrUrl("   ")
        assertEquals("", emptyUrl)
    }

    @Test
    fun testValidWebUrlValidation() {
        val validCases = listOf(
            "https://example.com/gallery",
            "http://sub.domain.org/path/to/image.jpg",
            "commons.wikimedia.org",
            "m.youtube.com/watch?v=123",
            "https://site.co.uk/media.mp4?id=50"
        )
        for (url in validCases) {
            assertTrue("Expected valid: $url", com.amresalehin.emregallery.data.MediaClassifier.isValidWebUrl(url))
        }

        val invalidCases = listOf(
            "not a valid url",
            "random pasted text from notes",
            "justwords",
            "   ",
            "javascript:void(0)",
            "file:///data/local/tmp",
            "invalid.123"
        )
        for (invalid in invalidCases) {
            assertEquals("Expected invalid: $invalid", false, com.amresalehin.emregallery.data.MediaClassifier.isValidWebUrl(invalid))
        }
    }

    @Test
    fun testGalleryDlEngineStatusAndSupportedSites() {
        val status = com.amresalehin.emregallery.data.GalleryDlEngine.getStatus()
        assertTrue(status.popularSites.isNotEmpty())
        assertTrue(status.popularSites.any { it.name == "Reddit" })
        assertTrue(status.popularSites.any { it.name == "Imgur" })
        assertTrue(status.popularSites.any { it.name == "Twitter / X" })
        assertTrue(status.popularSites.any { it.name == "Pinterest" })
        assertTrue(status.popularSites.any { it.name == "ArtStation" })
    }

    @Test
    fun testGalleryDlSupportInfoDetection() {
        val redditCheck = com.amresalehin.emregallery.data.GalleryDlEngine.checkSupport("https://www.reddit.com/r/earthporn/comments/123/mountains")
        assertTrue(redditCheck.isSupported)

        val imgurCheck = com.amresalehin.emregallery.data.GalleryDlEngine.checkSupport("https://imgur.com/a/abcde")
        assertTrue(imgurCheck.isSupported)

        val artstationCheck = com.amresalehin.emregallery.data.GalleryDlEngine.checkSupport("https://artstation.com/artwork/xyz123")
        assertTrue(artstationCheck.isSupported)

        val genericCheck = com.amresalehin.emregallery.data.GalleryDlEngine.checkSupport("https://nonexistent-random-domain-12345.com")
        assertEquals(false, genericCheck.isSupported)
    }

    @Test
    fun testExtractionSourceGalleryDlEnum() {
        assertEquals("gallery-dl", com.amresalehin.emregallery.data.ExtractionSource.GALLERY_DL.label)
        assertEquals(
            com.amresalehin.emregallery.data.DualMediaExtractor.ExtractionPhase.GALLERY_DL.name,
            "GALLERY_DL"
        )
    }

    @Test
    fun testJsoupHtmlParserExtractsMedia() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Sample Gallery Page</title>
                <meta property="og:image" content="https://example.com/og_hero.jpg" />
                <meta property="og:video" content="https://example.com/promo.mp4" />
            </head>
            <body>
                <img src="/images/pic1.jpg" data-src="/images/pic1_large.jpg" />
                <video poster="/images/poster.jpg">
                    <source src="/videos/clip.webm" type="video/webm" />
                </video>
                <div style="background-image: url('/images/bg.png');"></div>
                <a href="/downloads/wallpaper_4k.jpg">Download 4K</a>
            </body>
            </html>
        """.trimIndent()

        val doc = org.jsoup.Jsoup.parse(html, "https://example.com")
        assertEquals("Sample Gallery Page", doc.title())

        val ogImage = doc.selectFirst("meta[property=og:image]")?.attr("content")
        assertEquals("https://example.com/og_hero.jpg", ogImage)

        val images = doc.select("img")
        assertEquals(1, images.size)
        assertEquals("https://example.com/images/pic1.jpg", images[0].absUrl("src"))

        val videoSources = doc.select("video source")
        assertEquals(1, videoSources.size)
        assertEquals("https://example.com/videos/clip.webm", videoSources[0].absUrl("src"))

        val directLinks = doc.select("a[href]")
        assertEquals(1, directLinks.size)
        assertEquals("https://example.com/downloads/wallpaper_4k.jpg", directLinks[0].absUrl("href"))
    }

    @Test
    fun testOkHttpSharedSessionCookieJar() {
        val cookieJar = com.amresalehin.emregallery.data.OkHttpDownloader.SharedSessionCookieJar()
        val testUrl = "https://example.com/gallery"
        
        cookieJar.setManualCookie(testUrl, "session_token", "abc123xyz")
        cookieJar.setManualCookie(testUrl, "cf_clearance", "token987")

        val cookieString = cookieJar.getCookieString(testUrl)
        assertTrue(cookieString.contains("session_token=abc123xyz"))
        assertTrue(cookieString.contains("cf_clearance=token987"))

        val httpUrl = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("example.com")
            .encodedPath("/gallery")
            .build()
        val loadedCookies = cookieJar.loadForRequest(httpUrl)
        assertEquals(2, loadedCookies.size)
        assertTrue(loadedCookies.any { it.name == "session_token" && it.value == "abc123xyz" })
        assertTrue(loadedCookies.any { it.name == "cf_clearance" && it.value == "token987" })

        // Clear session
        cookieJar.clearSession()
        val afterClear = cookieJar.loadForRequest(httpUrl)
        assertEquals(0, afterClear.size)
    }

    @Test
    fun testOkHttpClientConfiguration() {
        val client = com.amresalehin.emregallery.data.OkHttpDownloader.client
        assertTrue(client.followRedirects)
        assertTrue(client.followSslRedirects)
        assertEquals(15000, client.connectTimeoutMillis)
        assertEquals(30000, client.readTimeoutMillis)
        assertEquals(30000, client.writeTimeoutMillis)
        assertEquals(com.amresalehin.emregallery.data.OkHttpDownloader.sharedCookieJar, client.cookieJar)
        assertTrue(client.interceptors.isNotEmpty())
    }

    @Test
    fun testWorkManagerDownloadWorkerConstants() {
        assertEquals("media_download", com.amresalehin.emregallery.data.MediaDownloadWorker.WORK_TAG_DOWNLOAD)
        assertEquals("media_url", com.amresalehin.emregallery.data.MediaDownloadWorker.KEY_MEDIA_URL)
        assertEquals("file_name", com.amresalehin.emregallery.data.MediaDownloadWorker.KEY_FILE_NAME)
        assertEquals("stack_id", com.amresalehin.emregallery.data.MediaDownloadWorker.KEY_STACK_ID)
        assertEquals("progress", com.amresalehin.emregallery.data.MediaDownloadWorker.KEY_PROGRESS)
    }

    @Test
    fun testMedia3StreamingMimeTypesDetection() {
        val hlsUrl = "https://example.com/live/stream.m3u8"
        val isHls = hlsUrl.contains(".m3u8", ignoreCase = true) || hlsUrl.contains("mpegurl", ignoreCase = true)
        assertTrue(isHls)

        val dashUrl = "https://example.com/vod/manifest.mpd"
        val isDash = dashUrl.contains(".mpd", ignoreCase = true) || dashUrl.contains("dash", ignoreCase = true)
        assertTrue(isDash)

        assertEquals("application/x-mpegURL", androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        assertEquals("application/dash+xml", androidx.media3.common.MimeTypes.APPLICATION_MPD)
    }
}

