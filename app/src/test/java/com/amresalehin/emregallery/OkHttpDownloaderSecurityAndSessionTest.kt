package com.amresalehin.emregallery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amresalehin.emregallery.data.DataUriParser
import com.amresalehin.emregallery.data.MediaUrlValidator
import com.amresalehin.emregallery.data.OkHttpDownloader
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OkHttpDownloaderSecurityAndSessionTest {

    @Test
    fun testOkHttpSharedSessionCookieJar() {
        val cookieJar = OkHttpDownloader.SharedSessionCookieJar()
        val testUrl = "https://example.com/gallery"

        cookieJar.setManualCookie(testUrl, "session_token", "abc123xyz")
        cookieJar.setManualCookie(testUrl, "cf_clearance", "token987")

        val cookieString = cookieJar.getCookieString(testUrl)
        assertTrue(cookieString.contains("session_token=abc123xyz"))
        assertTrue(cookieString.contains("cf_clearance=token987"))

        val httpUrl = HttpUrl.Builder()
            .scheme("https")
            .host("example.com")
            .encodedPath("/gallery")
            .build()
        val loadedCookies = cookieJar.loadForRequest(httpUrl)
        assertEquals(2, loadedCookies.size)
        assertTrue(loadedCookies.any { it.name == "session_token" && it.value == "abc123xyz" })
        assertTrue(loadedCookies.any { it.name == "cf_clearance" && it.value == "token987" })

        cookieJar.clearSession()
        val afterClear = cookieJar.loadForRequest(httpUrl)
        assertEquals(0, afterClear.size)
    }

    @Test
    fun testOkHttpClientConfiguration() {
        val client = OkHttpDownloader.client
        assertTrue(client.followRedirects)
        assertTrue(client.followSslRedirects)
        assertEquals(15000, client.connectTimeoutMillis)
        assertEquals(30000, client.readTimeoutMillis)
        assertEquals(30000, client.writeTimeoutMillis)
        assertEquals(OkHttpDownloader.sharedCookieJar, client.cookieJar)
        assertTrue(client.interceptors.isNotEmpty())
    }

    @Test
    fun testMockWebServerRejectsHtmlMimeTypeForImages() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val context: Context = ApplicationProvider.getApplicationContext()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody("<!DOCTYPE html><html><body>Error page</body></html>")
            )
            val url = server.url("/test_img.jpg").toString()
            val result = OkHttpDownloader.downloadMediaFileDetailed(context, url, "test_img.jpg")
            assertFalse(result.isSuccessful)
            assertNull(result.file)
            assertTrue(result.errorMessage?.contains("webpage") == true || result.errorMessage?.contains("HTML") == true)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun testMockWebServerRejectsJsonErrorPagesEvenForManifest() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val context: Context = ApplicationProvider.getApplicationContext()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"error":"forbidden","message":"Invalid token"}""")
            )
            val url = server.url("/stream.m3u8").toString()
            val result = OkHttpDownloader.downloadMediaFileDetailed(context, url, "stream.m3u8")
            assertFalse(result.isSuccessful)
            assertNull(result.file)
            assertTrue(
                result.errorMessage?.contains("error") == true ||
                    result.errorMessage?.contains("JSON") == true ||
                    result.errorMessage?.contains("json") == true
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun testMockWebServerHtmlDisguisedAsJpgRejected() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val context: Context = ApplicationProvider.getApplicationContext()
            val htmlBody = "<!DOCTYPE html><html><head><title>Wikipedia</title></head><body>File details page</body></html>"
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "image/jpeg")
                    .setBody(htmlBody)
            )

            val url = server.url("/File:Taj_Mahal.jpg").toString()
            val result = OkHttpDownloader.downloadMediaFileDetailed(context, url, "File_Taj_Mahal.jpg")
            assertFalse("HTML content disguised as JPEG must be rejected", result.isSuccessful)
            assertNull("No file must be committed for HTML content", result.file)
            assertTrue(
                result.errorMessage?.contains("HTML webpage") == true ||
                    result.errorMessage?.contains("corrupt or invalid") == true
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun testCloudflareAndAntiBotChallengePayloadRejection() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val context: Context = ApplicationProvider.getApplicationContext()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html; charset=UTF-8")
                    .setBody(
                        """
                        <!DOCTYPE html>
                        <html>
                        <head><title>Just a moment...</title></head>
                        <body>
                            <h2>DDoS protection by Cloudflare</h2>
                            <p>Ray ID: 7abc123456</p>
                        </body>
                        </html>
                        """.trimIndent()
                    )
            )

            val url = server.url("/protected_video.mp4").toString()
            val result = OkHttpDownloader.downloadMediaFileDetailed(context, url, "protected_video.mp4")
            assertFalse(result.isSuccessful)
            assertNull(result.file)
            assertTrue(result.errorMessage?.contains("webpage") == true || result.errorMessage?.contains("HTML") == true)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun testBase64InlineDataUriSupport() = runBlocking {
        val tinySpacer = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"
        assertFalse(DataUriParser.isSubstantialDataUri(tinySpacer))
        assertFalse(MediaUrlValidator.isValidMedia(tinySpacer))

        val dummyData = "A".repeat(2500)
        val largeDataUri = "data:image/png;base64,$dummyData"
        assertTrue(DataUriParser.isSubstantialDataUri(largeDataUri))
        assertTrue(MediaUrlValidator.isValidMedia(largeDataUri))

        val parsedInfo = DataUriParser.parse(largeDataUri)
        assertNotNull(parsedInfo)
        assertEquals("image/png", parsedInfo?.mimeType)
        assertEquals("PNG", parsedInfo?.format)

        val context: Context = ApplicationProvider.getApplicationContext()
        val validPngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        val validDataUri = "data:image/png;base64,$validPngBase64"
        val res = OkHttpDownloader.downloadMediaFileDetailed(context, validDataUri, "embedded_canvas.png")
        assertTrue(res.isSuccessful)
        assertNotNull(res.file)
        assertTrue(res.file?.exists() == true)
        assertEquals("image/png", res.mimeType)
    }
}
