package com.amresalehin.emregallery.data

import java.net.URI
import java.util.Locale

/** Pairs CMAF audio/video tracks from the same page and CDN media directory. */
internal object CmafTrackAssociation {
    const val AUDIO_TRACK_ID_METADATA = "cmaf_audio_track_id"
    const val AUDIO_TRACK_URL_METADATA = "cmaf_audio_track_url"
    const val AUDIO_TRACK_TITLE_METADATA = "cmaf_audio_track_title"
    const val AUDIO_TRACK_MIME_METADATA = "cmaf_audio_track_mime"
    const val REQUIRES_AUDIO_METADATA = "requires_audio_track"

    fun requiresAudio(item: ScrapedMediaItem): Boolean =
        item.metadata[REQUIRES_AUDIO_METADATA].equals("true", ignoreCase = true)

    fun companionAudioUrl(item: ScrapedMediaItem): String? =
        item.metadata[AUDIO_TRACK_URL_METADATA]?.takeIf { it.isNotBlank() }

    private fun name(item: ScrapedMediaItem): String {
        val path = runCatching { URI(item.url).path }.getOrNull().orEmpty()
        return "${item.title} ${path.substringAfterLast('/')}".lowercase(Locale.ROOT)
    }

    private fun tokens(item: ScrapedMediaItem): Set<String> =
        Regex("[a-z0-9]+").findAll(name(item)).map { it.value }.toSet()

    private fun isCmafAudio(item: ScrapedMediaItem): Boolean {
        val parts = tokens(item)
        return (item.isAudio || MediaClassifier.hasAudioOnlyTrackHint(item.url)) &&
            "cmaf" in parts && "audio" in parts
    }

    private fun isCmafVideo(item: ScrapedMediaItem): Boolean =
        item.isVideo && "cmaf" in tokens(item)

    private fun pageKey(item: ScrapedMediaItem): String? {
        val source = item.provenance.pageUrl.takeIf { it.isNotBlank() }
            ?: item.metadata["source_url"]?.takeIf { it.isNotBlank() }
            ?: return null
        return MediaClassifier.sanitizeUrl(source).trimEnd('/').takeIf { it.isNotBlank() }
    }

    /**
     * A source permalink can contain multiple separate videos. A track group must
     * share both that permalink and the CDN host/directory for one media resource.
     */
    private fun trackGroupKey(item: ScrapedMediaItem): String? {
        val page = pageKey(item) ?: return null
        val uri = runCatching { URI(item.url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
        val path = uri.path?.takeIf { it.isNotBlank() } ?: return null
        val directory = path.substringBeforeLast('/', "").trimEnd('/')
        if (directory.isBlank()) return null
        return "$page|$host|$directory"
    }

    private fun audioBitrate(item: ScrapedMediaItem): Int {
        val text = name(item).substringAfter("audio", name(item))
        return Regex("""(?:^|[^0-9])([0-9]{2,4})(?:kbps|k)?(?:[^0-9]|$)""")
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun videoHeight(item: ScrapedMediaItem): Int =
        Regex("""(?:^|[^0-9])([0-9]{3,4})(?:p|[^0-9]|$)""")
            .find(name(item))?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

    private fun bestAudio(items: List<ScrapedMediaItem>): ScrapedMediaItem =
        items.maxWithOrNull(compareBy<ScrapedMediaItem> { audioBitrate(it) }.thenBy { it.sizeBytes ?: 0L })!!

    private fun bestVideo(items: List<ScrapedMediaItem>): ScrapedMediaItem {
        var best = items.first()
        for (candidate in items.drop(1)) {
            val candidateHeight = videoHeight(candidate)
            val bestHeight = videoHeight(best)
            if (candidateHeight > bestHeight ||
                (candidateHeight == bestHeight && MediaClassifier.compareMediaQuality(candidate, best) > 0)
            ) best = candidate
        }
        return best
    }

    /**
     * A page may expose several renditions for one clip. Pair the best audio track
     * with the best video rendition for each CDN directory, and retain only that
     * single logical item. Separate CDN directories remain separate videos.
     */
    fun associate(items: List<ScrapedMediaItem>): List<ScrapedMediaItem> {
        if (items.size < 2) return items
        val groups = items.indices.mapNotNull { index ->
            trackGroupKey(items[index])?.let { it to index }
        }.groupBy({ it.first }, { it.second })
        val removed = mutableSetOf<Int>()
        val replacements = mutableMapOf<Int, ScrapedMediaItem>()

        for (indices in groups.values) {
            val audioIndices = indices.filter { isCmafAudio(items[it]) }
            val videoIndices = indices.filter { isCmafVideo(items[it]) }
            if (audioIndices.isEmpty() || videoIndices.isEmpty()) continue

            val audio = bestAudio(audioIndices.map { items[it] })
            val selectedVideo = bestVideo(videoIndices.map { items[it] })
            val selectedVideoIndex = videoIndices.first {
                items[it].id == selectedVideo.id && items[it].url == selectedVideo.url
            }
            val metadata = LinkedHashMap(selectedVideo.metadata).apply {
                put(AUDIO_TRACK_ID_METADATA, audio.id)
                put(AUDIO_TRACK_URL_METADATA, audio.url)
                put(AUDIO_TRACK_TITLE_METADATA, audio.title)
                put(AUDIO_TRACK_MIME_METADATA, audio.mimeType)
                put(REQUIRES_AUDIO_METADATA, "true")
            }
            replacements[selectedVideoIndex] = selectedVideo.copy(metadata = metadata)
            removed.addAll(audioIndices)
            removed.addAll(videoIndices.filter { it != selectedVideoIndex })
        }

        return items.indices.mapNotNull { index ->
            if (index in removed) null else replacements[index] ?: items[index]
        }
    }

    /** Transfer a selected standalone-audio card to its now-associated video item. */
    fun remapSelectedAudioIds(
        selectedIds: Set<String>,
        associatedItems: List<ScrapedMediaItem>
    ): Set<String> {
        if (selectedIds.isEmpty()) return selectedIds
        val byAudioId = associatedItems
            .filter(::requiresAudio)
            .mapNotNull { item ->
                item.metadata[AUDIO_TRACK_ID_METADATA]?.takeIf { it.isNotBlank() }?.let { it to item }
            }
            .groupBy({ it.first }, { it.second })

        val remapped = selectedIds.toMutableSet()
        for ((audioId, videos) in byAudioId) {
            if (audioId in selectedIds) {
                remapped.remove(audioId)
                remapped.add(bestVideo(videos).id)
            }
        }
        return remapped
    }
}
