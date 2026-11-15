package com.amresalehin.emregallery.data

import java.util.Locale

/**
 * Merges and deduplicates discovered [ScrapedMediaItem]s across concurrent extraction engines,
 * folding video poster images into their parent video entries and upgrading lower-quality variants.
 */
internal object DiscoveredMediaMerger {

    fun mergeProgressively(
        itemsMap: LinkedHashMap<String, ScrapedMediaItem>,
        newList: List<ScrapedMediaItem>
    ): Boolean {
        var modified = false

        for (item in newList) {
            val key = MediaDeduplicator.deduplicationKey(item.url)
            if (item.isVideo) {
                if (mergeIncomingVideo(itemsMap, key, item)) {
                    modified = true
                }
                continue
            }

            val videoThumbKeys = collectVideoThumbKeys(itemsMap.values)
            if (key in videoThumbKeys) {
                if (foldImageIntoMatchingVideos(itemsMap, key, item)) {
                    modified = true
                }
                continue
            }

            val existing = itemsMap[key]
            if (existing == null) {
                itemsMap[key] = item
                modified = true
            } else {
                val upgraded = mergeImageVariants(existing = existing, incoming = item, mergeResolutionHint = true)
                if (upgraded != existing) {
                    itemsMap[key] = upgraded
                    modified = true
                }
            }
        }

        return modified
    }

    fun mergeBrowserBatch(
        existingItems: List<ScrapedMediaItem>,
        newList: List<ScrapedMediaItem>
    ): List<ScrapedMediaItem>? {
        val existingMap = LinkedHashMap<String, ScrapedMediaItem>()
        for (item in existingItems) {
            existingMap[MediaDeduplicator.deduplicationKey(item.url)] = item
        }
        var modified = false
        for (item in newList) {
            val key = MediaDeduplicator.deduplicationKey(item.url)
            val existing = existingMap[key]
            if (existing == null) {
                existingMap[key] = item
                modified = true
            } else {
                val upgraded = mergeImageVariants(existing = existing, incoming = item, mergeResolutionHint = false)
                if (upgraded != existing) {
                    existingMap[key] = upgraded
                    modified = true
                }
            }
        }
        return if (modified) existingMap.values.toList() else null
    }

    private fun mergeIncomingVideo(
        itemsMap: LinkedHashMap<String, ScrapedMediaItem>,
        key: String,
        item: ScrapedMediaItem
    ): Boolean {
        var modified = false
        val ytId = extractYouTubeId(item)
        val videoPosterKeys = mutableSetOf<String>()
        item.thumbnailUrl?.let { videoPosterKeys.add(MediaDeduplicator.deduplicationKey(it)) }
        if (ytId != null) videoPosterKeys.add("ytthumb:${ytId.lowercase(Locale.ROOT)}")

        var bestThumb = item.thumbnailUrl
        for (pKey in videoPosterKeys) {
            val existingImg = itemsMap[pKey]
            if (existingImg != null && !existingImg.isVideo) {
                if (bestThumb == null || MediaQualityEvaluator.compareQuality(existingImg, item.copy(url = bestThumb)) > 0) {
                    bestThumb = existingImg.thumbnailUrl ?: existingImg.url
                }
                itemsMap.remove(pKey)
                modified = true
            }
        }

        val candidateVideo = if (bestThumb != item.thumbnailUrl) item.copy(thumbnailUrl = bestThumb) else item
        val existing = itemsMap[key]
        if (existing == null) {
            itemsMap[key] = candidateVideo
            modified = true
        } else {
            val cmp = MediaQualityEvaluator.compareQuality(candidateVideo, existing)
            val preferred = if (cmp > 0) candidateVideo else existing
            val secondary = if (preferred === candidateVideo) existing else candidateVideo
            val upgraded = preferred.copy(
                id = existing.id,
                thumbnailUrl = preferred.thumbnailUrl ?: secondary.thumbnailUrl
            )
            if (upgraded != existing) {
                itemsMap[key] = upgraded
                modified = true
            }
        }
        return modified
    }

