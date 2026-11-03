package com.amresalehin.emregallery.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * High-speed static HTML / DOM extractor.
 * Scans metadata, tags, CSS backgrounds, srcset, and iframes before live browser execution.
 */
class StaticMediaScraper {

    suspend fun scrape(inputUrl: String): StaticScrapeResult = withContext(Dispatchers.IO) {
        val trimmed = inputUrl.trim()
        if (trimmed.isBlank()) {
            return@withContext StaticScrapeResult("", "", "", emptyList(), "URL cannot be empty")
        }

        val normalizedUrl = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }

        val domain = runCatching { URL(normalizedUrl).host }.getOrDefault("")

        // Check if the input is a direct media file (e.g. video, stream, audio, image)
        val classification = MediaClassifier.classify(normalizedUrl)
        val isDirectMedia = classification.isVideo || classification.isAudio || classification.isStream ||
                normalizedUrl.endsWith(".jpg", true) || normalizedUrl.endsWith(".jpeg", true) ||
                normalizedUrl.endsWith(".png", true) || normalizedUrl.endsWith(".webp", true) ||
                normalizedUrl.endsWith(".gif", true) || normalizedUrl.endsWith(".svg", true)

        if (isDirectMedia) {
            val fileName = MediaClassifier.deriveFileName(normalizedUrl, classification.format, 0)
            val item = ScrapedMediaItem(
                id = "static_direct_0",
                url = normalizedUrl,
                title = fileName,
                isVideo = classification.isVideo,
                isAudio = classification.isAudio,
                isStream = classification.isStream,
                category = classification.category,
                format = classification.format,
                mimeType = classification.mimeType,
                source = ExtractionSource.STATIC_HTML.label
            )
            return@withContext StaticScrapeResult(
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
                setRequestProperty(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/*;q=0.8,video/*;q=0.8,audio/*;q=0.8,*/*;q=0.7"
                )
            }

            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext StaticScrapeResult(
                    sourceUrl = normalizedUrl,
                    pageTitle = domain,
                    domain = domain,
                    items = emptyList(),
                    errorMessage = "Server returned HTTP status $code"
                )
            }

            val contentType = conn.contentType?.lowercase() ?: ""
            if (contentType.startsWith("image/") || contentType.startsWith("video/") ||
                contentType.startsWith("audio/") || contentType.contains("mpegurl") || contentType.contains("dash")
            ) {
                val directClass = MediaClassifier.classify(normalizedUrl, contentType)
                val fileName = MediaClassifier.deriveFileName(normalizedUrl, directClass.format, 0)
                val item = ScrapedMediaItem(
                    id = "static_direct_0",
                    url = normalizedUrl,
                    title = fileName,
                    isVideo = directClass.isVideo,
                    isAudio = directClass.isAudio,
                    isStream = directClass.isStream,
                    category = directClass.category,
                    format = directClass.format,
                    mimeType = directClass.mimeType,
                    source = ExtractionSource.STATIC_HTML.label
                )
                return@withContext StaticScrapeResult(
                    sourceUrl = normalizedUrl,
                    pageTitle = fileName,
                    domain = domain,
                    items = listOf(item)
                )
            }

            // Read HTML response (limit to 1MB)
            val inputStream: InputStream = conn.inputStream
            val bytes = ByteArray(1024 * 1024)
            var totalRead = 0
            while (totalRead < bytes.size) {
                val read = inputStream.read(bytes, totalRead, bytes.size - totalRead)
                if (read == -1) break
                totalRead += read
            }
            val html = String(bytes, 0, totalRead, Charsets.UTF_8)
            conn.disconnect()

            val pageTitle = extractTitle(html).ifBlank { domain }
            val extractedItems = parseHtmlMedia(html, normalizedUrl)

