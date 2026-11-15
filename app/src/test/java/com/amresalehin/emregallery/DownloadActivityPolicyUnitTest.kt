package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.DownloadActivityItem
import com.amresalehin.emregallery.data.DownloadActivityManager
import com.amresalehin.emregallery.data.DownloadActivityStatus
import com.amresalehin.emregallery.ui.getDisplayFormatBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for download activity item status filtering, formatting helpers, badge rules,
 * transient failure detection, and exponential retry backoff calculations.
 */
class DownloadActivityPolicyUnitTest {

    @Test
    fun testDownloadActivityItemModelAndFiltering() {
        val downloadingItem = DownloadActivityItem(
            id = "act_1",
            title = "Best Dolby Vision 4K HDR Video",
            mediaUrl = "https://example.com/video1.mp4",
            isVideo = true,
            format = "4K",
            status = DownloadActivityStatus.DOWNLOADING,
            progress = 0.40f,
            bytesRead = 487_830_000L,
            totalBytes = 1_200_000_000L,
            speedBytesPerSec = 8_900_000L,
            durationHint = "30:06"
        )

        val failedItem = DownloadActivityItem(
            id = "act_2",
            title = "Nature in 4K.mp4",
            mediaUrl = "https://example.com/video2.mp4",
            isVideo = true,
            format = "4K",
            status = DownloadActivityStatus.FAILED,
            errorMessage = "Network error",
            durationHint = "04:21"
        )

        val finishedItem = DownloadActivityItem(
            id = "act_3",
            title = "Scenic Sunset in 4K.mp4",
            mediaUrl = "https://example.com/video3.mp4",
            localUri = "content://media/external/video/3",
            isVideo = true,
            format = "4K",
            status = DownloadActivityStatus.FINISHED,
            progress = 1.0f,
            totalBytes = 1_370_000_000L,
            durationHint = "12:18"
        )

        val activities = listOf(downloadingItem, failedItem, finishedItem)

        assertTrue(downloadingItem.isDownloading)
        assertFalse(downloadingItem.isFailed)
        assertFalse(downloadingItem.isFinished)

        assertTrue(failedItem.isFailed)
        assertFalse(failedItem.isDownloading)
        assertFalse(failedItem.isFinished)

        assertTrue(finishedItem.isFinished)
        assertFalse(finishedItem.isDownloading)
        assertFalse(finishedItem.isFailed)

        val downloadingOnly = activities.filter { it.isDownloading || it.isPaused }
        assertEquals(1, downloadingOnly.size)
        assertEquals("act_1", downloadingOnly[0].id)

        val failedOnly = activities.filter { it.isFailed }
        assertEquals(1, failedOnly.size)
        assertEquals("act_2", failedOnly[0].id)

        val finishedOnly = activities.filter { it.isFinished }
        assertEquals(1, finishedOnly.size)
        assertEquals("act_3", finishedOnly[0].id)

        val onePointTwoGb = (1.20 * 1024 * 1024 * 1024).toLong()
        assertEquals("1.20GB", DownloadActivityManager.formatBytes(onePointTwoGb))
        val fourEightSevenMb = (487.83 * 1024 * 1024).toLong()
        assertEquals("487.83MB", DownloadActivityManager.formatBytes(fourEightSevenMb))
        assertEquals("500.0KB", DownloadActivityManager.formatBytes(500 * 1024L))

        val eightPointNineSpeed = (8.90 * 1024 * 1024).toLong()
        assertEquals("8.90MB/s", DownloadActivityManager.formatSpeed(eightPointNineSpeed))
        assertEquals("0.00MB/s", DownloadActivityManager.formatSpeed(0L))
    }

    @Test
    fun testFalse4KBadgeIsStrictlyRejectedForImages() {
        val tajMahalImage = DownloadActivityItem(
            id = "img_taj",
            title = "Taj_Mahal_9794.jpg",
            mediaUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/Taj_Mahal_9794.jpg",
            isVideo = false,
            format = "4K",
            status = DownloadActivityStatus.FAILED
        )
        val badge = getDisplayFormatBadge(tajMahalImage)
        assertFalse("Thumbnail format badge must not be 4K for images", badge.equals("4K", ignoreCase = true))
        assertEquals("JPG", badge)

        val sambourneImage = DownloadActivityItem(
            id = "img_sam",
            title = "Sambourneagra1860s.jpg",
            mediaUrl = "https://upload.wikimedia.org/wikipedia/commons/1/12/Sambourneagra1860s.jpg",
            isVideo = false,
            format = "4K",
            status = DownloadActivityStatus.FAILED
        )
        assertEquals("JPG", getDisplayFormatBadge(sambourneImage))

        val pngImage = DownloadActivityItem(
            id = "img_png",
            title = "Diagram.png",
            mediaUrl = "https://example.com/diagram.png",
            isVideo = false,
            format = "PNG",
            status = DownloadActivityStatus.FAILED
        )
        assertEquals("PNG", getDisplayFormatBadge(pngImage))

        val real4kVideo = DownloadActivityItem(
            id = "vid_4k",
            title = "Nature 4K Documentary",
            mediaUrl = "https://example.com/nature.mp4",
            isVideo = true,
            format = "4K",
            status = DownloadActivityStatus.DOWNLOADING
        )
        assertEquals("4K", getDisplayFormatBadge(real4kVideo))
    }

