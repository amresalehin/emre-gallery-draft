package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.MediaDownloadWorker
import com.amresalehin.emregallery.data.OkHttpDownloader
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for WorkManager download keys, interrupted socket transfer rejection,
 * and non-raster audio/video format acceptance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OkHttpDownloaderValidationAndWorkerTest {

    @Test
    fun testWorkManagerDownloadWorkerConstants() {
        assertEquals("media_download", MediaDownloadWorker.WORK_TAG_DOWNLOAD)
        assertEquals("media_url", MediaDownloadWorker.KEY_MEDIA_URL)
        assertEquals("file_name", MediaDownloadWorker.KEY_FILE_NAME)
        assertEquals("stack_id", MediaDownloadWorker.KEY_STACK_ID)
        assertEquals("progress", MediaDownloadWorker.KEY_PROGRESS)
    }

    @Test
    fun testMockWebServerInterruptedTransferRejected() = OkHttpMockServerTestSupport.withServer { server, context ->
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setBody(Buffer().write(ByteArray(15) { 0x33.toByte() }))
                .setHeader("Content-Length", "50")
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END)
        )

        val url = server.url("/video.mp4").toString()
        val result = OkHttpDownloader.downloadMediaFileDetailed(context, url, "video.mp4")
        assertFalse(result.isSuccessful)
        assertNull(result.file)
        assertTrue(
            result.errorMessage?.contains("interrupted") == true ||
                result.errorMessage?.contains("incomplete") == true ||
                result.errorMessage?.contains("size mismatch") == true ||
                result.errorMessage?.contains("stream") == true ||
                result.errorMessage?.contains("unexpected") == true ||
                result.errorMessage?.contains("timeout") == true
        )
    }

    @Test
    fun testSupportedNonRasterFormatsValidation() = OkHttpMockServerTestSupport.withServer { server, context ->
        val nonRasterFormats = listOf(
            "audio/aac" to "song.aac",
            "audio/flac" to "music.flac",
            "audio/opus" to "voice.opus",
            "audio/x-ms-wma" to "audio.wma",
            "video/x-msvideo" to "movie.avi",
            "video/x-flv" to "video.flv",
            "video/3gpp" to "clip.3gp",
            "video/mp2t" to "segment.ts"
        )

        for ((mime, name) in nonRasterFormats) {
            val dummyBytes = ByteArray(32) { (it + 1).toByte() }
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", mime)
                    .setHeader("Content-Length", dummyBytes.size.toString())
                    .setBody(Buffer().write(dummyBytes))
            )
            val url = server.url("/$name").toString()
            val result = OkHttpDownloader.downloadMediaFileDetailed(context, url, name)
            assertTrue("Format $name should succeed without BitmapFactory failure", result.isSuccessful)
            assertNotNull(result.file)
            assertEquals(32L, result.file?.length())
        }
    }
}
