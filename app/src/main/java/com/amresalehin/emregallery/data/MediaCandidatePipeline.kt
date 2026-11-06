package com.amresalehin.emregallery.data

import java.net.URI
import java.util.Locale

/**
 * The single policy boundary for candidates reported by static parsing, browser capture,
 * site extractors, and downloader sessions. Extractors discover candidates; this pipeline
 * decides whether they are worth showing and collapses equivalent variants consistently.
 */
object MediaCandidatePipeline {

    /** Returns false for transport fragments, ad/tracker endpoints, invalid URLs, and UI chrome. */
    fun isUsefulCandidate(item: ScrapedMediaItem): Boolean {
        val url = item.url.trim()
        if (url.isBlank() || MediaClassifier.isMediaFragment(url) || AdBlockEngine.isAdOrTracker(url)) {
            return false
        }

        // Classifier validation is deliberately relaxed for signed/opaque video and audio URLs:
        // specialist extractors can identify those from response metadata even without extensions.
        val explicitMime = item.metadata["mime_hint"] ?: item.metadata["content_type"]
        val hasMediaTypeEvidence = item.isVideo || item.isAudio || item.isStream ||
                (explicitMime?.startsWith("image/", ignoreCase = true) == true) ||
                (item.mimeType.startsWith("image/", ignoreCase = true) &&
                        !item.mimeType.equals("image/jpeg", ignoreCase = true))
        val confirmedResponseMime = explicitMime.orEmpty().lowercase(Locale.ROOT)
        val verifiedDirectMediaResponse = item.metadata["resolved_response_media"] == "true" &&
                (confirmedResponseMime.startsWith("image/") || confirmedResponseMime.startsWith("video/") ||
                        confirmedResponseMime.startsWith("audio/") || confirmedResponseMime.contains("mpegurl") ||
                        confirmedResponseMime.contains("dash+xml"))
        if (!MediaClassifier.isValidMedia(url) &&
            (!hasMediaTypeEvidence || (!isSafeOpaqueMediaUrl(url) && !verifiedDirectMediaResponse))
        ) return false

        val isNonVisualMedia = item.isVideo || item.isAudio || item.isStream
        if (!isNonVisualMedia && ContentAwareMediaFilter.isUiChromeUrl(
                url = url,
                title = item.title,
                isVideoOrAudioOrStream = false
            )
        ) {
            return false
        }
        return true
    }

    private fun isSafeOpaqueMediaUrl(url: String): Boolean {
        val normalized = if (url.startsWith("http://", true) || url.startsWith("https://", true)) url else return false
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return false
        if (uri.host.isNullOrBlank()) return false
        val lower = url.lowercase(Locale.ROOT)
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        val knownNonMedia = listOf(
            "google-analytics", "doubleclick", "pixel", "tracking", "beacon", "telemetry",
            "stats.", "/stats/", "/logging", "/log/", "/track", "/api/", "/apis/",
            "/youtubei/", "generate_204", "/recaptcha", "/embed/"
        )
        if (knownNonMedia.any(lower::contains)) return false
        val nonMediaSuffixes = setOf(".html", ".htm", ".php", ".js", ".css", ".json", ".xml", ".txt")
        return nonMediaSuffixes.none(path::endsWith)
    }

    /**
     * Adds newly discovered candidates to the current list, preserving stable IDs and using
     * the classifier's canonical resolution ranking and video-poster folding in one place.
     */
    fun merge(
        current: List<ScrapedMediaItem>,
        candidates: Iterable<ScrapedMediaItem>
    ): List<ScrapedMediaItem> {
        val acceptedCurrent = current.filter(::isUsefulCandidate)
        val accepted = candidates.filter(::isUsefulCandidate)
        if (accepted.isEmpty() && acceptedCurrent == current) return current

        val previousIds = acceptedCurrent.associate { MediaClassifier.deduplicationKey(it.url) to it.id }
        return MediaClassifier.deduplicateScrapedItems(acceptedCurrent + accepted).map { merged ->
            val stableId = previousIds[MediaClassifier.deduplicationKey(merged.url)]
            if (stableId != null && merged.id != stableId) merged.copy(id = stableId) else merged
        }
    }
}
