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
    @Test fun keepsDifferentVideoRenditionsAsSeparateChoices() {
        val low = MediaClassifier.deduplicationKey("https://cdn.example/movie_720p.mp4")
        val high = MediaClassifier.deduplicationKey("https://cdn.example/movie_1080p.mp4")
        assertFalse("720p and 1080p must not collapse into one item", low == high)
    }

    @Test fun preservesQualityAndItagQueryParametersForVideoUrls() {
        val sd = MediaClassifier.deduplicationKey("https://cdn.example/movie.mp4?quality=480p&token=abc")
        val hd = MediaClassifier.deduplicationKey("https://cdn.example/movie.mp4?quality=1080p&token=abc")
        val itag18 = MediaClassifier.deduplicationKey("https://cdn.example/movie.mp4?itag=18&token=abc")
        val itag22 = MediaClassifier.deduplicationKey("https://cdn.example/movie.mp4?itag=22&token=abc")
        assertFalse(sd == hd)
        assertFalse(itag18 == itag22)
    }

    @Test fun extractsQualityLabelsFromCommonVideoUrlPatterns() {
        assertTrue(MediaClassifier.extractVideoQualityLabel("https://cdn.example/movie_1080p.mp4") == "1080p")
        assertTrue(MediaClassifier.extractVideoQualityLabel("https://cdn.example/master.m3u8?quality=720p") == "720p")
        assertTrue(MediaClassifier.extractVideoQualityLabel("https://cdn.example/movie.mp4?height=480") == "480p")
    }

}
