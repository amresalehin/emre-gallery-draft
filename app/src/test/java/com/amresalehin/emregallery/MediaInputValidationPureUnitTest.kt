package com.amresalehin.emregallery

import com.amresalehin.emregallery.data.DataUriParser
import com.amresalehin.emregallery.data.MediaRepository
import com.amresalehin.emregallery.data.MediaUrlValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM validation tests for non-media files, inline SVG placeholders,
 * and non-media tracking/embed endpoints.
 */
class MediaInputValidationPureUnitTest {

    @Test
    fun testMediaRepositoryRejectsXmlAndNonMediaFiles() {
        val nonMediaSamples = listOf(
            "AndroidManifest.xml" to "text/xml",
            "strings.xml" to "application/xml",
            "document.pdf" to "application/pdf",
            "archive.zip" to "application/zip",
            "notes.txt" to "text/plain",
            "payload.json" to "application/json",
            "app.apk" to "application/vnd.android.package-archive",
            "script.js" to "text/javascript",
            "style.css" to "text/css",
            ".nomedia" to null
        )

        for ((name, mime) in nonMediaSamples) {
            assertFalse(
                "Expected $name ($mime) to be rejected by isSupportedMedia",
                MediaRepository.isSupportedMedia(name, mime, isVideo = false)
            )
            assertFalse(
                "Expected $name ($mime) to be rejected as video by isSupportedMedia",
                MediaRepository.isSupportedMedia(name, mime, isVideo = true)
            )
        }

        val validMediaSamples = listOf(
            "photo.jpg" to "image/jpeg",
            "picture.png" to "image/png",
            "graphic.webp" to "image/webp",
            "camera.heic" to "image/heif",
            "video.mp4" to "video/mp4",
            "clip.webm" to "video/webm"
        )

        for ((name, mime) in validMediaSamples) {
            val isVid = name.endsWith(".mp4") || name.endsWith(".webm")
            assertTrue(
                "Expected $name ($mime) to be accepted by isSupportedMedia",
                MediaRepository.isSupportedMedia(name, mime, isVideo = isVid)
            )
        }
    }

    @Test
    fun testMediaClassifierRejectsSvgXmlDataUriPlaceholders() {
        val svgXmlDataUri =
            "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMDAiIGhlaWdodD0iMTY4IiB2aWV3Qm94PSIwIDAgMzAwIDE2OCI+PHJlY3Qgd2lkdGg9IjEwMCUiIGhlaWdodD0iMTAwJSIgc3R5bGU9ImZpbGw6I2NmZDRkYjtmaWxsLW9wYWNpdHk6IDAuMTsiLz48L3N2Zz4="
        assertFalse(
            "Expected SVG XML placeholder data URI to be rejected",
            DataUriParser.isSubstantialDataUri(svgXmlDataUri)
        )
        assertFalse(
            "Expected SVG XML placeholder data URI to be invalid media",
            MediaUrlValidator.isValidMedia(svgXmlDataUri)
        )
    }

    @Test
    fun testNonMediaEndpointsAndIframeWebPagesAreRejected() {
        val rejectedUrls = listOf(
            "https://www.youtube.com/embed/FaEeQPmv1D4",
            "https://www.youtube.com/shorts/FaEeQPmv1D4",
            "https://www.youtube.com/watch?v=FaEeQPmv1D4",
            "https://www.youtube.com/api/stats/playback?ns=yt&cpn=123",
            "https://play.google.com/log?format=json&has_webp=1",
            "https://i.ytimg.com/generate_204",
            "https://accounts.google.com/ServiceLogin?service=youtube",
            "https://example.com/embed/player_widget",
            "https://example.com/tracking/pixel"
        )

        for (url in rejectedUrls) {
            assertFalse(
                "Expected $url to be rejected by isValidMedia",
                MediaUrlValidator.isValidMedia(url)
            )
        }
    }
}
