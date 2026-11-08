package com.amresalehin.emregallery.data

import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

internal fun MediaClassifier.mergeMediaItemsImpl(
    targetMap: MutableMap<String, ScrapedMediaItem>,
    incoming: List<ScrapedMediaItem>
): Boolean {
    if (incoming.isEmpty()) return false
    var modified = false

    fun getVideoThumbKeys(): Set<String> {
        val keys = mutableSetOf<String>()
        for (entry in targetMap.values) {
            if (entry.isVideo) {
                entry.thumbnailUrl?.let { keys.add(deduplicationKey(it)) }
                val ytId = extractYouTubeVideoId(entry.url)
                    ?: entry.thumbnailUrl?.let { extractYouTubeVideoId(it) }
                    ?: extractYouTubeVideoId(entry.provenance.pageUrl)
                    ?: entry.metadata["source_url"]?.let { extractYouTubeVideoId(it) }
                    ?: entry.metadata["webpage_url"]?.let { extractYouTubeVideoId(it) }
                if (ytId != null) {
                    keys.add("ytthumb:${ytId.lowercase(Locale.ROOT)}")
                }
            }
        }
        return keys
    }

    for (item in incoming) {
        val key = deduplicationKey(item.url)

        // Case A: Incoming item is a Video -> fold existing matching poster image into thumbnail
        if (item.isVideo) {
            val ytId = extractYouTubeVideoId(item.url)
                ?: item.thumbnailUrl?.let { extractYouTubeVideoId(it) }
                ?: extractYouTubeVideoId(item.provenance.pageUrl)
                ?: item.metadata["source_url"]?.let { extractYouTubeVideoId(it) }
                ?: item.metadata["webpage_url"]?.let { extractYouTubeVideoId(it) }
            val videoPosterKeys = mutableSetOf<String>()
            item.thumbnailUrl?.let { videoPosterKeys.add(deduplicationKey(it)) }
            if (ytId != null) videoPosterKeys.add("ytthumb:${ytId.lowercase(Locale.ROOT)}")

            var bestThumb = item.thumbnailUrl
            for (pKey in videoPosterKeys) {
                val existingImg = targetMap[pKey]
                if (existingImg != null && !existingImg.isVideo) {
                    if (bestThumb == null || compareMediaQuality(existingImg, item.copy(url = bestThumb)) > 0) {
                        bestThumb = existingImg.thumbnailUrl ?: existingImg.url
                    }
                    targetMap.remove(pKey)
                    modified = true
                }
            }
            val candidateVideo = if (bestThumb != item.thumbnailUrl) item.copy(thumbnailUrl = bestThumb) else item
            val existing = targetMap[key]
            if (existing == null) {
                targetMap[key] = candidateVideo
                modified = true
            } else {
                val cmp = compareMediaQuality(candidateVideo, existing)
                val preferred = if (cmp > 0) candidateVideo else existing
                val secondary = if (preferred === candidateVideo) existing else candidateVideo
                val upgraded = preferred.copy(
                    id = existing.id,
                    thumbnailUrl = preferred.thumbnailUrl ?: secondary.thumbnailUrl
                )
                if (upgraded != existing) {
                    targetMap[key] = upgraded
                    modified = true
                }
            }
            continue
        }

        // Case B: Incoming item is an Image -> check if it matches an existing video's thumbnail/poster
        val currentVideoThumbKeys = getVideoThumbKeys()
        if (key in currentVideoThumbKeys) {
            for ((vKey, vItem) in targetMap.entries.toList()) {
                if (vItem.isVideo) {
                    val ytId = extractYouTubeVideoId(vItem.url)
                        ?: vItem.thumbnailUrl?.let { extractYouTubeVideoId(it) }
                        ?: vItem.provenance.pageUrl.let { extractYouTubeVideoId(it) }
                        ?: vItem.metadata["source_url"]?.let { extractYouTubeVideoId(it) }
                        ?: vItem.metadata["webpage_url"]?.let { extractYouTubeVideoId(it) }
                    val matches = (vItem.thumbnailUrl != null && deduplicationKey(vItem.thumbnailUrl) == key) ||
                            (ytId != null && "ytthumb:${ytId.lowercase(Locale.ROOT)}" == key)
                    if (matches) {
                        if (vItem.thumbnailUrl == null || compareMediaQuality(item, vItem.copy(url = vItem.thumbnailUrl)) > 0) {
                            targetMap[vKey] = vItem.copy(thumbnailUrl = item.thumbnailUrl ?: item.url)
                            modified = true
                        }
                    }
                }
            }
            continue
        }

        // Case C: Standard media insertion / quality upgrade
        val existing = targetMap[key]
        if (existing == null) {
            targetMap[key] = item
            modified = true
        } else {
            val cmp = compareMediaQuality(item, existing)
            val preferred = if (cmp > 0) item else existing
            val secondary = if (preferred === item) existing else item

            val engines = listOf(existing.provenance.extractorEngine, item.provenance.extractorEngine).distinct()
            val mergedEngine = engines.joinToString(" + ")
            val mergedThumb = preferred.thumbnailUrl ?: secondary.thumbnailUrl
            val mergedResolution = when {
                parsePixelArea(preferred) != null -> preferred.resolutionHint ?: secondary.resolutionHint
                parsePixelArea(secondary) != null -> secondary.resolutionHint ?: preferred.resolutionHint
                !preferred.resolutionHint.isNullOrBlank() && preferred.resolutionHint != "Network" -> preferred.resolutionHint
                !secondary.resolutionHint.isNullOrBlank() && secondary.resolutionHint != "Network" -> secondary.resolutionHint
                else -> preferred.resolutionHint ?: secondary.resolutionHint
            }
            val mergedHeaders = secondary.downloadHeaders + preferred.downloadHeaders
            val resolvedUrl = resolveOriginalMedia(preferred.url).mediaUrl

            val upgraded = preferred.copy(
                id = existing.id,
                url = resolvedUrl,
                thumbnailUrl = mergedThumb,
                resolutionHint = mergedResolution,
                provenance = preferred.provenance.copy(extractorEngine = mergedEngine),
                downloadHeaders = mergedHeaders
            )
            if (upgraded != existing) {
                targetMap[key] = upgraded
                modified = true
            }
        }
    }
    return modified
}

/**
 * Deduplicates a list of scraped media items discovered by one or more extraction engines.
 * Retains the highest-quality master file, folds video posters, combines provenance,
 * and preserves required download headers.
 */
internal fun MediaClassifier.deduplicateScrapedItemsImpl(items: List<ScrapedMediaItem>): List<ScrapedMediaItem> {
    if (items.isEmpty()) return emptyList()
    val map = LinkedHashMap<String, ScrapedMediaItem>()
    mergeMediaItems(map, items)
    return map.values.toList()
}

/**
 * Extracts an 11-character YouTube video ID from a watch/shorts/embed URL or thumbnail CDN URL.
 */
internal fun MediaClassifier.extractYouTubeVideoIdImpl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val regex = Regex("""(?:youtu\.be/|youtube\.com/(?:embed/|v/|shorts/|watch\?v=|watch\?.+&v=))([a-zA-Z0-9_-]{11})""")
    val m = regex.find(url)
    if (m != null) return m.groupValues[1]
    val thumbRegex = Regex("""(?:ytimg\.com|youtube\.com)/(?:vi|vi_webp)/([a-zA-Z0-9_-]{11})""")
    return thumbRegex.find(url)?.groupValues?.get(1)
}

/**
 * Checks if a media item is an SVG vector asset based on format, MIME type, or URL extension.
