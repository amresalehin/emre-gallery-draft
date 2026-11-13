package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.MediaCategory
import com.amresalehin.emregallery.data.MediaClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioMediaClassificationTest {

    @Test
    fun extensionlessAudioCdnUrlUsesExplicitMimeType() {
        val result = MediaClassifier.classify(
            "https://cdn.example.test/delivery/track?id=12345&token=opaque",
            "audio/ogg"
        )
        assertEquals(MediaCategory.AUDIO, result.category)
        assertTrue(result.isAudio)
        assertFalse(result.isVideo)
        assertEquals("OGG", result.format)
    }

    @Test
    fun audioWildcardMimeStillClassifiesAsAudio() {
        val result = MediaClassifier.classify("https://cdn.example.test/track?opaque=1", "audio/*")
        assertEquals(MediaCategory.AUDIO, result.category)
        assertEquals("AUDIO", result.format)
    }

    @Test
    fun audioWebmIsNotMisclassifiedAsVideoWebm() {
        val result = MediaClassifier.classify("https://cdn.example.test/track.webm", "audio/webm")
        assertEquals(MediaCategory.AUDIO, result.category)
        assertTrue(result.isAudio)
        assertFalse(result.isVideo)
    }

    @Test
    fun audioOnlyHlsManifestRetainsAudioSemantics() {
        val result = MediaClassifier.classify(
            "https://cdn.example.test/audio/index.m3u8?sig=opaque",
            "audio/vnd.apple.mpegurl"
        )
        assertEquals(MediaCategory.STREAMS, result.category)
        assertTrue(result.isStream)
        assertTrue(result.isAudio)
        assertFalse(result.isVideo)
    }

    @Test
    fun commonAudioContainerExtensionsAreRecognized() {
        val extensions = listOf("m4b", "oga", "weba", "amr", "aiff", "caf", "mka")
        extensions.forEach { extension ->
            val result = MediaClassifier.classify("https://cdn.example.test/audio.$extension")
            assertEquals("$extension should be audio", MediaCategory.AUDIO, result.category)
            assertTrue("$extension should set isAudio", result.isAudio)
        }
    }

    @Test
    fun videoMp4RemainsVideo() {
        val result = MediaClassifier.classify("https://cdn.example.test/movie.mp4", "video/mp4")
        assertEquals(MediaCategory.VIDEOS, result.category)
        assertTrue(result.isVideo)
        assertFalse(result.isAudio)
    }
}
