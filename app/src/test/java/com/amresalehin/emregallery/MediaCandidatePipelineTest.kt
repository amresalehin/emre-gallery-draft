package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.MediaCandidatePipeline
import com.amresalehin.emregallery.data.MediaProviderRouting
import com.amresalehin.emregallery.data.ScrapedMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCandidatePipelineTest {

    @Test
    fun rejectsFragmentsTrackersAndWebsiteChrome() {
        val fragment = image("segment", "https://cdn.example.com/chunk-12.mp4")
        val tracker = image("tracker", "https://analytics.example.com/pixel.jpg")
        val logo = image("logo", "https://example.com/assets/site-logo.png")

        assertFalse(MediaCandidatePipeline.isUsefulCandidate(fragment))
        assertFalse(MediaCandidatePipeline.isUsefulCandidate(tracker))
        assertFalse(MediaCandidatePipeline.isUsefulCandidate(logo))
        assertTrue(MediaCandidatePipeline.merge(emptyList(), listOf(fragment, tracker, logo)).isEmpty())
    }

    @Test
    fun mergesResolutionVariantsAndPreservesExistingItemId() {
        val low = image("stable-id", "https://images.example.com/photo-320x240.webp", "320x240")
        val high = image("new-id", "https://images.example.com/photo-1920x1080.jpg", "1920x1080")

        val result = MediaCandidatePipeline.merge(listOf(low), listOf(high))

        assertEquals(1, result.size)
        assertEquals("stable-id", result.single().id)
        assertEquals("1920x1080", result.single().resolutionHint)
    }

    @Test
    fun foldsYoutubePosterIntoVideoInsteadOfShowingItAsSeparateDownload() {
        val video = ScrapedMediaItem(
            id = "video-id",
            url = "https://rr1.googlevideo.com/videoplayback?id=FaEeQPmv1D4",
            title = "Clip",
            isVideo = true,
            thumbnailUrl = "https://i.ytimg.com/vi/FaEeQPmv1D4/hqdefault.jpg"
        )
        val poster = image("poster-id", "https://i.ytimg.com/vi/FaEeQPmv1D4/oar2.jpg", "405x720")

        val result = MediaCandidatePipeline.merge(emptyList(), listOf(video, poster))

        assertEquals(1, result.size)
        assertTrue(result.single().isVideo)
        assertEquals("video-id", result.single().id)
        assertTrue(result.single().thumbnailUrl?.contains("oar2.jpg") == true)
    }

    @Test
    fun providerHostMatchingUsesBoundariesAndIgnoresLookalikeDomains() {
        assertTrue(MediaProviderRouting.isYtDlpUrl("https://m.youtube.com/watch?v=123"))
        assertTrue(MediaProviderRouting.isYtDlpUrl("https://youtu.be/abc"))
        assertFalse(MediaProviderRouting.isYtDlpUrl("https://youtube.com.attacker.example/video"))
        assertFalse(MediaProviderRouting.isYtDlpUrl("https://notyoutube.com/video"))
        assertEquals("example.com", MediaProviderRouting.hostOf("https://www.example.com/path"))
    }

    private fun image(id: String, url: String, resolution: String? = null) = ScrapedMediaItem(
        id = id,
        url = url,
        title = "photo",
        resolutionHint = resolution,
        thumbnailUrl = url
    )
}
