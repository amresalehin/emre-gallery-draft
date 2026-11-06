package com.amresalehin.emregallery.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL
import java.util.Locale

/**
 * Streamlined static HTML / DOM extractor powered by OkHttp, Jsoup, and MediaCaptureEngine.
 * Reliably parses document metadata, OpenGraph tags, HTML5 video/audio tags,
 * srcset, lazy-loaded attributes, container attributes, CSS backgrounds, direct links, and iframes.
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

        // Fast-path: Check if the input is already a direct media file
        if (MediaClassifier.isValidMedia(normalizedUrl)) {
            val classification = MediaClassifier.classify(normalizedUrl)
            val isDirect = classification.isVideo || classification.isAudio || classification.isStream ||
                    normalizedUrl.endsWith(".jpg", true) || normalizedUrl.endsWith(".jpeg", true) ||
                    normalizedUrl.endsWith(".png", true) || normalizedUrl.endsWith(".webp", true) ||
                    normalizedUrl.endsWith(".gif", true) || normalizedUrl.endsWith(".svg", true)

            if (isDirect) {
                val item = MediaCaptureEngine.buildMediaItem(
                    rawUrl = normalizedUrl,
                    source = ExtractionSource.STATIC_HTML,
                    baseUrl = normalizedUrl
                )
                if (item != null) {
                    return@withContext StaticScrapeResult(
                        sourceUrl = normalizedUrl,
                        pageTitle = item.title,
                        domain = domain,
                        items = listOf(item)
                    )
                }
            }
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
                val item = MediaCaptureEngine.buildMediaItem(
                    rawUrl = normalizedUrl,
                    source = ExtractionSource.STATIC_HTML,
                    baseUrl = normalizedUrl,
                    mimeHint = contentType
                )
                if (item != null) {
                    return@withContext StaticScrapeResult(
                        sourceUrl = normalizedUrl,
                        pageTitle = item.title,
                        domain = domain,
                        items = listOf(item)
                    )
                }
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

        fun addCandidate(
            rawUrl: String,
            source: ExtractionSource,
            resolution: String? = null,
            posterUrl: String? = null,
            domSelector: String? = null,
            declaredWidth: Int? = null,
            declaredHeight: Int? = null,
            itemTitleHint: String? = null
        ) {
            val item = MediaCaptureEngine.buildMediaItem(
                rawUrl = rawUrl,
                source = source,
                baseUrl = baseUrl,
                poster = posterUrl,
                resolutionHint = resolution,
                indexHint = itemsMap.size,
                titleHint = itemTitleHint,
                domSelector = domSelector,
                declaredWidth = declaredWidth,
                declaredHeight = declaredHeight
            ) ?: return

            MediaClassifier.mergeMediaItems(itemsMap, listOf(item))
        }

        // 1. OpenGraph, Twitter & Schema.org meta tags
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
                    addCandidate(content, ExtractionSource.STATIC_HTML, "OpenGraph", domSelector = sel)
                }
            }
        }

        // 2. Video elements & posters
        for (video in doc.select("video")) {
            val poster = video.attr("poster").ifBlank { null }
            val vw = video.attr("width").toIntOrNull()
            val vh = video.attr("height").toIntOrNull()
            val vRes = if (vw != null && vh != null) "${vw}x${vh}" else null

            val rawSrc = video.attr("src").ifBlank { video.attr("data-src") }
            val chosenUrl = rawSrc.takeIf { it.isNotBlank() && !it.startsWith("blob:", true) && !it.startsWith("data:", true) }
                ?: video.select("source").map { it.attr("src").ifBlank { it.attr("data-src") } }
                    .firstOrNull { it.isNotBlank() && !it.startsWith("blob:", true) && !it.startsWith("data:", true) }

            if (!chosenUrl.isNullOrBlank()) {
                addCandidate(chosenUrl, ExtractionSource.DOM_SCRAPE, resolution = vRes, posterUrl = poster, domSelector = "video")
            } else if (!poster.isNullOrBlank()) {
                addCandidate(poster, ExtractionSource.DOM_SCRAPE, resolution = "Poster", domSelector = "video[poster]")
            }
        }

        // 3. Audio elements
        for (audio in doc.select("audio")) {
            val rawSrc = audio.attr("src").ifBlank { audio.attr("data-src") }
            val chosenAudio = rawSrc.takeIf { it.isNotBlank() && !it.startsWith("blob:", true) && !it.startsWith("data:", true) }
                ?: audio.select("source").map { it.attr("src").ifBlank { it.attr("data-src") } }
                    .firstOrNull { it.isNotBlank() && !it.startsWith("blob:", true) && !it.startsWith("data:", true) }

            if (!chosenAudio.isNullOrBlank()) {
                addCandidate(chosenAudio, ExtractionSource.DOM_SCRAPE, domSelector = "audio")
            }
        }

        // 4. Img elements with lazy-load support
        val lazyAttrs = listOf(
            "data-src", "data-original", "data-lazy-src", "data-lazy",
            "data-full-url", "data-high-res-src", "data-hires", "data-large-file",
            "data-orig-file", "data-url", "data-actualsrc", "data-fallback-src",
            "data-main-thumb", "data-thumb", "data-poster", "src"
        )
        for (img in doc.select("img")) {
            if (ContentAwareMediaFilter.isUiChromeElement(img)) continue

            val chosenSrc = lazyAttrs.firstNotNullOfOrNull { attr ->
                img.attr(attr).trim().takeIf { it.isNotBlank() && !it.startsWith("data:", true) && !it.startsWith("about:", true) }
            }

            val w = img.attr("width").toIntOrNull() ?: img.attr("data-width").toIntOrNull()
            val h = img.attr("height").toIntOrNull() ?: img.attr("data-height").toIntOrNull()
            val alt = img.attr("alt").ifBlank { img.attr("title") }.takeIf { it.isNotBlank() }
            val resHint = if (w != null && h != null) "${w}x${h}" else if (w != null) "${w}w" else null

            if (!chosenSrc.isNullOrBlank()) {
                addCandidate(
                    rawUrl = chosenSrc,
                    source = ExtractionSource.DOM_SCRAPE,
                    resolution = resHint,
                    domSelector = "img",
                    declaredWidth = w,
                    declaredHeight = h,
                    itemTitleHint = alt
                )
            }
        }

        // 5. Container attributes (trailers, videos, posters)
        for (el in doc.select("[data-trailer], [data-main-thumb], [data-video], [data-mp4], [data-poster]")) {
            if (el.tagName().equals("video", true) || el.tagName().equals("img", true)) continue
            val trailer = el.attr("data-trailer").ifBlank { el.attr("data-video").ifBlank { el.attr("data-mp4") } }
            val thumb = el.attr("data-main-thumb").ifBlank { el.attr("data-thumb").ifBlank { el.attr("data-poster") } }
            val title = el.attr("title").ifBlank { el.selectFirst("a[title]")?.attr("title") ?: el.selectFirst(".title, h2, h3")?.text() }

            if (trailer.isNotBlank() && !trailer.startsWith("data:", true)) {
                addCandidate(
                    rawUrl = trailer,
                    source = ExtractionSource.DOM_SCRAPE,
                    posterUrl = thumb.takeIf { it.isNotBlank() && !it.startsWith("data:", true) },
                    domSelector = "container[trailer]",
                    itemTitleHint = title
                )
            }
            if (thumb.isNotBlank() && !thumb.startsWith("data:", true)) {
                addCandidate(
                    rawUrl = thumb,
                    source = ExtractionSource.DOM_SCRAPE,
                    domSelector = "container[thumb]",
                    itemTitleHint = title
                )
            }
        }

        // 6. Srcset parsing
        for (elem in doc.select("[srcset], [data-srcset]")) {
            if (ContentAwareMediaFilter.isUiChromeElement(elem)) continue
            val rawSrcset = elem.attr("srcset").ifBlank { elem.attr("data-srcset") }
            val w = elem.attr("width").toIntOrNull() ?: elem.attr("data-width").toIntOrNull()
            val h = elem.attr("height").toIntOrNull() ?: elem.attr("data-height").toIntOrNull()
            val alt = elem.attr("alt").ifBlank { elem.attr("title") }.takeIf { it.isNotBlank() }

            if (rawSrcset.isNotBlank()) {
                for ((url, res) in MediaClassifier.parseSrcset(rawSrcset, baseUrl)) {
                    addCandidate(
                        rawUrl = url,
                        source = ExtractionSource.SRCSET,
                        resolution = res.ifBlank { if (w != null && h != null) "${w}x${h}" else "srcset" },
                        domSelector = "[srcset]",
                        declaredWidth = w,
                        declaredHeight = h,
                        itemTitleHint = alt
                    )
                }
            }
        }

        // 7. Content CSS background-image
        for (styled in doc.select("[style*='url(']")) {
            if (!ContentAwareMediaFilter.isAcceptableCssBackground(styled)) continue
            val style = styled.attr("style")
            val match = Regex("""url\(['"]?(.*?)['"]?\)""", RegexOption.IGNORE_CASE).find(style)
            val bgUrl = match?.groupValues?.get(1)?.trim()
            if (!bgUrl.isNullOrBlank() && !bgUrl.startsWith("data:", true)) {
                addCandidate(bgUrl, ExtractionSource.CSS_BACKGROUND, domSelector = "style[background-image]")
            }
        }

        // 8. Direct media IFrames
        for (iframe in doc.select("iframe[src]")) {
            val src = iframe.attr("src").trim()
            if (src.isNotBlank() && !ContentAwareMediaFilter.isUiChromeElement(iframe)) {
                val cleanPath = src.substringBefore("?").substringBefore("#").lowercase(Locale.ROOT)
                if (MediaClassifier.KNOWN_MEDIA_EXTENSIONS.any { cleanPath.endsWith(".$it") }) {
                    addCandidate(src, ExtractionSource.IFRAME, resolution = "iFrame Media", domSelector = "iframe")
                }
            }
        }

        // 9. Direct anchor links
        for (link in doc.select("a[href]")) {
            if (ContentAwareMediaFilter.isUiChromeElement(link)) continue
            val href = link.attr("href").trim()
            if (href.isBlank()) continue
            val cleanPath = href.substringBefore("?").substringBefore("#").lowercase(Locale.ROOT)
            if (cleanPath.endsWith(".jpg") || cleanPath.endsWith(".jpeg") || cleanPath.endsWith(".png") ||
                cleanPath.endsWith(".webp") || cleanPath.endsWith(".gif") || cleanPath.endsWith(".mp4") ||
                cleanPath.endsWith(".webm") || cleanPath.endsWith(".mkv") || cleanPath.endsWith(".mp3")
            ) {
                val childThumb = link.selectFirst("img")?.let { it.attr("src").ifBlank { it.attr("data-src") } }
                addCandidate(
                    rawUrl = href,
                    source = ExtractionSource.DOM_SCRAPE,
                    resolution = "Full Link",
                    posterUrl = childThumb,
                    domSelector = "a[href]",
                    itemTitleHint = link.attr("title").ifBlank { link.text() }.takeIf { it.isNotBlank() }
                )
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
