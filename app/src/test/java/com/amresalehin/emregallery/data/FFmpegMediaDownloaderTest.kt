package com.amresalehin.emregallery.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FFmpegMediaDownloaderTest {
    @Test fun detectsManifestUrlsAndQueries() {
        assertTrue(FFmpegMediaDownloader.isManifestUrl("https://media.example/master.m3u8"))
        assertTrue(FFmpegMediaDownloader.isManifestUrl("https://media.example/manifest.mpd?sig=abc"))
    }

    @Test fun rejectsNonManifestUrls() {
        assertFalse(FFmpegMediaDownloader.isManifestUrl("https://media.example/movie.mp4"))
        assertFalse(FFmpegMediaDownloader.isManifestUrl("https://media.example/page?next=.m3u8"))
    }
}
