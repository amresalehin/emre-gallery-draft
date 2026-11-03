package com.amresalehin.emregallery.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class ScrapedMediaItem(
    val id: String,
    val url: String,
    val title: String,
    val isVideo: Boolean,
    val isAudio: Boolean = false,
    val mimeType: String = when {
        isVideo -> "video/mp4"
        isAudio -> "audio/mpeg"
        else -> "image/jpeg"
    }
)

data class ScrapeResult(
    val sourceUrl: String,
    val pageTitle: String,
    val domain: String,
    val items: List<ScrapedMediaItem>,
    val errorMessage: String? = null
)

class MediaScraper {

    suspend fun scrape(inputUrl: String): ScrapeResult = withContext(Dispatchers.IO) {
        val trimmed = inputUrl.trim()
        if (trimmed.isBlank()) {
            return@withContext ScrapeResult("", "", "", emptyList(), "URL cannot be empty")
        }

        val normalizedUrl = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }

        val domain = runCatching { URL(normalizedUrl).host }.getOrDefault("")

        // Check if direct image, video or audio
        val lower = normalizedUrl.lowercase()
        val isDirectMedia = lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") ||
                lower.endsWith(".gif") || lower.endsWith(".mp4") ||
                lower.endsWith(".webm") || lower.endsWith(".mov") ||
                lower.endsWith(".mp3") || lower.endsWith(".wav") ||
                lower.endsWith(".m4a") || lower.endsWith(".ogg") ||
                lower.endsWith(".flac") || lower.endsWith(".aac")

        if (isDirectMedia) {
            val isVid = lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov")
            val isAud = lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a") ||
                    lower.endsWith(".ogg") || lower.endsWith(".flac") || lower.endsWith(".aac")
            val fileName = runCatching { Uri.parse(normalizedUrl).lastPathSegment ?: "media" }.getOrDefault("media")
            val item = ScrapedMediaItem(
                id = "scraped_0",
                url = normalizedUrl,
                title = fileName,
                isVideo = isVid,
                isAudio = isAud
            )
            return@withContext ScrapeResult(
                sourceUrl = normalizedUrl,
                pageTitle = fileName,
                domain = domain,
                items = listOf(item)
            )
        }

        try {
            val url = URL(normalizedUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/*;q=0.8,video/*;q=0.8,audio/*;q=0.8,*/*;q=0.7")
            }

            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext ScrapeResult(
                    sourceUrl = normalizedUrl,
                    pageTitle = domain,
                    domain = domain,
                    items = emptyList(),
                    errorMessage = "Server returned HTTP status $code"
                )
            }

            val contentType = conn.contentType?.lowercase() ?: ""
            if (contentType.startsWith("image/") || contentType.startsWith("video/") || contentType.startsWith("audio/")) {
                val isVid = contentType.startsWith("video/")
                val isAud = contentType.startsWith("audio/")
                val fileName = runCatching { Uri.parse(normalizedUrl).lastPathSegment ?: "media" }.getOrDefault("media")
                val item = ScrapedMediaItem(
                    id = "scraped_0",
                    url = normalizedUrl,
                    title = fileName,
                    isVideo = isVid,
                    isAudio = isAud
                )
                return@withContext ScrapeResult(
                    sourceUrl = normalizedUrl,
                    pageTitle = fileName,
                    domain = domain,
                    items = listOf(item)
                )
            }

            // Read HTML response (limit to 600KB)
            val inputStream: InputStream = conn.inputStream
            val bytes = ByteArray(600 * 1024)
            var totalRead = 0
            while (totalRead < bytes.size) {
                val read = inputStream.read(bytes, totalRead, bytes.size - totalRead)
                if (read == -1) break
                totalRead += read
            }
            val html = String(bytes, 0, totalRead, Charsets.UTF_8)
            conn.disconnect()

            val pageTitle = extractTitle(html).ifBlank { domain }
            val mediaUrls = extractMediaUrls(html, url)

            if (mediaUrls.isEmpty()) {
                return@withContext ScrapeResult(
                    sourceUrl = normalizedUrl,
                    pageTitle = pageTitle,
                    domain = domain,
                    items = emptyList(),
                    errorMessage = "No downloadable media found on this page"
                )
            }