    @Test
    fun testTemporaryFailureDetection() {
        assertTrue(DownloadActivityManager.isTemporaryFailure(429, "Server returned HTTP status 429"))
        assertTrue(DownloadActivityManager.isTemporaryFailure(500, "Internal Server Error"))
        assertTrue(DownloadActivityManager.isTemporaryFailure(502, "Bad Gateway"))
        assertTrue(DownloadActivityManager.isTemporaryFailure(503, "Service Unavailable"))
        assertTrue(DownloadActivityManager.isTemporaryFailure(504, "Gateway Timeout"))
        assertTrue(DownloadActivityManager.isTemporaryFailure(-1, "SocketTimeoutException: timeout"))
        assertTrue(DownloadActivityManager.isTemporaryFailure(-1, "ConnectException: Failed to connect to host"))
        assertTrue(DownloadActivityManager.isTemporaryFailure(-1, "Connection reset by peer"))

        assertFalse(DownloadActivityManager.isTemporaryFailure(404, "Not Found"))
        assertFalse(DownloadActivityManager.isTemporaryFailure(401, "Unauthorized"))
        assertFalse(DownloadActivityManager.isTemporaryFailure(403, "Forbidden"))
    }

    @Test
    fun testExponentialBackoffWithJitterAndRetryAfter() {
        val delayFromHeader = DownloadActivityManager.calculateRetryDelaySeconds(attempt = 1, retryAfterSeconds = 25L)
        assertEquals(25L, delayFromHeader)

        val delay1 = DownloadActivityManager.calculateRetryDelaySeconds(attempt = 1, retryAfterSeconds = null)
        assertTrue("Attempt 1 delay should be in [2, 3]", delay1 in 2L..3L)

        val delay2 = DownloadActivityManager.calculateRetryDelaySeconds(attempt = 2, retryAfterSeconds = null)
        assertTrue("Attempt 2 delay should be in [4, 6]", delay2 in 4L..6L)

        val delay3 = DownloadActivityManager.calculateRetryDelaySeconds(attempt = 3, retryAfterSeconds = null)
        assertTrue("Attempt 3 delay should be in [8, 12]", delay3 in 8L..12L)

        val delay4 = DownloadActivityManager.calculateRetryDelaySeconds(attempt = 4, retryAfterSeconds = null)
        assertTrue("Attempt 4 delay should be in [16, 24]", delay4 in 16L..24L)

        val delay5 = DownloadActivityManager.calculateRetryDelaySeconds(attempt = 5, retryAfterSeconds = null)
        assertTrue("Attempt 5 delay should be in [32, 48]", delay5 in 32L..48L)
    }

    @Test
    fun testRetryingItemsStayInDownloadingFilterUntil5RetriesExhausted() {
        val retryingItem = DownloadActivityItem(
            id = "retry_item_1",
            title = "Taj_Mahal_Photo.jpg",
            mediaUrl = "https://example.com/taj.jpg",
            status = DownloadActivityStatus.RETRYING,
            retryCount = 2,
            maxRetries = 5,
            nextRetryTimestampMillis = System.currentTimeMillis() + 8000L,
            retryDelaySeconds = 8L
        )

        val failedItem = DownloadActivityItem(
            id = "failed_item_1",
            title = "Unreachable.jpg",
            mediaUrl = "https://example.com/unreachable.jpg",
            status = DownloadActivityStatus.FAILED,
            retryCount = 5,
            maxRetries = 5,
            errorMessage = "Server returned HTTP status 429"
        )

        val activities = listOf(retryingItem, failedItem)

        assertFalse("Retrying item must not have isFailed = true", retryingItem.isFailed)
        assertTrue("Retrying item must have isRetrying = true", retryingItem.isRetrying)

        val downloadingFilter = activities.filter { it.isDownloading || it.isPaused || it.isRetrying }
        assertEquals(1, downloadingFilter.size)
        assertEquals("retry_item_1", downloadingFilter[0].id)

        val failedFilter = activities.filter { it.isFailed }
        assertEquals(1, failedFilter.size)
        assertEquals("failed_item_1", failedFilter[0].id)
    }

    @Test
    fun testStackAndCollectionPreservationFields() {
        val originalItem = DownloadActivityItem(
            id = "batch_item_1",
            title = "Taj_Mahal_Gate.jpg",
            mediaUrl = "https://example.com/gate.jpg",
            status = DownloadActivityStatus.RETRYING,
            stackId = "stack_taj_mahal",
            stackTitle = "Taj Mahal - Wikipedia",
            sourceDomain = "wikipedia.org",
            sourceUrl = "https://en.wikipedia.org/wiki/Taj_Mahal"
        )

        assertEquals("stack_taj_mahal", originalItem.stackId)
        assertEquals("Taj Mahal - Wikipedia", originalItem.stackTitle)
        assertEquals("wikipedia.org", originalItem.sourceDomain)
        assertEquals("https://en.wikipedia.org/wiki/Taj_Mahal", originalItem.sourceUrl)
    }
}
