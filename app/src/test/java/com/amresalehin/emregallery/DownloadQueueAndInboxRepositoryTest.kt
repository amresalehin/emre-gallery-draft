package com.amresalehin.emregallery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emregallery.data.DownloadActivityManager
import com.amresalehin.emregallery.data.DownloadActivityStatus
import com.amresalehin.emregallery.data.DownloadQueueManager
import com.amresalehin.emregallery.data.MediaInboxItem
import com.amresalehin.emregallery.data.MediaInboxRepository
import com.amresalehin.emregallery.data.MediaInboxStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadQueueAndInboxRepositoryTest {

    @Test
    fun downloadQueueManagerActiveDownloadState() {
        val manager = DownloadQueueManager
        manager.cancelAll(ApplicationProvider.getApplicationContext())
        assertFalse(manager.inMemoryDownloading.value)

        manager.markDownloadStarted(2)
        assertTrue(manager.inMemoryDownloading.value)

        manager.markDownloadCompleted()
        assertTrue(manager.inMemoryDownloading.value)

        manager.markDownloadCompleted()
        assertFalse(manager.inMemoryDownloading.value)
    }

    @Test
    fun downloadQueueManagerCancelAllResetsState() {
        val manager = DownloadQueueManager
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        manager.markDownloadStarted(5)
        assertTrue(manager.inMemoryDownloading.value)

        manager.cancelAll(ctx)
        assertFalse(manager.inMemoryDownloading.value)
    }

    @Test
    fun handleDownloadFailureDoesNotOverwritePausedStatus() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val manager = DownloadActivityManager
        manager.initialize(ctx)

        val itemId = "test_pause_item_${System.currentTimeMillis()}"
        manager.recordQueued(
            id = itemId,
            title = "Video Clip",
            mediaUrl = "https://example.com/clip.mp4"
        )

        manager.pauseDownload(ctx, itemId)
        val paused = manager.activities.value.first { it.id == itemId }
        assertEquals(DownloadActivityStatus.PAUSED, paused.status)

        val handled = manager.handleDownloadFailure(
            context = ctx,
            itemId = itemId,
            statusCode = -1,
            errorMessage = "Socket timeout",
            retryAfterSeconds = null
        )

        assertFalse("handleDownloadFailure should ignore failure callbacks for paused item", handled)
        val afterCallback = manager.activities.value.first { it.id == itemId }
        assertEquals(DownloadActivityStatus.PAUSED, afterCallback.status)
    }

    @Test
    fun handleDownloadFailureDoesNotOverwriteCancelledStatus() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val manager = DownloadActivityManager
        manager.initialize(ctx)

        val itemId = "test_cancel_item_${System.currentTimeMillis()}"
        manager.recordQueued(
            id = itemId,
            title = "Video Clip 2",
            mediaUrl = "https://example.com/clip2.mp4"
        )

        manager.cancelAllActive(ctx)
        val cancelled = manager.activities.value.first { it.id == itemId }
        assertEquals(DownloadActivityStatus.FAILED, cancelled.status)
        assertEquals("Cancelled by user", cancelled.errorMessage)

        val handled = manager.handleDownloadFailure(
            context = ctx,
            itemId = itemId,
            statusCode = -1,
            errorMessage = "Connection reset by peer",
            retryAfterSeconds = null
        )

        assertFalse("handleDownloadFailure should not restart retry for user-cancelled item", handled)
        val afterCallback = manager.activities.value.first { it.id == itemId }
        assertEquals(DownloadActivityStatus.FAILED, afterCallback.status)
        assertEquals("Cancelled by user", afterCallback.errorMessage)
    }

    @Test
    fun stackConsolidationDoesNotMergeOnTitleAlone() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val repo = MediaInboxRepository.getInstance(ctx)
        repo.clearAll()

        val stack1 = MediaInboxStack(
            id = "stack_page_1",
            title = "Home",
            sourceDomain = "example.com",
            sourceUrl = "https://example.com/page1",
            items = listOf(
                MediaInboxItem(
                    id = "item_p1",
                    title = "photo1.jpg",
                    localUri = "/tmp/photo1.jpg",
                    sourceUrl = "https://example.com/photo1.jpg",
                    isVideo = false
                )
            )
        )

        val stack2 = MediaInboxStack(
            id = "stack_page_2",
            title = "Home",
            sourceDomain = "instagram.com",
            sourceUrl = "https://instagram.com/page2",
            items = listOf(
                MediaInboxItem(
                    id = "item_p2",
                    title = "photo2.jpg",
                    localUri = "/tmp/photo2.jpg",
                    sourceUrl = "https://instagram.com/photo2.jpg",
                    isVideo = false
                )
            )
        )

        repo.addStack(stack1)
        repo.addStack(stack2)

        val stacks = repo.stacks.value
        assertEquals("Two distinct pages with title 'Home' must not be merged into one stack", 2, stacks.size)
    }

    @Test
    fun addItemToStackDeletesReplacedFileOnDisk() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val repo = MediaInboxRepository.getInstance(ctx)
        repo.clearAll()

        val oldFile = File(ctx.cacheDir, "old_download_${System.currentTimeMillis()}.jpg")
        oldFile.writeText("sample bytes")
        assertTrue(oldFile.exists())

        val newFile = File(ctx.cacheDir, "new_download_${System.currentTimeMillis()}.jpg")
        newFile.writeText("new sample bytes")
        assertTrue(newFile.exists())

        val sourceUrl = "https://example.com/asset.jpg"
        val initialItem = MediaInboxItem(
            id = "item_v1",
            title = "asset.jpg",
            localUri = oldFile.absolutePath,
            sourceUrl = sourceUrl,
            isVideo = false
        )

        repo.addItemToStack(
            stackId = "stack_rep_1",
            item = initialItem,
            stackTitle = "Test Stack",
            sourceDomain = "example.com",
            sourceUrl = "https://example.com/post"
        )

        val updatedItem = MediaInboxItem(
            id = "item_v2",
            title = "asset.jpg",
            localUri = newFile.absolutePath,
            sourceUrl = sourceUrl,
            isVideo = false
        )

        repo.addItemToStack(
            stackId = "stack_rep_1",
            item = updatedItem,
            stackTitle = "Test Stack",
            sourceDomain = "example.com",
            sourceUrl = "https://example.com/post"
        )

        assertFalse("Replaced file should be deleted from disk", oldFile.exists())
        assertTrue("New file should still exist", newFile.exists())

        newFile.delete()
    }
}
