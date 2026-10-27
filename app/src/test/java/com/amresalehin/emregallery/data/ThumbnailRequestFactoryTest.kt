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
        assertEquals(ThumbnailSize(768, 768), ThumbnailRequestFactory.sizeFor(1200, 1400))
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
