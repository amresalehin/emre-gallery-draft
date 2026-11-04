package com.amresalehin.emregallery.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class MediaProvenance(
    val extractorEngine: String = "Static HTML",
    val pageUrl: String = "",
    val domSelector: String? = null
)

data class ScrapedMediaItem(
    val id: String,
    val url: String,
    val title: String,
    val isVideo: Boolean = false,
    val isAudio: Boolean = false,
    val isStream: Boolean = false,
    val category: MediaCategory = when {
        isStream -> MediaCategory.STREAMS
        isVideo -> MediaCategory.VIDEOS
        isAudio -> MediaCategory.AUDIO
        else -> MediaCategory.IMAGES
    },
    val format: String = when {
        isStream -> "HLS/DASH"
        isVideo -> "MP4"
        isAudio -> "AUDIO"
        else -> "IMAGE"
    },
    val thumbnailUrl: String? = null,
    val source: String = "Static HTML",
    val mimeType: String = when {
        isStream -> "application/x-mpegURL"
        isVideo -> "video/mp4"
        isAudio -> "audio/mpeg"
        else -> "image/jpeg"
    },
    val resolutionHint: String? = null,
    val sizeBytes: Long? = null,
    val provenance: MediaProvenance = MediaProvenance(extractorEngine = source),
    val metadata: Map<String, String> = emptyMap(),
    val downloadHeaders: Map<String, String> = emptyMap(),
    val detectedAt: Long = System.currentTimeMillis()
)

data class ScrapeResult(
    val sourceUrl: String,
    val pageTitle: String,
    val domain: String,
    val items: List<ScrapedMediaItem>,
    val errorMessage: String? = null
)

/**
 * High-level scraping and media download service.
 */
class MediaScraper {

    private val staticScraper = StaticMediaScraper()

    suspend fun scrape(inputUrl: String): ScrapeResult = withContext(Dispatchers.IO) {
        val staticRes = staticScraper.scrape(inputUrl)
        ScrapeResult(
            sourceUrl = staticRes.sourceUrl,
            pageTitle = staticRes.pageTitle,
            domain = staticRes.domain,
            items = staticRes.items,
            errorMessage = staticRes.errorMessage
        )
    }

    /**
     * Downloads an image, video, audio file, or stream manifest (HLS/DASH) to main device storage.
     * Saved to: [Environment.DIRECTORY_DOWNLOADS]/Emre Gallery/ (main browser download path).
     * Leverages GalleryDlEngine when applicable, falling back to OkHttp with shared session cookies.
     */
    suspend fun downloadMediaFile(
        context: Context,
        mediaUrl: String,
        fileName: String,
        referer: String? = null
    ): File? = withContext(Dispatchers.IO) {
        // First attempt download via gallery-dl / yt-dlp engine
        val gdlFile = GalleryDlEngine.downloadMedia(context, mediaUrl, fileName, referer)
        if (gdlFile != null && gdlFile.exists() && gdlFile.length() > 0L) {
            return@withContext gdlFile
        }

        // High-performance streaming download via OkHttp with shared session cookies
        OkHttpDownloader.downloadMediaFile(
            context = context,
            mediaUrl = mediaUrl,
            fileName = fileName,
            referer = referer
        )
    }
}
