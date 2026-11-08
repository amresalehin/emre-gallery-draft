package com.amresalehin.emregallery.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * TargetMediaExpectation represents the expected media type based on URL classification.
 * Used to govern routing decisions and determine whether an extraction result is actually
 * sufficient or merely a partial preview (e.g. static OpenGraph poster).
 */
enum class TargetMediaExpectation {
    VIDEO_OR_STREAM,
    GALLERY_OR_ALBUM,
    GENERIC_ANY
}

/**
 * Extraction Coordinator - Goal-aware, routing-based media extraction engine.
 *
 * Implements a dependable "is this the actual downloadable media?" decision model:
 * 1. Categorizes user expectation (VIDEO_OR_STREAM, GALLERY_OR_ALBUM, GENERIC_ANY).
 * 2. Checks explicit engine availability (isYtDlpAvailable, isGalleryDlAvailable) before invoking Python adapters.
 * 3. Preserves extractor diagnostic error messages (e.g. bot challenge, login required, throttling) without swallowing them.
 * 4. Ensures static thumbnails (og:image) never prematurely terminate video or album extraction pipelines.
 * 5. Falls back to dynamic Chromium browser when specialized or static extractors do not satisfy the expectation.
 */
typealias ExtractionState = DualMediaExtractor.ExtractionState
typealias ExtractionPhase = DualMediaExtractor.ExtractionPhase

class ExtractionCoordinator(internal val context: Context) {

    internal var extractionJob: Job? = null

    internal val _state = MutableStateFlow(ExtractionState())
    val state: StateFlow<ExtractionState> = _state.asStateFlow()


    fun extract(targetUrl: String, scope: CoroutineScope, directOnly: Boolean = false) =
        extractImpl(targetUrl, scope, directOnly)

    companion object {
        fun isDirectMediaUrl(url: String): Boolean {
            if (!MediaClassifier.isValidMedia(url)) return false
            val classification = MediaClassifier.classify(url)
            return classification.isVideo || classification.isAudio || classification.isStream ||
                    url.endsWith(".jpg", true) || url.endsWith(".jpeg", true) ||
                    url.endsWith(".png", true) || url.endsWith(".webp", true) ||
                    url.endsWith(".gif", true) || url.endsWith(".svg", true)
        }

        fun determineExpectation(url: String): TargetMediaExpectation {
            val lower = url.lowercase(java.util.Locale.ROOT)
            if (isVideoPlatformUrl(lower) || lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains("/videoplayback")) {
                return TargetMediaExpectation.VIDEO_OR_STREAM
            }
            if (isGalleryPlatformUrl(lower)) {
                return TargetMediaExpectation.GALLERY_OR_ALBUM
            }
            return TargetMediaExpectation.GENERIC_ANY
        }

        /**
         * Determines whether the discovered media items satisfy the extraction goal.
         * Prevents partial thumbnail/poster previews from being declared as success
         * for video streams or album platforms.
         */
        fun isExtractionSufficient(
            expectation: TargetMediaExpectation,
            items: Collection<ScrapedMediaItem>
        ): Boolean {
            if (items.isEmpty()) return false
            return when (expectation) {
                TargetMediaExpectation.VIDEO_OR_STREAM -> {
                    // Must have at least one playable video, stream, or audio item
                    items.any { it.isVideo || it.isStream || it.isAudio }
                }
                TargetMediaExpectation.GALLERY_OR_ALBUM -> {
                    items.isNotEmpty()
                }
                TargetMediaExpectation.GENERIC_ANY -> {
                    items.isNotEmpty()
                }
            }
        }

        fun isVideoPlatformUrl(lower: String): Boolean {
            val videoHosts = listOf(
                "youtube.com", "youtu.be", "vimeo.com", "tiktok.com", "dailymotion.com",
                "twitch.tv", "soundcloud.com", "bandcamp.com", "facebook.com", "instagram.com/reel"
            )
            return videoHosts.any { lower.contains(it) } || lower.contains(".m3u8") || lower.contains(".mpd")
        }

        fun isGalleryPlatformUrl(lower: String): Boolean {
            val galleryHosts = listOf(
                "reddit.com", "redd.it", "imgur.com", "pinterest.com", "pin.it",
                "artstation.com", "deviantart.com", "flickr.com", "instagram.com/p/",
                "instagram.com/tv/"
            )
            return galleryHosts.any { lower.contains(it) }
        }
    }

    @Synchronized
    fun addDiscoveredItems(newList: List<ScrapedMediaItem>, title: String? = null, domain: String? = null) {
        val existingMap = LinkedHashMap<String, ScrapedMediaItem>()
        for (item in _state.value.items) {
            existingMap[MediaClassifier.deduplicationKey(item.url)] = item
        }
        val modified = MediaNormalizer.mergeCandidates(existingMap, newList)
        if (modified || title != null || domain != null) {
            _state.value = _state.value.copy(
                items = existingMap.values.toList(),
                pageTitle = title ?: _state.value.pageTitle,
                domain = domain ?: _state.value.domain,
                statusMessage = "Discovered ${existingMap.size} media items"
            )
        }
    }

    fun addDiscoveredItem(item: ScrapedMediaItem) {
        addDiscoveredItems(listOf(item))
    }

    fun cancel() {
        extractionJob?.cancel()
        extractionJob = null
        if (_state.value.isRunning) {
            _state.value = _state.value.copy(
                isRunning = false,
                phase = ExtractionPhase.IDLE,
                statusMessage = "Extraction stopped"
            )
        }
    }

    fun reset() {
        cancel()
        _state.value = ExtractionState()
    }

    fun cleanup() {
        reset()
    }
}
