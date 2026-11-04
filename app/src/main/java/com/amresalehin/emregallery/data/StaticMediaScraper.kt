package com.amresalehin.emregallery.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL

/**
 * High-speed static HTML / DOM extractor powered by OkHttp and Jsoup.
 * Reliably parses document metadata, OpenGraph tags, HTML5 video/audio/picture tags,
 * srcset, lazy-loaded attributes, CSS backgrounds, direct links, and iframes with shared session handling.
 */
class StaticMediaScraper {

    suspend fun scrape(inputUrl: String): StaticScrapeResult = withContext(Dispatchers.IO) {
        val trimmed = inputUrl.trim()
        if (trimmed.isBlank()) {
            return@withContext StaticScrapeResult("", "", "", emptyList(), "URL cannot be empty")
        }

        val normalizedUrl = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            "https://$trimmed"
        } else {
            trimmed
        }

        val domain = runCatching { URL(normalizedUrl).host }.getOrDefault("")

        // Check if the input is a direct media file
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
            val response = OkHttpDownloader.getHtml(normalizedUrl)
            if (!response.isSuccessful) {
                return@withContext StaticScrapeResult(
                    sourceUrl = normalizedUrl,
                    pageTitle = domain,
                    domain = domain,
                    items = emptyList(),
                    errorMessage = response.errorMessage ?: "Server returned HTTP status ${response.statusCode}"
                )
            }

            val contentType = response.contentType
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

            val html = response.bodyString ?: ""
            val targetBaseUrl = response.finalUrl.ifBlank { normalizedUrl }
            val doc = Jsoup.parse(html, targetBaseUrl)
            val pageTitle = doc.title().trim().ifBlank { domain }
            val extractedItems = parseDocumentMedia(doc, targetBaseUrl)

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
                errorMessage = e.message ?: "Failed to scrape page"
            )
        }
    }

    private fun parseDocumentMedia(doc: Document, baseUrl: String): List<ScrapedMediaItem> {
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

        // 1. OpenGraph, Twitter & schema.org meta tags
        val metaSelectors = listOf(
            "meta[property=og:video]", "meta[content][property=og:video]",
            "meta[property=og:image]", "meta[content][property=og:image]",
            "meta[name=twitter:image]", "meta[name=twitter:player:stream]",
            "meta[itemprop=image]", "meta[itemprop=video]"
        )
        for (sel in metaSelectors) {
            for (meta in doc.select(sel)) {
                val content = meta.attr("content").ifBlank { meta.attr("value") }
                if (content.isNotBlank()) {
                    addCandidate(content, ExtractionSource.STATIC_HTML, "OpenGraph")
                }
            }
        }

        // 2. Video elements & posters & source children
        for (video in doc.select("video")) {
            val src = video.attr("src").ifBlank { video.attr("data-src") }
            val poster = video.attr("poster").ifBlank { null }
            val vw = video.attr("width").toIntOrNull()
            val vh = video.attr("height").toIntOrNull()
            val vRes = if (vw != null && vh != null) "${vw}x${vh}" else null
            if (src.isNotBlank()) {
                addCandidate(src, ExtractionSource.DOM_SCRAPE, resolution = vRes, posterUrl = poster)
            }
            if (!poster.isNullOrBlank()) {
                addCandidate(poster, ExtractionSource.DOM_SCRAPE, resolution = "Poster")
            }
        }

        // 3. Audio elements & source elements
        for (source in doc.select("source, audio")) {
            val src = source.attr("src").ifBlank { source.attr("data-src") }
            if (src.isNotBlank()) {
                addCandidate(src, ExtractionSource.DOM_SCRAPE)
            }
        }

        // 4. Img elements with lazy-load support
        for (img in doc.select("img")) {
            val src = img.attr("src").ifBlank {
                img.attr("data-src").ifBlank {
                    img.attr("data-original").ifBlank {
                        img.attr("data-lazy-src").ifBlank {
                            img.attr("data-full-url")
                        }
                    }
                }
            }
            val w = img.attr("width").toIntOrNull() ?: img.attr("data-width").toIntOrNull()
            val h = img.attr("height").toIntOrNull() ?: img.attr("data-height").toIntOrNull()
            val resHint = if (w != null && h != null) "${w}x${h}" else if (w != null) "${w}w" else null
            if (src.isNotBlank()) {
                addCandidate(src, ExtractionSource.DOM_SCRAPE, resolution = resHint)
            }
        }

        // 5. Srcset parsing (img and picture source elements)
        for (elem in doc.select("[srcset], [data-srcset]")) {
            val rawSrcset = elem.attr("srcset").ifBlank { elem.attr("data-srcset") }
            val w = elem.attr("width").toIntOrNull() ?: elem.attr("data-width").toIntOrNull()
            val h = elem.attr("height").toIntOrNull() ?: elem.attr("data-height").toIntOrNull()
            if (rawSrcset.isNotBlank()) {
                val entries = MediaClassifier.parseSrcset(rawSrcset, baseUrl)
                for ((url, res) in entries) {
                    val pxMatch = Regex("""[/_-](\d{2,5})px[-_./]""").find(url)
                    val resHint = if (pxMatch != null) {
                        "${pxMatch.groupValues[1]}px"
                    } else if (res.isNotBlank() && res != "srcset") {
                        if (res.endsWith("x", true) && w != null) {
                            val mult = res.removeSuffix("x").removeSuffix("X").toFloatOrNull() ?: 1f
                            val scaledW = (w * mult).toInt()
                            val scaledH = if (h != null) (h * mult).toInt() else null
                            if (scaledH != null) "${scaledW}x${scaledH}" else "${scaledW}w"
                        } else {
                            res
                        }
                    } else if (w != null && h != null) {
                        "${w}x${h}"
                    } else if (w != null) {
                        "${w}w"
                    } else {
                        "srcset"
                    }
                    addCandidate(url, ExtractionSource.SRCSET, resolution = resHint)
                }
            }
        }

        // 6. Inline CSS background-image extraction
        for (styled in doc.select("[style*='url('], [style*='url(\"'], [style*='url(&quot;']")) {
            val style = styled.attr("style")
            val urlIndex = style.indexOf("url(", ignoreCase = true)
            if (urlIndex != -1) {
                val sub = style.substring(urlIndex + 4)
                val closeIndex = sub.indexOf(")")
                if (closeIndex != -1) {
                    val rawUrl = sub.substring(0, closeIndex)
                        .replace("\"", "")
                        .replace("'", "")
                        .replace("&quot;", "")
                        .trim()
                    if (rawUrl.isNotBlank()) {
                        addCandidate(rawUrl, ExtractionSource.CSS_BACKGROUND)
                    }
                }
            }
        }

        // 7. iFrame inspection (video embed frames)
        for (iframe in doc.select("iframe[src]")) {
            val src = iframe.attr("src")
            if (src.isNotBlank()) {
                addCandidate(src, ExtractionSource.IFRAME, resolution = "iFrame Embed")
            }
        }

        // 8. Direct anchor links to media files
        for (link in doc.select("a[href]")) {
            val href = link.attr("href")
            if (href.isNotBlank() && MediaClassifier.isValidMedia(href)) {
                val lower = href.lowercase()
                if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                    lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".mp4") ||
                    lower.endsWith(".webm") || lower.endsWith(".mkv") || lower.endsWith(".mp3")
                ) {
                    addCandidate(href, ExtractionSource.DOM_SCRAPE, resolution = "Full Link")
                }
            }
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
