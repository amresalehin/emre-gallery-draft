package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.CmafTrackAssociation
import com.amresalehin.emregallery.data.MediaCategory
import com.amresalehin.emregallery.data.MediaClassifier
import com.amresalehin.emregallery.data.MediaProvenance
import com.amresalehin.emregallery.data.ScrapedMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CmafTrackAssociationTest {
    private val page = "https://www.reddit.com/r/test/comments/abc123/sample_post/"

    private fun video(url: String, sourcePage: String = page) = ScrapedMediaItem(
        id = "video_${url.substringAfterLast('/')}", url = url, title = url.substringAfterLast('/'),
        isVideo = true, category = MediaCategory.VIDEOS, format = "MP4", mimeType = "video/mp4",
        provenance = MediaProvenance(extractorEngine = "DOM", pageUrl = sourcePage)
    )

    private fun audio(url: String, sourcePage: String = page) = ScrapedMediaItem(
        id = "audio_${url.substringAfterLast('/')}", url = url, title = url.substringAfterLast('/'),
        isAudio = true, category = MediaCategory.AUDIO, format = "MP4 AUDIO", mimeType = "audio/mp4",
        provenance = MediaProvenance(extractorEngine = "DOM", pageUrl = sourcePage)
    )

    @Test
    fun samePageCmafTracksBecomeOneSelectableVideo() {
        val low = video("https://v.redd.it/abc/CMAF_VP9_480.mp4")
        val high = video("https://v.redd.it/abc/CMAF_VP9_720.mp4")
        val soundtrack = audio("https://v.redd.it/abc/CMAF_AUDIO_128.mp4")
        val items = MediaClassifier.deduplicateScrapedItems(listOf(low, soundtrack, high))
        assertEquals(1, items.size)
        assertEquals(high.url, items.single().url)
        assertEquals(soundtrack.url, CmafTrackAssociation.companionAudioUrl(items.single()))
        assertTrue(CmafTrackAssociation.requiresAudio(items.single()))
        assertTrue(items.single().isVideo)
        assertFalse(items.single().isAudio)
    }

    @Test
    fun tracksFromDifferentPostsAreNotPaired() {
        val video = video("https://v.redd.it/abc/CMAF_VP9_480.mp4")
        val audio = audio(
            "https://v.redd.it/other/CMAF_AUDIO_128.mp4",
            "https://www.reddit.com/r/test/comments/other/post/"
        )
        val items = MediaClassifier.deduplicateScrapedItems(listOf(video, audio))
        assertEquals(2, items.size)
        assertFalse(items.any { CmafTrackAssociation.requiresAudio(it) })
        assertTrue(items.any { it.isAudio })
    }

    @Test
    fun nonCmafStandaloneAudioRemainsIndependent() {
        val video = video("https://cdn.example.test/post/video.mp4")
        val audio = audio("https://cdn.example.test/post/music.mp4")
        val items = CmafTrackAssociation.associate(listOf(video, audio))
        assertEquals(2, items.size)
        assertFalse(items.any { CmafTrackAssociation.requiresAudio(it) })
    }
}
