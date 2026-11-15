package com.amresalehin.emregallery

import android.net.Uri
import android.webkit.WebResourceRequest
import com.amresalehin.emregallery.data.BrowserMediaExtractor
import com.amresalehin.emregallery.data.MediaCaptureEngine
import com.amresalehin.emregallery.data.MediaCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaCaptureAudioSemanticsTest {
    private val page = "https://www.reddit.com/r/test/comments/abc123/sample_post/"

    private fun mediaRequest(url: String, accept: String): WebResourceRequest =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)
            override fun isForMainFrame(): Boolean = false
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): Map<String, String> = mapOf("Accept" to accept)
        }

    @Test
    fun domScanPreservesAudioSemanticsForMp4CmafTrack() {
        val item = MediaCaptureEngine.parseJsDomScanResult(
            """[{"u":"https://v.redd.it/abc/CMAF_AUDIO_128.mp4","s":"DOM","t":"audio"}]""",
            page
        ).single()

        assertEquals(MediaCategory.AUDIO, item.category)
        assertTrue(item.isAudio)
        assertFalse(item.isVideo)
    }

    @Test
    fun legacyDomAudioMarkerRemainsSupported() {
        val item = MediaCaptureEngine.parseJsDomScanResult(
            """[{"u":"https://v.redd.it/abc/CMAF_AUDIO_128.mp4","s":"DOM","r":"audio"}]""",
            page
        ).single()

        assertEquals(MediaCategory.AUDIO, item.category)
        assertTrue(item.isAudio)
        assertFalse(item.isVideo)
    }

    @Test
    fun networkAcceptAudioOverridesMp4Extension() {
        val discovered = mutableListOf<com.amresalehin.emregallery.data.ScrapedMediaItem>()
        MediaCaptureEngine.shouldInterceptRequest(
            request = mediaRequest("https://cdn.example.test/track.mp4", "audio/mp4, */*"),
            baseUrl = page,
            onMediaDiscovered = discovered::add
        )

        val item = discovered.single()
        assertEquals(MediaCategory.AUDIO, item.category)
        assertTrue(item.isAudio)
        assertFalse(item.isVideo)
    }

    @Test
    fun networkCmafFilenameOverridesGenericAcceptHeader() {
        val discovered = mutableListOf<com.amresalehin.emregallery.data.ScrapedMediaItem>()
        MediaCaptureEngine.shouldInterceptRequest(
            request = mediaRequest("https://v.redd.it/abc/CMAF_AUDIO_128.mp4", "*/*"),
            baseUrl = page,
            onMediaDiscovered = discovered::add
        )

        val item = discovered.single()
        assertEquals(MediaCategory.AUDIO, item.category)
        assertTrue(item.isAudio)
        assertFalse(item.isVideo)
    }

    @Test
    fun mutationObserverCarriesAudioAndVideoElementTypes() {
        val script = MediaCaptureEngine.getMutationObserverScript()
        assertTrue(script.contains("report(node.src, 'audio')"))
        assertTrue(script.contains("report(node.src, 'video')"))
        assertTrue(script.contains("window.EmreBridge.reportMedia(abs, mediaType || '')"))
    }

    @Test
    fun browserBridgePassesElementSemanticType() {
        var foundUrl: String? = null
        var foundType: String? = null
        val bridge = BrowserMediaExtractor.EmreMediaBridge { url, type ->
            foundUrl = url
            foundType = type
        }

        bridge.reportMedia("https://v.redd.it/abc/CMAF_AUDIO_128.mp4", "audio")

        assertEquals("https://v.redd.it/abc/CMAF_AUDIO_128.mp4", foundUrl)
        assertEquals("audio", foundType)
    }
}
