package com.amresalehin.emregallery.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThumbnailRequestFactoryTest {
    @Test
    fun sizeUsesStableResolutionBuckets() {
        assertEquals(ThumbnailSize(192, 320), ThumbnailRequestFactory.sizeFor(180, 241))
        assertEquals(ThumbnailSize(320, 512), ThumbnailRequestFactory.sizeFor(260, 400))
        assertEquals(ThumbnailSize(512, 512), ThumbnailRequestFactory.sizeFor(1200, 1400))
    }

    @Test
    fun mediaItemAndUriUseTheSameCacheIdentity() {
        val item = MediaItem(
            id = 42L,
            uri = "content://media/external/images/media/42",
            name = "photo.jpg",
            mimeType = "image/jpeg",
            dateAddedSeconds = 1L,
            dateTakenMillis = 1L,
            sizeBytes = 123L,
            width = 1200,
            height = 800,
            relativePath = "DCIM/Camera",
            isVideo = false
        )
        assertEquals(
            ThumbnailRequestFactory.cacheKey(Uri.parse(item.uri), false, 320, 512, item.sizeBytes),
            ThumbnailRequestFactory.cacheKey(item, 320, 512)
        )
    }

    @Test
    fun cacheIdentitySeparatesOutputVariantsAndRevision() {
        val uri = Uri.parse("content://media/external/video/media/42")
        val first = ThumbnailRequestFactory.cacheKey(uri, true, 192, 320, 100L)
        assertNotEquals(first, ThumbnailRequestFactory.cacheKey(uri, true, 320, 320, 100L))
        assertNotEquals(first, ThumbnailRequestFactory.cacheKey(uri, false, 192, 320, 100L))
        assertNotEquals(first, ThumbnailRequestFactory.cacheKey(uri, true, 192, 320, 101L))
    }
}
