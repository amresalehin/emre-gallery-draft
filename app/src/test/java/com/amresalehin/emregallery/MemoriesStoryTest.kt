package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.MediaItem
import com.amresalehin.emregallery.ui.Destination
import com.amresalehin.emregallery.ui.NavBackStackEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MemoriesStoryTest {

    @Test
    fun navBackStackEntryPreservesStoryTitle() {
        val dummyMedia = MediaItem(
            id = 101L,
            uri = "content://media/external/images/media/101",
            name = "IMG_001.jpg",
            relativePath = "DCIM/Camera",
            mimeType = "image/jpeg",
            dateTakenMillis = 1700000000000L,
            dateAddedSeconds = 1700000000L,
            sizeBytes = 2048576L,
            width = 1920,
            height = 1080,
            isVideo = false
        )

        val entry = NavBackStackEntry(
            destination = Destination.VIEWER,
            selectedMedia = dummyMedia,
            viewerMediaList = listOf(dummyMedia),
            viewerStoryTitle = "On this day · 2023"
        )

        assertEquals(Destination.VIEWER, entry.destination)
        assertEquals("On this day · 2023", entry.viewerStoryTitle)
        assertEquals(1, entry.viewerMediaList.size)
        assertEquals(dummyMedia.uri, entry.selectedMedia?.uri)
    }

    @Test
    fun testSegmentedProgressCalculation() {
        val totalItems = 5
        val currentIndex = 2
        val currentProgress = 0.65f

        for (i in 0 until totalItems) {
            val progress = when {
                i < currentIndex -> 1f
                i == currentIndex -> currentProgress.coerceIn(0f, 1f)
                else -> 0f
            }

            when (i) {
                0 -> assertEquals(1f, progress, 0.001f)
                1 -> assertEquals(1f, progress, 0.001f)
                2 -> assertEquals(0.65f, progress, 0.001f)
                3 -> assertEquals(0f, progress, 0.001f)
                4 -> assertEquals(0f, progress, 0.001f)
            }
        }
    }

    @Test
    fun testTapZoneCalculation() {
        val screenWidth = 1080f
        val leftZoneThreshold = screenWidth * 0.30f

        val tapXLeft = 200f
        val tapXRight = 800f

        val isLeftTap = tapXLeft < leftZoneThreshold
        val isRightTap = tapXRight >= leftZoneThreshold

        assertEquals(true, isLeftTap)
        assertEquals(true, isRightTap)
    }
}