            val items = mediaUrls.mapIndexed { index, mediaUrl ->
                val mLower = mediaUrl.lowercase()
                val isVid = mLower.endsWith(".mp4") || mLower.endsWith(".webm") || mLower.endsWith(".mov") || mLower.contains("video")
                val isAud = mLower.endsWith(".mp3") || mLower.endsWith(".wav") || mLower.endsWith(".m4a") ||
                        mLower.endsWith(".ogg") || mLower.endsWith(".flac") || mLower.endsWith(".aac") || mLower.contains("audio")
                val ext = when {
                    isVid -> "mp4"
                    isAud -> "mp3"
                    else -> "jpg"
                }
                val name = runCatching {
                    val segment = Uri.parse(mediaUrl).lastPathSegment ?: "Media_${index + 1}"
                    if (segment.contains(".")) segment else "$segment.$ext"
                }.getOrDefault("Media_${index + 1}")

                ScrapedMediaItem(
                    id = "scraped_$index",
                    url = mediaUrl,
                    title = name,
                    isVideo = isVid,
                    isAudio = isAud
                )
            }

            ScrapeResult(
                sourceUrl = normalizedUrl,
                pageTitle = pageTitle,
                domain = domain,
                items = items
            )
        } catch (e: Exception) {
            ScrapeResult(
                sourceUrl = normalizedUrl,
                pageTitle = domain,
                domain = domain,
                items = emptyList(),
                errorMessage = e.localizedMessage ?: "Failed to connect to page"
            )
        }
    }

    private fun extractTitle(html: String): String {
        val matcher = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
        return if (matcher.find()) {
            matcher.group(1)?.replace(Regex("\\s+"), " ")?.trim() ?: ""
        } else {
            ""
        }
    }

    private fun extractMediaUrls(html: String, baseUrl: URL): List<String> {
        val found = LinkedHashSet<String>()

        // 1. OpenGraph & Twitter tags (highest quality)
        val metaPatterns = listOf(
            Pattern.compile("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta[^>]+property=[\"']og:video[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta[^>]+name=[\"']twitter:image[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in metaPatterns) {
            val m = pattern.matcher(html)
            while (m.find()) {
                m.group(1)?.let { raw ->
                    resolveUrl(raw, baseUrl)?.let { resolved ->
                        if (isValidMediaUrl(resolved)) found.add(resolved)
                    }
                }
            }
        }

        // 2. Video & Audio tags & Source tags
        val mediaTagPatterns = listOf(
            Pattern.compile("<video[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<audio[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<source[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in mediaTagPatterns) {
            val m = pattern.matcher(html)
            while (m.find()) {
                m.group(1)?.let { raw ->
                    resolveUrl(raw, baseUrl)?.let { resolved ->
                        if (isValidMediaUrl(resolved)) found.add(resolved)
                    }
                }
            }
        }

        // 3. Img tags (src and data-src)
        val imgPattern = Pattern.compile("<img[^>]+(?:src|data-src)=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val imgMatcher = imgPattern.matcher(html)
        while (imgMatcher.find()) {
            imgMatcher.group(1)?.let { raw ->
                resolveUrl(raw, baseUrl)?.let { resolved ->
                    if (isValidMediaUrl(resolved)) found.add(resolved)
                }
            }
        }

        return found.take(30)
    }

    private fun resolveUrl(raw: String, baseUrl: URL): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("data:") || trimmed.startsWith("javascript:")) {
            return null
        }
        return try {
            URL(baseUrl, trimmed).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun isValidMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        // Filter out common tracking pixels and decorative icons
        if (lower.contains("1x1") || lower.contains("pixel") || lower.contains("favicon.ico") || lower.contains("analytics")) {
            return false
        }
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    suspend fun downloadMediaFile(context: Context, mediaUrl: String, fileName: String): File? = withContext(Dispatchers.IO) {
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()

            val sanitizedName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(dir, "${System.currentTimeMillis()}_$sanitizedName")

            val url = URL(mediaUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 25000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
            }

            conn.connect()
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                targetFile
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
