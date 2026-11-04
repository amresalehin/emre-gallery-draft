package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.MediaCategory
import com.amresalehin.emregallery.data.MediaProvenance
import com.amresalehin.emregallery.data.MediaResolver
import com.amresalehin.emregallery.data.ScrapedMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaResolverTest {

    @Test
    fun pinterestResolutionVariantsCollapseToOneOriginal() {
        val small = ScrapedMediaItem(
            id = "small",
            url = "https://i.pinimg.com/236x/aa/bb/cc/image.jpg",
            title = "image-small.jpg",
            category = MediaCategory.IMAGES,
            width = 236,
            height = 393,
            sizeBytes = 12000
        )
        val large = small.copy(
            id = "large",
            url = "https://i.pinimg.com/736x/aa/bb/cc/image.jpg",
            title = "image-large.jpg",
            width = 736,
            height = 1227,
            sizeBytes = 95000
        )
        val original = small.copy(
            id = "original",
            url = "https://i.pinimg.com/originals/aa/bb/cc/image.jpg",
            title = "image-original.jpg",
            sizeBytes = 110000
        )

        val resolved = MediaResolver.resolve(listOf(small, large, original))
        assertEquals(1, resolved.size)
        assertEquals(original.url, resolved.single().url)
    }

    @Test
    fun splitVideoAndAudioRemainOneLogicalItem() {
        val video = ScrapedMediaItem(
            id = "video",
            url = "https://cdn.example/video-1080.mp4",
            title = "clip.mp4",
            isVideo = true,
            category = MediaCategory.VIDEOS,
            canonicalMediaId = "platform:123",
            width = 1920,
            height = 1080,
            hasAudio = false,
            provenance = MediaProvenance("yt-dlp")
        )
        val audioVariant = video.copy(
            id = "audio-paired",
            audioUrl = "https://cdn.example/audio.m4a",
            hasAudio = true,
            sizeBytes = 10_000_000
        )

        val resolved = MediaResolver.resolve(listOf(video, audioVariant))
        assertEquals(1, resolved.size)
        assertEquals(video.url, resolved.single().url)
        assertEquals("https://cdn.example/audio.m4a", resolved.single().audioUrl)
        assertEquals(true, resolved.single().hasAudio)
    }

    @Test
    fun unrelatedImagesRemainSeparate() {
        val items = listOf(
            ScrapedMediaItem("one", "https://i.pinimg.com/236x/a/one.jpg", "one.jpg"),
            ScrapedMediaItem("two", "https://i.pinimg.com/236x/a/two.jpg", "two.jpg")
        )
        assertEquals(2, MediaResolver.resolve(items).size)
    }

    @Test
    fun resolverRetainsExtractorMetadataAndHeaders() {
        val first = ScrapedMediaItem(
            "a", "https://cdn.example/a.jpg", "a.jpg",
            metadata = mapOf("first" to "yes"),
            downloadHeaders = mapOf("Referer" to "https://site.example")
        )
        val second = first.copy(
            id = "b",
            metadata = mapOf("second" to "yes"),
            downloadHeaders = mapOf("Cookie" to "session=abc")
        )
        val result = MediaResolver.resolve(listOf(first, second)).single()
        assertNotNull(result.downloadHeaders["Referer"])
        assertNotNull(result.downloadHeaders["Cookie"])
        assertEquals("yes", result.metadata["first"])
        assertEquals("yes", result.metadata["second"])
    }
}