    private fun collectVideoThumbKeys(entries: Collection<ScrapedMediaItem>): Set<String> {
        val keys = mutableSetOf<String>()
        for (entry in entries) {
            if (entry.isVideo) {
                entry.thumbnailUrl?.let { keys.add(MediaDeduplicator.deduplicationKey(it)) }
                val ytId = extractYouTubeId(entry)
                if (ytId != null) {
                    keys.add("ytthumb:${ytId.lowercase(Locale.ROOT)}")
                }
            }
        }
        return keys
    }

    private fun foldImageIntoMatchingVideos(
        itemsMap: LinkedHashMap<String, ScrapedMediaItem>,
        imageKey: String,
        imageItem: ScrapedMediaItem
    ): Boolean {
        var modified = false
        for ((vKey, vItem) in itemsMap.entries.toList()) {
            if (vItem.isVideo) {
                val ytId = extractYouTubeId(vItem)
                val matches = (vItem.thumbnailUrl != null && MediaDeduplicator.deduplicationKey(vItem.thumbnailUrl) == imageKey) ||
                    (ytId != null && "ytthumb:${ytId.lowercase(Locale.ROOT)}" == imageKey)
                if (matches && (vItem.thumbnailUrl == null ||
                        MediaQualityEvaluator.compareQuality(imageItem, vItem.copy(url = vItem.thumbnailUrl)) > 0)
                ) {
                    itemsMap[vKey] = vItem.copy(thumbnailUrl = imageItem.thumbnailUrl ?: imageItem.url)
                    modified = true
                }
            }
        }
        return modified
    }

    private fun mergeImageVariants(
        existing: ScrapedMediaItem,
        incoming: ScrapedMediaItem,
        mergeResolutionHint: Boolean
    ): ScrapedMediaItem {
        val cmp = MediaQualityEvaluator.compareQuality(incoming, existing)
        val preferred = if (cmp > 0) incoming else existing
        val secondary = if (preferred === incoming) existing else incoming

        val engines = listOf(existing.provenance.extractorEngine, incoming.provenance.extractorEngine).distinct()
        val mergedEngine = engines.joinToString(" + ")
        val mergedThumb = preferred.thumbnailUrl ?: secondary.thumbnailUrl
        val mergedHeaders = secondary.downloadHeaders + preferred.downloadHeaders
        val resolvedUrl = OriginalMediaResolver.resolve(preferred.url).mediaUrl

        val mergedResolution = if (mergeResolutionHint) {
            when {
                MediaQualityEvaluator.parsePixelArea(preferred) != null -> preferred.resolutionHint ?: secondary.resolutionHint
                MediaQualityEvaluator.parsePixelArea(secondary) != null -> secondary.resolutionHint ?: preferred.resolutionHint
                !preferred.resolutionHint.isNullOrBlank() && preferred.resolutionHint != "Network" -> preferred.resolutionHint
                !secondary.resolutionHint.isNullOrBlank() && secondary.resolutionHint != "Network" -> secondary.resolutionHint
                else -> preferred.resolutionHint ?: secondary.resolutionHint
            }
        } else {
            preferred.resolutionHint
        }

        return preferred.copy(
            id = existing.id,
            url = resolvedUrl,
            thumbnailUrl = mergedThumb,
            resolutionHint = mergedResolution,
            provenance = preferred.provenance.copy(extractorEngine = mergedEngine),
            downloadHeaders = mergedHeaders
        )
    }

    private fun extractYouTubeId(item: ScrapedMediaItem): String? {
        return WebUrlUtils.extractYouTubeVideoId(item.url)
            ?: item.thumbnailUrl?.let { WebUrlUtils.extractYouTubeVideoId(it) }
            ?: WebUrlUtils.extractYouTubeVideoId(item.provenance.pageUrl)
    }
}
