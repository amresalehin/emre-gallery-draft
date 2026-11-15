package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.AdBlockEngine
import com.amresalehin.emregallery.data.ContentAwareMediaFilter
import com.amresalehin.emregallery.data.MediaCategory
import com.amresalehin.emregallery.data.MediaClassifier
import com.amresalehin.emregallery.data.MediaUrlValidator
import com.amresalehin.emregallery.data.ScrapedMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaClassificationAndFilteringTest {

    @Test
    fun mediaClassifierDetectsStreamsAndFormats() {
        val hls = MediaClassifier.classify("https://example.com/live/playlist.m3u8")
        assertEquals(MediaCategory.STREAMS, hls.category)
        assertTrue(hls.isStream)
        assertTrue(hls.isVideo)
        assertEquals("HLS (m3u8)", hls.format)

        val dash = MediaClassifier.classify("https://example.com/video/manifest.mpd")
        assertEquals(MediaCategory.STREAMS, dash.category)
        assertTrue(dash.isStream)
        assertEquals("DASH (mpd)", dash.format)

        val mp4 = MediaClassifier.classify("https://example.com/clip.mp4")
        assertEquals(MediaCategory.VIDEOS, mp4.category)
        assertEquals("MP4", mp4.format)

        val mp3 = MediaClassifier.classify("https://example.com/audio.mp3")
        assertEquals(MediaCategory.AUDIO, mp3.category)
        assertTrue(mp3.isAudio)

        val webp = MediaClassifier.classify("https://example.com/photo.webp")
        assertEquals(MediaCategory.IMAGES, webp.category)
        assertEquals("WEBP", webp.format)
    }

    @Test
    fun isValidMediaRejectsWikiArticlesAndNonMediaPages() {
        assertFalse(MediaUrlValidator.isValidMedia("https://en.wikipedia.org/wiki/File:Taj_Mahal.jpg"))
        assertFalse(MediaUrlValidator.isValidMedia("https://en.wikipedia.org/wiki/Taj_Mahal"))
        assertFalse(MediaUrlValidator.isValidMedia("https://commons.wikimedia.org/wiki/File:Mumtaz_Mahal.jpg"))
        assertFalse(MediaUrlValidator.isValidMedia("https://example.com/w/index.php?title=File:Photo.png"))
        assertFalse(MediaUrlValidator.isValidMedia("https://reddit.com/r/pics/comments/abc/pic.jpg"))

        assertTrue(MediaUrlValidator.isValidMedia("https://upload.wikimedia.org/wikipedia/commons/1/1d/Taj_Mahal_%28Edited%29.jpeg"))
        assertTrue(MediaUrlValidator.isValidMedia("https://thumb.wikimedia.org/wikipedia/commons/thumb/1/1d/Taj_Mahal.jpeg/500px-Taj_Mahal.jpeg"))
        assertTrue(MediaUrlValidator.isValidMedia("https://example.com/images/hero.jpg"))
    }

    @Test
    fun testWebsiteAndRouteAwareMediaExclusions() {
        assertTrue(MediaUrlValidator.isValidMedia("https://mycdn.example.com/gallery/photo.jpg"))
        assertTrue(MediaUrlValidator.isValidMedia("https://mycdn.example.com/pin/12345.png"))
        assertTrue(MediaUrlValidator.isValidMedia("https://mycdn.example.com/comments/image.jpg"))
        assertTrue(MediaUrlValidator.isValidMedia("https://mycdn.example.com/wiki/hero.png"))

        assertTrue(MediaUrlValidator.isValidMedia("https://i.redd.it/comments/abc.jpg"))
        assertTrue(MediaUrlValidator.isValidMedia("https://i.pinimg.com/736x/ab/cd/ef.jpg"))
        assertTrue(MediaUrlValidator.isValidMedia("https://i.imgur.com/gallery/abcde.jpg"))

        assertFalse(MediaUrlValidator.isValidMedia("https://reddit.com/r/pics/comments/abc/pic.jpg"))
        assertFalse(MediaUrlValidator.isValidMedia("https://pinterest.com/pin/12345/"))
        assertFalse(MediaUrlValidator.isValidMedia("https://imgur.com/gallery/abcde"))
        assertFalse(MediaUrlValidator.isValidMedia("https://twitter.com/user/status/12345"))
    }

    @Test
    fun testHostAndPathAwareExclusionRules() {
        assertTrue(MediaUrlValidator.isValidMedia("https://mycdn.example.com/gallery/photo.jpg"))
        assertTrue(MediaUrlValidator.isValidMedia("https://cdn.example.org/comments/attachment.png"))
        assertTrue(MediaUrlValidator.isValidMedia("https://assets.example.net/pin/banner.webp"))

        assertFalse(MediaUrlValidator.isValidMedia("https://imgur.com/gallery/a8f2k"))
        assertFalse(MediaUrlValidator.isValidMedia("https://reddit.com/r/Android/comments/xyz123/discussion"))
        assertFalse(MediaUrlValidator.isValidMedia("https://pinterest.com/pin/987654321/"))
        assertFalse(MediaUrlValidator.isValidMedia("https://en.wikipedia.org/wiki/File:Taj_Mahal.jpg"))
    }

    @Test
    fun testVideoFragmentsAndStreamingChunksRejected() {
        val fragments = listOf(
            "https://cdn.example.com/stream/segment-001.ts",
            "https://cdn.example.com/hls/seg_12.ts",
            "https://cdn.example.com/dash/chunk-stream0-00001.m4s",
            "https://cdn.example.com/dash/init-stream0.m4s",
            "https://cdn.example.com/video/init.mp4",
            "https://cdn.example.com/video/frag-1.mp4",
            "https://cdn.example.com/stream/track-1-frag-5.m4v",
            "https://cdn.example.com/stream.mp4?sq=3&rn=2",
            "blob:https://example.com/39a2f-1234"
        )

        for (frag in fragments) {
            assertTrue("Fragment $frag should be detected as media fragment", MediaUrlValidator.isMediaFragment(frag))
            assertFalse("Fragment $frag should not be accepted as valid media", MediaUrlValidator.isValidMedia(frag))
        }

        val validVideos = listOf(
            "https://cdn.example.com/videos/full_trailer.mp4",
            "https://cdn.example.com/hls/master.m3u8",
            "https://cdn.example.com/dash/manifest.mpd",
            "https://cdn.example.com/assets/animation.webm"
        )

        for (vid in validVideos) {
            assertFalse("Valid video $vid should not be a fragment", MediaUrlValidator.isMediaFragment(vid))
            assertTrue("Valid video $vid should be accepted as valid media", MediaUrlValidator.isValidMedia(vid))
        }
    }

    @Test
    fun testEnhancedFragmentAndSegmentFiltering() {
        val fragmentCases = listOf(
            "https://cdn.example.com/stream/segment-001.ts",
            "https://cdn.example.com/hls/seg_12.ts",
            "https://cdn.example.com/live/hls-50.ts",
            "https://cdn.example.com/video/track-1-frag-5.ts",
            "https://cdn.example.com/dash/chunk-stream0-00001.m4s",
            "https://cdn.example.com/dash/init-stream0.m4s",
            "https://cdn.example.com/video/init.mp4",
            "https://cdn.example.com/video/init_video.mp4",
            "https://cdn.example.com/video/header.mp4",
            "https://cdn.example.com/video/frag-1.mp4",
            "https://cdn.example.com/stream.mp4?sq=3&rn=2",
            "https://cdn.example.com/stream.mp4?bytestart=0&byteend=5000",
            "https://cdn.example.com/stream/part-1.dash",
            "blob:https://example.com/abc-123"
        )

        for (frag in fragmentCases) {
            assertTrue("Expected fragment detection for $frag", MediaUrlValidator.isMediaFragment(frag))
            assertFalse("Expected non-valid media for $frag", MediaUrlValidator.isValidMedia(frag))
        }

        val validVideos = listOf(
            "https://cdn.example.com/videos/full_trailer.mp4",
            "https://cdn.example.com/hls/master.m3u8",
            "https://cdn.example.com/dash/manifest.mpd",
            "https://cdn.example.com/assets/animation.webm",
            "https://cdn.example.com/shows/episode_01.mkv"
        )

        for (vid in validVideos) {
            assertFalse("Valid video should not be fragment: $vid", MediaUrlValidator.isMediaFragment(vid))
            assertTrue("Valid video should be accepted as media: $vid", MediaUrlValidator.isValidMedia(vid))
        }
    }

    @Test
    fun testStreamingClassificationAccuracy() {
        val hlsPathMp4 = "https://cdn.example.com/hls/sample.mp4"
        val classMp4 = MediaClassifier.classify(hlsPathMp4)
        assertEquals(MediaCategory.VIDEOS, classMp4.category)
        assertEquals("MP4", classMp4.format)
        assertFalse(classMp4.isStream)

        val hlsManifest = "https://cdn.example.com/live/playlist.m3u8"
        val classHls = MediaClassifier.classify(hlsManifest)
        assertEquals(MediaCategory.STREAMS, classHls.category)
        assertEquals("HLS (m3u8)", classHls.format)
        assertTrue(classHls.isStream)

        val dashManifest = "https://cdn.example.com/dash/video.mpd"
        val classDash = MediaClassifier.classify(dashManifest)
        assertEquals(MediaCategory.STREAMS, classDash.category)
        assertEquals("DASH (mpd)", classDash.format)
        assertTrue(classDash.isStream)
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

    @Test
    fun testWikipediaFilePagesAndRelativeLinksRejection() {
        assertFalse(MediaUrlValidator.isValidMedia("/wiki/File:Taj_Mahal-10_(cropped).jpg"))
        assertFalse(MediaUrlValidator.isValidMedia("/wiki/File:Mumtaz_Mahal.jpg"))
        assertFalse(MediaUrlValidator.isValidMedia("https://en.wikipedia.org/wiki/File:Taj_Mahal-10_(cropped).jpg"))
        assertFalse(MediaUrlValidator.isValidMedia("https://en.wikipedia.org/wiki/File:Shahjahan_on_globe.jpg"))
        assertFalse(MediaUrlValidator.isValidMedia("/w/index.php?title=File:Taj_Mahal.jpg"))

        assertTrue(MediaUrlValidator.isValidMedia("https://upload.wikimedia.org/wikipedia/commons/1/1d/Taj_Mahal-10_(cropped).jpg"))
        assertTrue(MediaUrlValidator.isValidMedia("https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/Taj_Mahal-10_%28cropped%29.jpg/500px-Taj_Mahal-10_%28cropped%29.jpg"))
        assertTrue(MediaUrlValidator.isValidMedia("//upload.wikimedia.org/wikipedia/commons/thumb/1/1d/Taj_Mahal-10_%28cropped%29.jpg/500px-Taj_Mahal-10_%28cropped%29.jpg"))
    }

    @Test
    fun contentAwareFilterRejectsChromeAssetsAndAcceptsContentMedia() {
        assertTrue(ContentAwareMediaFilter.isUiChromeUrl("https://en.wikipedia.org/static/images/mobile/copyright/wikipedia-wordmark-en.svg"))
        assertTrue(ContentAwareMediaFilter.isUiChromeUrl("https://example.com/assets/logo.png"))
        assertTrue(ContentAwareMediaFilter.isUiChromeUrl("https://example.com/favicon-32x32.png"))
        assertTrue(ContentAwareMediaFilter.isUiChromeUrl("https://example.com/banners/donate_fundraising_banner.jpg"))
        assertTrue(ContentAwareMediaFilter.isUiChromeUrl("https://example.com/icons/nav-arrow-right.png"))
        assertTrue(ContentAwareMediaFilter.isUiChromeUrl("https://example.com/img.jpg", width = 16, height = 16))
        assertTrue(ContentAwareMediaFilter.isUiChromeUrl("https://example.com/divider.png", width = 800, height = 10))

        assertFalse(ContentAwareMediaFilter.isUiChromeUrl("https://upload.wikimedia.org/wikipedia/commons/1/10/Taj_Mahal-10_%28cropped%29.jpg"))
        assertFalse(ContentAwareMediaFilter.isUiChromeUrl("https://example.com/photos/landscape_2026.jpg", width = 1920, height = 1080))
        assertFalse(ContentAwareMediaFilter.isUiChromeUrl("https://example.com/stream/playlist.m3u8", isVideoOrAudioOrStream = true))
    }

    @Test
    fun isSvgDetectionAndDefaultFiltering() {
        val svgItem = ScrapedMediaItem(
            id = "svg_1",
            url = "https://example.com/vectors/illustration.svg",
            title = "Illustration",
            format = "SVG",
            mimeType = "image/svg+xml"
        )
        val rasterItem = ScrapedMediaItem(
            id = "jpg_1",
            url = "https://example.com/photos/landscape.jpg",
            title = "Landscape",
            format = "JPG",
            mimeType = "image/jpeg"
        )

        assertTrue(MediaClassifier.isSvg(svgItem.url, svgItem.mimeType, svgItem.format))
        assertFalse(MediaClassifier.isSvg(rasterItem.url, rasterItem.mimeType, rasterItem.format))

        val allItems = listOf(svgItem, rasterItem)
        val defaultFiltered = allItems.filter { !MediaClassifier.isSvg(it.url, it.mimeType, it.format) }
        assertEquals(1, defaultFiltered.size)
        assertEquals("jpg_1", defaultFiltered.first().id)

        val showSvgFiltered = allItems
        assertEquals(2, showSvgFiltered.size)
    }

    @Test
    fun testAdBlockEngineIntegration() {
        val adUrls = listOf(
            "https://googleads.g.doubleclick.net/pagead/ads?client=ca-pub-123",
            "https://www.google-analytics.com/analytics.js",
            "https://static.criteo.net/js/ld/ld.js",
            "https://cdn.taboola.com/libtrc/unip/123/tfa.js",
            "https://widgets.outbrain.com/outbrain.js",
            "https://ib.adnxs.com/seg?add=1",
            "https://main.exoclick.com/tag.js",
            "https://b.clarity.ms/collect",
            "https://static.hotjar.com/c/hotjar-123.js",
            "https://connect.facebook.net/en_us/fbevents.js"
        )

        for (ad in adUrls) {
            assertTrue("Expected AdBlock detection for $ad", AdBlockEngine.isAdOrTracker(ad))
            assertFalse("Expected isValidMedia to reject ad URL $ad", MediaUrlValidator.isValidMedia(ad))
        }

        val emptyResp = AdBlockEngine.createEmptyResponse()
        assertEquals("text/plain", emptyResp.mimeType)
        assertEquals("UTF-8", emptyResp.encoding)
        assertNotNull(emptyResp.data)
        assertEquals(0, emptyResp.data.available())

        val regularMedia = "https://images.unsplash.com/photo-123.jpg"
        assertFalse(AdBlockEngine.isAdOrTracker(regularMedia))
        assertTrue(MediaUrlValidator.isValidMedia(regularMedia))
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
}
