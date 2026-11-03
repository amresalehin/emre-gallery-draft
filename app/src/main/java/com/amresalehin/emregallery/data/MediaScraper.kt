package com.amresalehin.emregallery.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ScrapedMediaItem(
    val id: String,
    val url: String,
    val title: String,
    val isVideo: Boolean,
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
     */
    suspend fun downloadMediaFile(context: Context, mediaUrl: String, fileName: String): File? = withContext(Dispatchers.IO) {
        try {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val emreGalleryDir = File(publicDownloads, "Emre Gallery")
            if (!emreGalleryDir.exists()) {
                emreGalleryDir.mkdirs()
            }
            val dir = if (emreGalleryDir.exists() && emreGalleryDir.canWrite()) {
                emreGalleryDir
            } else {
                val appExternal = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val fallbackFolder = File(appExternal ?: context.filesDir, "Emre Gallery")
                if (!fallbackFolder.exists()) fallbackFolder.mkdirs()
                fallbackFolder
            }

            val sanitizedName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(dir, "${System.currentTimeMillis()}_$sanitizedName")

            val url = URL(mediaUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Accept", "*/*")
            }

            conn.connect()
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                // Notify system media scanner so the downloaded media appears in device gallery
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    null,
                    null
                )
                targetFile
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