            StaticScrapeResult(
                sourceUrl = normalizedUrl,
                pageTitle = pageTitle,
                domain = domain,
                items = extractedItems
            )
        } catch (e: Exception) {
            StaticScrapeResult(
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

    private fun parseHtmlMedia(html: String, baseUrl: String): List<ScrapedMediaItem> {
        val itemsMap = LinkedHashMap<String, ScrapedMediaItem>()

        fun addCandidate(rawUrl: String, source: ExtractionSource, resolution: String? = null, posterUrl: String? = null) {
            val resolved = MediaClassifier.resolveUrl(rawUrl, baseUrl) ?: return
            if (!MediaClassifier.isValidMedia(resolved)) return

            val key = MediaClassifier.canonicalKey(resolved)
            if (itemsMap.containsKey(key)) return

            val classification = MediaClassifier.classify(resolved)
            val title = MediaClassifier.deriveFileName(resolved, classification.format, itemsMap.size)

            val item = ScrapedMediaItem(
                id = "static_${itemsMap.size}",
                url = resolved,
                title = title,
                isVideo = classification.isVideo,
                isAudio = classification.isAudio,
                isStream = classification.isStream,
                category = classification.category,
                format = classification.format,
                thumbnailUrl = if (classification.category == MediaCategory.IMAGES) resolved else posterUrl,
                source = source.label,
                mimeType = classification.mimeType,
                resolutionHint = resolution
            )
            itemsMap[key] = item
        }

        // 1. OpenGraph & Twitter tags
        val metaPatterns = listOf(
            Pattern.compile("<meta[^>]+property=[\"']og:video[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:video[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta[^>]+name=[\"']twitter:image[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta[^>]+name=[\"']twitter:player:stream[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        )
        for (p in metaPatterns) {
            val m = p.matcher(html)
            while (m.find()) {
                m.group(1)?.let { addCandidate(it, ExtractionSource.STATIC_HTML, "OpenGraph") }
            }
        }

        // 2. Video & Audio tags with poster & source
        val videoPattern = Pattern.compile("<video([^>]*)>", Pattern.CASE_INSENSITIVE)
        val vm = videoPattern.matcher(html)
        while (vm.find()) {
            val attrs = vm.group(1) ?: ""
            val srcMatch = Pattern.compile("src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(attrs)
            val posterMatch = Pattern.compile("poster=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(attrs)
            val poster = if (posterMatch.find()) posterMatch.group(1)?.let { MediaClassifier.resolveUrl(it, baseUrl) } else null

            if (srcMatch.find()) {
                srcMatch.group(1)?.let { addCandidate(it, ExtractionSource.DOM_SCRAPE, posterUrl = poster) }
            }
            if (poster != null) {
                addCandidate(poster, ExtractionSource.DOM_SCRAPE, resolution = "Poster")
            }
        }

        // Audio & Source tags
        val audioOrSourcePattern = Pattern.compile("<(?:source|audio)[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val asm = audioOrSourcePattern.matcher(html)
        while (asm.find()) {
            asm.group(1)?.let { addCandidate(it, ExtractionSource.DOM_SCRAPE) }
        }

        // 3. Img tags (src, data-src, data-original)
        val imgPattern = Pattern.compile("<img[^>]+(?:src|data-src|data-original)=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val im = imgPattern.matcher(html)
        while (im.find()) {
            im.group(1)?.let { addCandidate(it, ExtractionSource.DOM_SCRAPE) }
        }

        // 4. Srcset parsing (<picture><source srcset="..."> and <img srcset="...">)
        val srcsetPattern = Pattern.compile("(?:srcset|data-srcset)=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val ssm = srcsetPattern.matcher(html)
        while (ssm.find()) {
            val rawSrcset = ssm.group(1) ?: continue
            val entries = MediaClassifier.parseSrcset(rawSrcset, baseUrl)
            for ((url, res) in entries) {
                addCandidate(url, ExtractionSource.SRCSET, resolution = res.ifBlank { "srcset" })
            }
        }

        // 5. CSS background-image extraction (inline style and embedded style tags)
        val cssBgPattern = Pattern.compile("background(?:-image)?\\s*:\\s*url\\([\"']?([^\"')]+)[\"']?\\)", Pattern.CASE_INSENSITIVE)
        val cm = cssBgPattern.matcher(html)
        while (cm.find()) {
            cm.group(1)?.let { addCandidate(it, ExtractionSource.CSS_BACKGROUND) }
        }

        // 6. iFrame inspection (look for video embed frames)
        val iframePattern = Pattern.compile("<iframe[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val ifm = iframePattern.matcher(html)
        while (ifm.find()) {
            ifm.group(1)?.let { addCandidate(it, ExtractionSource.IFRAME, resolution = "iFrame Embed") }
        }

        return itemsMap.values.toList()
    }
}

data class StaticScrapeResult(
    val sourceUrl: String,
    val pageTitle: String,
    val domain: String,
    val items: List<ScrapedMediaItem>,
    val errorMessage: String? = null
)
