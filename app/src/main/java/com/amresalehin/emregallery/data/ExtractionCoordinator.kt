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

class ExtractionCoordinator(private val context: Context) {

    private var extractionJob: Job? = null

    private val _state = MutableStateFlow(ExtractionState())
    val state: StateFlow<ExtractionState> = _state.asStateFlow()

    fun extract(targetUrl: String, scope: CoroutineScope, directOnly: Boolean = false) {
        cancel()

        val trimmed = targetUrl.trim()
        if (!MediaClassifier.isValidWebUrl(trimmed)) {
            _state.value = ExtractionState(
                errorMessage = "Please enter a valid URL (e.g. https://example.com)",
                isRunning = false,
                items = emptyList()
            )
            return
        }

        val requestContext = MediaRequestContext.create(trimmed)
        val domain = requestContext.domain
        val expectation = determineExpectation(requestContext.url)

        _state.value = ExtractionState(
            isRunning = true,
            statusMessage = "Analyzing URL route...",
            phase = ExtractionPhase.DIRECT_MEDIA,
            domain = domain,
            items = emptyList()
        )

        extractionJob = scope.launch {
            val itemsMap = LinkedHashMap<String, ScrapedMediaItem>()
            val itemsMutex = Mutex()
            var lastDiagnosticError: String? = null

            suspend fun updateItems(
                newList: List<ScrapedMediaItem>,
                title: String? = null,
                msg: String? = null,
                phase: ExtractionPhase? = null
            ) = itemsMutex.withLock {
                val modified = MediaNormalizer.mergeCandidates(itemsMap, newList)
                if (modified || msg != null || phase != null || title != null) {
                    val currentItems = itemsMap.values.toList()
                    _state.value = _state.value.copy(
                        items = currentItems,
                        pageTitle = if (_state.value.pageTitle.isBlank() && !title.isNullOrBlank()) title else _state.value.pageTitle,
                        statusMessage = msg ?: _state.value.statusMessage,
                        phase = phase ?: _state.value.phase
                    )
                    DownloaderSession.addDiscoveredItems(currentItems)
                    if (!title.isNullOrBlank()) DownloaderSession.setPageTitle(title)
                    if (msg != null) DownloaderSession.setStatusMessage(msg)
                }
            }

            suspend fun updateStatus(status: String, phase: ExtractionPhase? = null) = itemsMutex.withLock {
                _state.value = _state.value.copy(
                    statusMessage = status,
                    phase = phase ?: _state.value.phase
                )
            }

            try {
                // Route 1: Direct media file check
                if (isDirectMediaUrl(requestContext.url)) {
                    updateStatus("Resolving direct media file...", ExtractionPhase.DIRECT_MEDIA)
                    val directItem = MediaNormalizer.normalize(
                        rawUrl = requestContext.url,
                        source = ExtractionSource.STATIC_HTML,
                        baseUrl = requestContext.url
                    )
                    if (directItem != null) {
                        updateItems(listOf(directItem), title = directItem.title, msg = "Discovered direct media file")
                        _state.value = _state.value.copy(
                            isRunning = false,
                            phase = ExtractionPhase.COMPLETED,
                            statusMessage = "Discovered direct media file"
                        )
                        return@launch
                    }
                }

                if (directOnly) {
                    _state.value = _state.value.copy(
                        isRunning = false,
                        phase = ExtractionPhase.COMPLETED,
                        statusMessage = if (itemsMap.isNotEmpty()) "Discovered direct media file" else "No direct media found"
                    )
                    return@launch
                }

                // Route 2: Video / Stream Platforms -> yt-dlp
                if (expectation == TargetMediaExpectation.VIDEO_OR_STREAM) {
                    if (GalleryDlEngine.isYtDlpAvailable()) {
                        updateStatus("Extracting via media platform adapter...", ExtractionPhase.GALLERY_DL)
                        val cookies = OkHttpDownloader.sharedCookieJar.getCookieString(requestContext.url).takeIf { it.isNotBlank() }
                        val ytdlpRes = withContext(Dispatchers.IO) {
                            try {
                                GalleryDlEngine.extractYtDlp(requestContext.url, GalleryDlOptions(cookies = cookies))
                            } catch (e: Exception) {
                                GalleryDlExtractResult(success = false, errorMessage = e.localizedMessage ?: "yt-dlp execution error")
                            }
                        }

                        if (ytdlpRes.success && ytdlpRes.items.isNotEmpty()) {
                            updateItems(ytdlpRes.items, title = ytdlpRes.pageTitle)
                            if (isExtractionSufficient(expectation, itemsMap.values)) {
                                finishSuccess(itemsMap.size)
                                return@launch
                            }
                        } else if (!ytdlpRes.errorMessage.isNullOrBlank()) {
                            lastDiagnosticError = ytdlpRes.errorMessage
                            Log.w("ExtractionCoordinator", "yt-dlp extraction failed: ${ytdlpRes.errorMessage}")
                            updateStatus("Platform extractor: ${ytdlpRes.errorMessage?.take(50)}. Probing fallback...")
                        }
                    } else {
                        lastDiagnosticError = "yt-dlp adapter is not installed or available in Python runtime"
                        Log.i("ExtractionCoordinator", "yt-dlp unavailable in Python runtime, skipping to fallback")
                    }
                }

                // Route 3: Gallery / Image Platforms -> gallery-dl
                if (expectation == TargetMediaExpectation.GALLERY_OR_ALBUM || GalleryDlEngine.checkSupport(requestContext.url).isSupported) {
                    if (GalleryDlEngine.isGalleryDlAvailable()) {
                        updateStatus("Extracting via gallery platform adapter...", ExtractionPhase.GALLERY_DL)
                        val cookies = OkHttpDownloader.sharedCookieJar.getCookieString(requestContext.url).takeIf { it.isNotBlank() }
                        val gdlRes = withContext(Dispatchers.IO) {
                            try {
                                GalleryDlEngine.extractGalleryDl(requestContext.url, GalleryDlOptions(cookies = cookies))
                            } catch (e: Exception) {
                                GalleryDlExtractResult(success = false, errorMessage = e.localizedMessage ?: "gallery-dl execution error")
                            }
                        }

                        if (gdlRes.success && gdlRes.items.isNotEmpty()) {
                            updateItems(gdlRes.items, title = gdlRes.pageTitle)
                            if (isExtractionSufficient(expectation, itemsMap.values)) {
                                finishSuccess(itemsMap.size)
                                return@launch
                            }
                        } else if (!gdlRes.errorMessage.isNullOrBlank()) {
                            lastDiagnosticError = gdlRes.errorMessage
                            Log.w("ExtractionCoordinator", "gallery-dl extraction failed: ${gdlRes.errorMessage}")
                        }
                    }
                }

                // Route 4: Webpage HTML Scraper
                updateStatus("Parsing webpage HTML & metadata...", ExtractionPhase.STATIC_SCRAPING)
                val scraper = StaticMediaScraper()
                val staticRes = withContext(Dispatchers.IO) {
                    try {
                        scraper.scrape(requestContext.url)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (staticRes != null && staticRes.items.isNotEmpty()) {
                    updateItems(staticRes.items, title = staticRes.pageTitle)
                    // Core validation gate: only finish if the discovered items actually satisfy expectation!
                    if (isExtractionSufficient(expectation, itemsMap.values)) {
                        finishSuccess(itemsMap.size)
                        return@launch
                    } else {
                        // For video platforms, static scraper only discovered thumbnails/OpenGraph art
                        Log.i("ExtractionCoordinator", "Static scraping found preview thumbnails, but video stream is unresolved. Continuing to browser...")
                        updateStatus("Preview image discovered. Probing dynamic playback for video stream...", ExtractionPhase.BROWSER_INTERCEPTING)
                    }
                }

                // Route 5: Dynamic JavaScript Browser Fallback
                updateStatus("Probing dynamic page content...", ExtractionPhase.BROWSER_INTERCEPTING)
                val browserRes = BrowserSession.extractHeadless(
                    context = context,
                    targetUrl = requestContext.url,
                    onStatusUpdate = { s -> launch { updateStatus(s) } },
                    onMediaDiscovered = { item -> launch { updateItems(listOf(item)) } }
                )

                if (browserRes.items.isNotEmpty()) {
                    updateItems(browserRes.items, title = browserRes.pageTitle)
                }

                // Final Goal Evaluation
                if (isExtractionSufficient(expectation, itemsMap.values)) {
                    finishSuccess(itemsMap.size)
                } else {
                    // Extraction failed to satisfy the media expectation.
                    // Preserve and surface the true root diagnostic so user can take action.
                    val diagnostic = lastDiagnosticError ?: browserRes.errorMessage ?: staticRes?.errorMessage
                    val finalReason = when {
                        diagnostic != null && (diagnostic.contains("bot", true) || diagnostic.contains("sign in", true) ||
                                diagnostic.contains("challenge", true) || diagnostic.contains("waf", true) ||
                                diagnostic.contains("429", true) || diagnostic.contains("captcha", true)) -> {
                            "Verification Required: $diagnostic"
                        }
                        expectation == TargetMediaExpectation.VIDEO_OR_STREAM && itemsMap.isNotEmpty() -> {
                            val detail = diagnostic ?: "Video stream is dynamically protected or requires login."
                            "Only preview images found. $detail Tap 'Open in Live Browser' to play and capture."
                        }
                        diagnostic != null -> diagnostic
                        else -> "No media discovered on this page."
                    }

                    _state.value = _state.value.copy(
                        isRunning = false,
                        phase = ExtractionPhase.ERROR,
                        statusMessage = finalReason,
                        errorMessage = finalReason
                    )
                }
            } catch (ce: CancellationException) {
                _state.value = _state.value.copy(
                    isRunning = false,
                    phase = ExtractionPhase.IDLE,
                    statusMessage = "Extraction stopped"
                )
            } catch (e: Exception) {
                Log.e("ExtractionCoordinator", "Routing error", e)
                val err = e.localizedMessage ?: "Extraction failed"
                _state.value = _state.value.copy(
                    isRunning = false,
                    phase = ExtractionPhase.ERROR,
                    statusMessage = "Extraction failed: $err",
                    errorMessage = err
                )
            }
        }
    }

    private fun finishSuccess(count: Int) {
        _state.value = _state.value.copy(
            isRunning = false,
            phase = ExtractionPhase.COMPLETED,
            statusMessage = "Extraction completed. Discovered $count media items."
        )
    }

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
