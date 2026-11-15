package com.amresalehin.emregallery.ui

import com.amresalehin.emregallery.data.MediaCategory
import com.amresalehin.emregallery.data.ScrapedMediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloaderMediaDisplayUtilsTest {
    @Test
    fun unknownImageDimensionsAreNotInvented() {
        val item = ScrapedMediaItem(
            id = "image", url = "https://cdn.example.test/image.jpg",
            title = "image.jpg", category = MediaCategory.IMAGES
        )
        assertEquals("Dimensions unavailable", resolveScrapedMediaDimensionLabel(item, emptyMap()))
    }

    @Test
    fun unknownVideoDimensionsAreNotInvented() {
        val item = ScrapedMediaItem(
            id = "video", url = "https://cdn.example.test/video.mp4",
            title = "video.mp4", isVideo = true, category = MediaCategory.VIDEOS
        )
        assertEquals("Resolution unavailable", resolveScrapedMediaDimensionLabel(item, emptyMap()))
    }

    @Test
    fun audioUsesAudioLabelInsteadOfSpatialDimensions() {
        val item = ScrapedMediaItem(
            id = "audio", url = "https://cdn.example.test/CMAF_AUDIO_128.mp4",
            title = "CMAF_AUDIO_128.mp4", isAudio = true,
            category = MediaCategory.AUDIO, resolutionHint = "40 × 40"
        )
        assertEquals("Audio only", resolveScrapedMediaDimensionLabel(item, emptyMap()))
    }

    @Test
    fun actualDimensionsArePreserved() {
        val item = ScrapedMediaItem(
            id = "image", url = "https://cdn.example.test/image.jpg", title = "image.jpg"
        )
        assertEquals("640 × 360", resolveScrapedMediaDimensionLabel(item, mapOf("image" to (640 to 360))))
    }
}
