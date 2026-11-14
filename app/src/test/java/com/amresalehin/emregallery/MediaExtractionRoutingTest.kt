package com.amresalehin.emregallery

import androidx.work.ExistingWorkPolicy
import com.amresalehin.emregallery.data.MediaDownloadStrategy
import com.amresalehin.emregallery.data.MediaProviderRouting
import com.amresalehin.emregallery.data.DownloadQueueManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaExtractionRoutingTest {
    @Test
    fun platformRoutingMatchesParsedHostAndRealSubdomainsOnly() {
        assertTrue(MediaProviderRouting.isYtDlpPageUrl("https://www.youtube.com/watch?v=abc"))
        assertTrue(MediaProviderRouting.isYtDlpPageUrl("https://m.bandcamp.com/track/example"))
        assertFalse(MediaProviderRouting.isYtDlpPageUrl("https://youtube.com.example.org/watch?v=abc"))
        assertFalse(MediaProviderRouting.isYtDlpPageUrl("https://notyoutube.com/watch?v=abc"))
        assertFalse(MediaProviderRouting.isYtDlpPageUrl("https://example.org/?next=youtube.com"))
    }

    @Test
    fun manifestRoutingUsesPathRatherThanQueryText() {
        assertTrue(MediaProviderRouting.isManifestUrl("https://cdn.example/video.m3u8?token=1"))
        assertTrue(MediaProviderRouting.isManifestUrl("https://cdn.example/manifest.mpd#quality"))
        assertFalse(MediaProviderRouting.isManifestUrl("https://example.org/watch?format=.m3u8"))
    }

    @Test
    fun downloadStrategySurvivesWireParsingAndMerge() {
        assertEquals(MediaDownloadStrategy.YTDLP_PAGE, MediaDownloadStrategy.fromWireValue("ytdlp_page"))
        assertEquals(MediaDownloadStrategy.AUTO, MediaDownloadStrategy.fromWireValue("unknown"))
        assertEquals(
            MediaDownloadStrategy.YTDLP_PAGE,
            MediaDownloadStrategy.merge(MediaDownloadStrategy.AUTO, MediaDownloadStrategy.YTDLP_PAGE)
        )
        assertEquals(
            MediaDownloadStrategy.YTDLP_PAGE,
            MediaDownloadStrategy.merge(MediaDownloadStrategy.DIRECT_HTTP, MediaDownloadStrategy.YTDLP_PAGE)
        )
    }

    @Test
    fun duplicateDownloadRequestsAreAppendedAsTheirOwnWorkers() {
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, DownloadQueueManager.policyFor(isRetry = false))
        assertEquals(ExistingWorkPolicy.REPLACE, DownloadQueueManager.policyFor(isRetry = true))
    }
}
