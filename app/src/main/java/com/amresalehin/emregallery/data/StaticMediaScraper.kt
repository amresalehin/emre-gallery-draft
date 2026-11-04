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
        val isValid = MediaClassifier.isValidMedia(normalizedUrl)
        val classification = MediaClassifier.classify(normalizedUrl)
        val isDirectMedia = isValid && (classification.isVideo || classification.isAudio || classification.isStream ||
                normalizedUrl.endsWith(".jpg", true) || normalizedUrl.endsWith(".jpeg", true) ||
                normalizedUrl.endsWith(".png", true) || normalizedUrl.endsWith(".webp", true) ||
                normalizedUrl.endsWith(".gif", true) || normalizedUrl.endsWith(".svg", true))

        if (isDirectMedia) {
            val resolvedInfo = MediaClassifier.resolveOriginalMedia(normalizedUrl)
            val downloadUrl = resolvedInfo.mediaUrl
            val targetClass = MediaClassifier.classify(downloadUrl)
            val fileName = resolvedInfo.cleanTitle ?: MediaClassifier.deriveFileName(downloadUrl, targetClass.format, 0)
            val thumb = resolvedInfo.thumbnailUrl ?: if (targetClass.category == MediaCategory.IMAGES) downloadUrl else null
            val item = ScrapedMediaItem(
                id = "static_direct_0",
                url = downloadUrl,
                title = fileName,
                isVideo = targetClass.isVideo,
                isAudio = targetClass.isAudio,
                isStream = targetClass.isStream,
                category = targetClass.category,
                format = targetClass.format,
                thumbnailUrl = thumb,
                mimeType = targetClass.mimeType,
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

            // Resolve full-resolution media if URL is a known thumbnail (e.g. Wikimedia / MediaWiki)
            val resolvedInfo = MediaClassifier.resolveOriginalMedia(resolved)
            val downloadUrl = resolvedInfo.mediaUrl
            val classification = MediaClassifier.classify(downloadUrl)

            // Ensure thumbnail URL is always a valid image URL and never null for images
            val thumb = resolvedInfo.thumbnailUrl ?: posterUrl ?: if (classification.category == MediaCategory.IMAGES) resolved else posterUrl

            val key = MediaClassifier.canonicalKey(downloadUrl)
            if (itemsMap.containsKey(key)) return

            val title = resolvedInfo.cleanTitle ?: MediaClassifier.deriveFileName(downloadUrl, classification.format, itemsMap.size)

            val item = ScrapedMediaItem(
                id = "static_${itemsMap.size}",
                url = downloadUrl,
                title = title,
                isVideo = classification.isVideo,
                isAudio = classification.isAudio,
                isStream = classification.isStream,
                category = classification.category,
                format = classification.format,
                thumbnailUrl = thumb,
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

        // 2. Video elements & posters (deduplicated per video element)
        for (video in doc.select("video")) {
            val poster = video.attr("poster").ifBlank { null }
            val vw = video.attr("width").toIntOrNull()
            val vh = video.attr("height").toIntOrNull()
            val vRes = if (vw != null && vh != null) "${vw}x${vh}" else null

            val rawSrc = video.attr("src").ifBlank { video.attr("data-src") }
            var chosenUrl: String? = rawSrc.takeIf { it.isNotBlank() && !it.startsWith("blob:", true) && !it.startsWith("data:", true) }
            if (chosenUrl == null) {
                val sources = video.select("source")
                for (source in sources) {
                    val sSrc = source.attr("src").ifBlank { source.attr("data-src") }
                    if (sSrc.isNotBlank() && !sSrc.startsWith("blob:", true) && !sSrc.startsWith("data:", true)) {
                        chosenUrl = sSrc
                        break
                    }
                }
            }
            if (!chosenUrl.isNullOrBlank()) {
                addCandidate(chosenUrl, ExtractionSource.DOM_SCRAPE, resolution = vRes, posterUrl = poster)
            }
            if (!poster.isNullOrBlank() && chosenUrl == null) {
                addCandidate(poster, ExtractionSource.DOM_SCRAPE, resolution = "Poster")
            }
        }

        // 3. Audio elements & audio sources
        for (audio in doc.select("audio")) {
            val rawSrc = audio.attr("src").ifBlank { audio.attr("data-src") }
            var chosenAudio: String? = rawSrc.takeIf { it.isNotBlank() && !it.startsWith("blob:", true) && !it.startsWith("data:", true) }
            if (chosenAudio == null) {
                val sources = audio.select("source")
                for (source in sources) {
                    val sSrc = source.attr("src").ifBlank { source.attr("data-src") }
                    if (sSrc.isNotBlank() && !sSrc.startsWith("blob:", true) && !sSrc.startsWith("data:", true)) {
                        chosenAudio = sSrc
                        break
                    }
                }
            }
            if (!chosenAudio.isNullOrBlank()) {
                addCandidate(chosenAudio, ExtractionSource.DOM_SCRAPE)
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
            val href = link.attr("href").trim()
            if (href.isBlank()) continue
            val resolvedHref = MediaClassifier.resolveUrl(href, baseUrl) ?: continue
            if (MediaClassifier.isValidMedia(resolvedHref)) {
                val lower = resolvedHref.lowercase()
                val cleanPath = lower.substringBefore("?").substringBefore("#")
                if (cleanPath.endsWith(".jpg") || cleanPath.endsWith(".jpeg") || cleanPath.endsWith(".png") ||
                    cleanPath.endsWith(".webp") || cleanPath.endsWith(".gif") || cleanPath.endsWith(".mp4") ||
                    cleanPath.endsWith(".webm") || cleanPath.endsWith(".mkv") || cleanPath.endsWith(".mp3")
                ) {
                    val childImg = link.selectFirst("img")
                    val childThumb = childImg?.attr("src")?.ifBlank { childImg.attr("data-src") }
                    val poster = if (!childThumb.isNullOrBlank()) MediaClassifier.resolveUrl(childThumb, baseUrl) else null
                    addCandidate(resolvedHref, ExtractionSource.DOM_SCRAPE, resolution = "Full Link", posterUrl = poster)
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
