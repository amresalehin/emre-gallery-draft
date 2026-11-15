package com.amresalehin.emregallery.data

import java.net.URI
import java.util.Locale

/** Pairs CMAF audio/video tracks from one original page into a single logical download item. */
internal object CmafTrackAssociation {
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

    private fun audioBitrate(item: ScrapedMediaItem): Int {
        val text = name(item).substringAfter("audio", name(item))
        return Regex("""(?:^|[^0-9])(\d{2,4})(?:kbps|k)?(?:[^0-9]|$)""")
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun videoHeight(item: ScrapedMediaItem): Int =
        Regex("""(?:^|[^0-9])(\d{3,4})(?:p|[^0-9]|$)""")
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

    /** Pair only same-page CMAF tracks; unrelated standalone audio remains selectable. */
    fun associate(items: List<ScrapedMediaItem>): List<ScrapedMediaItem> {
        if (items.size < 2) return items
        val groups = items.indices.mapNotNull { index -> pageKey(items[index])?.let { it to index } }
            .groupBy({ it.first }, { it.second })
        val removed = mutableSetOf<Int>()
        val replacements = mutableMapOf<Int, ScrapedMediaItem>()
        for (indices in groups.values) {
            val audioIndices = indices.filter { isCmafAudio(items[it]) }
            val videoIndices = indices.filter { isCmafVideo(items[it]) }
            if (audioIndices.isEmpty() || videoIndices.isEmpty()) continue

            val audio = bestAudio(audioIndices.map { items[it] })
            val video = bestVideo(videoIndices.map { items[it] })
            val videoIndex = videoIndices.first { items[it].id == video.id && items[it].url == video.url }
            val metadata = LinkedHashMap(video.metadata).apply {
                put(AUDIO_TRACK_URL_METADATA, audio.url)
                put(AUDIO_TRACK_TITLE_METADATA, audio.title)
                put(AUDIO_TRACK_MIME_METADATA, audio.mimeType)
                put(REQUIRES_AUDIO_METADATA, "true")
            }
            replacements[videoIndex] = video.copy(metadata = metadata)
            removed.addAll(audioIndices)
            removed.addAll(videoIndices.filter { it != videoIndex })
        }
        return items.indices.mapNotNull { index ->
            if (index in removed) null else replacements[index] ?: items[index]
        }
    }
}
