package com.amresalehin.emregallery.data

import java.net.URI
import java.util.Locale

/**
 * Resolves raw extractor output into logical media assets before display or queuing.
 * Handles extractor IDs, known CDN rendition paths, and platform-specific original URLs.
 */
object MediaResolver {

    fun resolve(items: List<ScrapedMediaItem>): List<ScrapedMediaItem> {
        val groups = LinkedHashMap<String, MutableList<ScrapedMediaItem>>()
        for (item in items) {
            val key = "${item.category}:${identity(item)}"
            groups.getOrPut(key) { mutableListOf() }.add(item)
        }
        return groups.values.map(::mergeGroup)
    }

    fun identity(item: ScrapedMediaItem): String {
        item.canonicalMediaId?.takeIf { it.isNotBlank() }?.let { return "extractor:$it" }

        val uri = runCatching { URI(item.url) }.getOrNull()
        val host = uri?.host?.lowercase(Locale.ROOT).orEmpty()
        val path = uri?.path.orEmpty()
        if (host == "i.pinimg.com" || host.endsWith(".pinimg.com")) {
            val parts = path.trim('/').split('/')
            val renditionIndex = parts.indexOfFirst {
                it.equals("originals", true) || it.matches(Regex("""\d{2,5}x"""))
            }
            val canonicalPath = if (renditionIndex >= 0 && renditionIndex < parts.lastIndex) {
                parts.drop(renditionIndex + 1).joinToString("/")
            } else parts.joinToString("/")
            if (canonicalPath.isNotBlank()) return "pinterest:$canonicalPath"
        }
        return MediaClassifier.deduplicationKey(item.url)
    }

    private fun mergeGroup(group: List<ScrapedMediaItem>): ScrapedMediaItem {
        if (group.size == 1) {
            val single = group.first()
            val original = MediaClassifier.resolveOriginalMedia(single.url).mediaUrl
            return if (original != single.url) single.copy(url = original) else single
        }

        val best = group.maxByOrNull(::qualityScore) ?: group.first()
        val normalizedUrl = MediaClassifier.resolveOriginalMedia(best.url).mediaUrl
        val mergedHeaders = linkedMapOf<String, String>()
        val mergedMetadata = linkedMapOf<String, String>()
        group.forEach {
            mergedHeaders.putAll(it.downloadHeaders)
            mergedMetadata.putAll(it.metadata)
        }
        val engines = group.map { it.provenance.extractorEngine }.filter { it.isNotBlank() }.distinct()
        val audioUrl = group.firstNotNullOfOrNull { it.audioUrl }
        val knownAudio = group.any { it.hasAudio == true || !it.audioUrl.isNullOrBlank() }

        return best.copy(
            url = normalizedUrl,
            thumbnailUrl = group.firstNotNullOfOrNull { it.thumbnailUrl }
                ?: if (best.category == MediaCategory.IMAGES) normalizedUrl else null,
            resolutionHint = best.resolutionHint?.takeIf { it.isNotBlank() }
                ?: group.firstNotNullOfOrNull { it.resolutionHint?.takeIf { value -> value.isNotBlank() && value != "Network" } },
            audioUrl = audioUrl,
            hasAudio = when {
                knownAudio -> true
                group.any { it.hasAudio == false } -> false
                else -> best.hasAudio
            },
            canonicalMediaId = best.canonicalMediaId ?: group.firstNotNullOfOrNull { it.canonicalMediaId },
            width = best.width ?: group.maxOfOrNull { it.width ?: 0 }?.takeIf { it > 0 },
            height = best.height ?: group.maxOfOrNull { it.height ?: 0 }?.takeIf { it > 0 },
            sizeBytes = group.maxOfOrNull { it.sizeBytes ?: 0L }?.takeIf { it > 0L } ?: best.sizeBytes,
            provenance = best.provenance.copy(
                extractorEngine = engines.joinToString(" + ").ifBlank { best.provenance.extractorEngine }
            ),
            metadata = mergedMetadata,
            downloadHeaders = mergedHeaders
        )
    }

    private fun qualityScore(item: ScrapedMediaItem): Long {
        val parsed = dimensions(item)
        val width = (item.width ?: parsed.first ?: 0).toLong()
        val height = (item.height ?: parsed.second ?: 0).toLong()
        val area = if (height > 0L) width * height else width * width
        val bytes = item.sizeBytes ?: 0L
        val url = item.url.lowercase(Locale.ROOT)
        val pinterestOriginal = url.contains("/originals/")
        val genericOriginal = !url.contains("/thumb/") && !url.contains("/transcoded/") &&
            !Regex("""/(\d{2,5})x/""").containsMatchIn(url)
        val hasAudio = item.hasAudio == true || !item.audioUrl.isNullOrBlank()
        val fps = ((item.frameRate ?: item.metadata["fps"]?.toDoubleOrNull() ?: 0.0) * 1000).toLong()
        val bitrate = ((item.metadata["tbr"]?.toDoubleOrNull() ?: 0.0) * 1000).toLong()

        return when (item.category) {
            MediaCategory.IMAGES -> (if (pinterestOriginal) 1_000_000_000_000_000L else if (genericOriginal) 100_000_000_000_000L else 0L) +
                area * 100L + bytes.coerceAtMost(99_999_999L)
            MediaCategory.VIDEOS, MediaCategory.STREAMS ->
                (if (hasAudio) 10_000_000_000_000L else 0L) +
                    area * 100L + fps * 10L + bitrate + bytes.coerceAtMost(9_999_999L)
            MediaCategory.AUDIO -> bitrate * 100L + bytes.coerceAtMost(9_999_999L)
            else -> bytes
        }
    }

    private fun dimensions(item: ScrapedMediaItem): Pair<Int?, Int?> {
        Regex("""(\d{2,5})\s*[xX×]\s*(\d{2,5})""").find(item.resolutionHint.orEmpty())?.let {
            return it.groupValues[1].toIntOrNull() to it.groupValues[2].toIntOrNull()
        }
        val width = Regex("""/(\d{2,5})x/""").find(item.url)?.groupValues?.get(1)?.toIntOrNull()
        return width to null
    }
}
