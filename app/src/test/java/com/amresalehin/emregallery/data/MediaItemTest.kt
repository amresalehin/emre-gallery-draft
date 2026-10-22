package com.amresalehin.emregallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaItemTest {
    @Test fun videoFlagAndMetadataArePreserved() {
        val item = MediaItem(7, "content://media/7", "clip.mp4", "video/mp4", 100L, 200L, 4096L, 1080, 1920, "Movies/Trips/", true, 125000L)
        assertEquals(true, item.isVideo)
        assertEquals("clip.mp4", item.name)
        assertEquals(125000L, item.durationMillis)
    }

    @Test fun durationAndMediaTypeRemainAvailable() {
        val item = MediaItem(7L, "content://media/7", "clip.mp4", "video/mp4", 100L, 200L, 4096L, 1080, 1920, "Movies/Trips/", true, 125000L)
        assertEquals("clip.mp4", item.name)
        assertEquals(true, item.isVideo)
        assertEquals(125000L, item.durationMillis)
    }
}
