package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.OkHttpDownloader
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for resumable HTTP Range/206 downloads, 200 full-restart fallbacks,
 * Content-Range mismatch recovery, and HTTP 416 handling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OkHttpDownloaderTransferTest {

    @Test
    fun testMockWebServerResumableRange206() = OkHttpMockServerTestSupport.withServer { server, context ->
        val downloadId = "test_resumable_video"
        OkHttpMockServerTestSupport.seedPartialDownloadingFile(
            context = context,
            downloadId = downloadId,
            fileName = "mp4",
            byteCount = 5,
            fillByte = 0x41.toByte()
        )

        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Range", "bytes 5-9/10")
                .setHeader("Content-Length", "5")
                .setBody(Buffer().write(ByteArray(5) { 0x42.toByte() }))
        )

        val url = server.url("/video.mp4").toString()
        val result = OkHttpDownloader.downloadMediaFileDetailed(
            context = context,
            mediaUrl = url,
            fileName = "video.mp4",
            downloadId = downloadId
        )

        val recorded = server.takeRequest()
        assertEquals("bytes=5-", recorded.getHeader("Range"))
        assertTrue(result.isSuccessful)
        assertTrue(result.isResumed)
        assertEquals(10L, result.bytesDownloaded)
        assertNotNull(result.file)
        assertEquals(10L, result.file?.length())
    }

    @Test
    fun testMockWebServerHttp200FallbackClearsPartialFile() = OkHttpMockServerTestSupport.withServer { server, context ->
        val downloadId = "test_fallback_video"
        OkHttpMockServerTestSupport.seedPartialDownloadingFile(
            context = context,
            downloadId = downloadId,
            fileName = "mp4",
            byteCount = 8,
            fillByte = 0x55.toByte()
        )

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setBody(Buffer().write(ByteArray(10) { 0x77.toByte() }))
                .setHeader("Content-Length", "10")
        )

        val url = server.url("/video.mp4").toString()
        val result = OkHttpDownloader.downloadMediaFileDetailed(
            context = context,
            mediaUrl = url,
            fileName = "video.mp4",
            downloadId = downloadId
        )

        assertTrue(result.isSuccessful)
        assertFalse(result.isResumed)
        assertEquals(10L, result.bytesDownloaded)
        assertNotNull(result.file)
        assertEquals(10L, result.file?.length())
    }

    @Test
    fun testMockWebServerHttp416SafeHandling() = OkHttpMockServerTestSupport.withServer { server, context ->
        val downloadId = "test_416_video"
        OkHttpMockServerTestSupport.seedPartialDownloadingFile(
            context = context,
            downloadId = downloadId,
            fileName = "mp4",
            byteCount = 50,
            fillByte = 0x11.toByte()
        )

        server.enqueue(
            MockResponse()
                .setResponseCode(416)
                .setHeader("Content-Range", "bytes */10")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setBody(Buffer().write(ByteArray(10) { 0x22.toByte() }))
                .setHeader("Content-Length", "10")
        )

        val url = server.url("/video.mp4").toString()
        val result = OkHttpDownloader.downloadMediaFileDetailed(
            context = context,
            mediaUrl = url,
            fileName = "video.mp4",
            downloadId = downloadId
        )

        assertTrue(result.isSuccessful)
        assertEquals(10L, result.bytesDownloaded)
        assertNotNull(result.file)
        assertEquals(10L, result.file?.length())
    }

    @Test
    fun testMockWebServerContentRangeMismatchRestartsCleanly() = OkHttpMockServerTestSupport.withServer { server, context ->
        val downloadId = "test_mismatch_resume"
        OkHttpMockServerTestSupport.seedPartialDownloadingFile(
            context = context,
            downloadId = downloadId,
            fileName = "mp4",
            byteCount = 10,
            fillByte = 0x11.toByte()
        )

        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Range", "bytes 0-19/20")
                .setBody(Buffer().write(ByteArray(20) { 0x22.toByte() }))
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setBody(Buffer().write(ByteArray(20) { 0x33.toByte() }))
                .setHeader("Content-Length", "20")
        )

        val url = server.url("/video.mp4").toString()
        val result = OkHttpDownloader.downloadMediaFileDetailed(
            context = context,
            mediaUrl = url,
            fileName = "video.mp4",
            downloadId = downloadId
        )

        assertTrue(result.isSuccessful)
        assertEquals(20L, result.bytesDownloaded)
        assertNotNull(result.file)
        assertEquals(20L, result.file?.length())
    }

    @Test
    fun testMockWebServerHttp416AlreadyCompleteCommitsFile() = OkHttpMockServerTestSupport.withServer { server, context ->
        val downloadId = "test_416_complete"
        OkHttpMockServerTestSupport.seedPartialDownloadingFile(
            context = context,
            downloadId = downloadId,
            fileName = "mp4",
            byteCount = 30,
            fillByte = 0x44.toByte()
        )

        server.enqueue(
            MockResponse()
                .setResponseCode(416)
                .setHeader("Content-Range", "bytes */30")
        )

        val url = server.url("/video.mp4").toString()
        val result = OkHttpDownloader.downloadMediaFileDetailed(
            context = context,
            mediaUrl = url,
            fileName = "video.mp4",
            downloadId = downloadId
        )

        assertTrue(result.isSuccessful)
        assertEquals(30L, result.bytesDownloaded)
        assertNotNull(result.file)
        assertEquals(30L, result.file?.length())
    }
}
