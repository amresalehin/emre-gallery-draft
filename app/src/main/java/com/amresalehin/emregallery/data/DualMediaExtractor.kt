package com.amresalehin.emregallery.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.URL

data class MediaRequestContext(
    val url: String,
    val domain: String
) {
    companion object {
        fun create(targetUrl: String): MediaRequestContext {
            val trimmed = targetUrl.trim()
            val normalized = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                "https://$trimmed"
            } else {
                trimmed
            }
            val domain = runCatching { URL(normalized).host }.getOrDefault("")
            return MediaRequestContext(url = normalized, domain = domain)
        }
    }
}

data class MediaExtractionResult(
    val items: List<ScrapedMediaItem> = emptyList(),
    val pageTitle: String? = null,
    val errorMessage: String? = null
)

interface ExtractionProvider {
    fun isApplicable(context: MediaRequestContext): Boolean
    suspend fun extract(
        context: MediaRequestContext,
        onItemDiscovered: (ScrapedMediaItem) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): MediaExtractionResult
}

class DirectMediaProvider : ExtractionProvider {
    override fun isApplicable(context: MediaRequestContext): Boolean {
        if (!MediaClassifier.isValidMedia(context.url)) return false
        val classification = MediaClassifier.classify(context.url)
        return classification.isVideo || classification.isAudio || classification.isStream ||
                context.url.endsWith(".jpg", true) || context.url.endsWith(".jpeg", true) ||
                context.url.endsWith(".png", true) || context.url.endsWith(".webp", true) ||
                context.url.endsWith(".gif", true) || context.url.endsWith(".svg", true)
    }

    override suspend fun extract(
        context: MediaRequestContext,
        onItemDiscovered: (ScrapedMediaItem) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): MediaExtractionResult {
        val resolvedInfo = MediaClassifier.resolveOriginalMedia(context.url)
        val downloadUrl = resolvedInfo.mediaUrl
        val classification = MediaClassifier.classify(downloadUrl)
        val fileName = resolvedInfo.cleanTitle ?: MediaClassifier.deriveFileName(downloadUrl, classification.format, 0)
        val thumb = resolvedInfo.thumbnailUrl ?: if (classification.category == MediaCategory.IMAGES) downloadUrl else null
        val item = ScrapedMediaItem(
            id = "direct_${System.currentTimeMillis()}",
            url = downloadUrl,
            title = fileName,
            isVideo = classification.isVideo,
            isAudio = classification.isAudio,
            isStream = classification.isStream,
            category = classification.category,
            format = classification.format,
            thumbnailUrl = thumb,
            mimeType = classification.mimeType,
            source = "Direct Link",
            provenance = MediaProvenance(
                extractorEngine = "Direct URL",
                pageUrl = context.url
            ),
            metadata = mapOf("source_url" to context.url),
            downloadHeaders = mapOf(
                "User-Agent" to OkHttpDownloader.DEFAULT_USER_AGENT,
                "Referer" to context.url
            )
        )
        onItemDiscovered(item)
        return MediaExtractionResult(items = listOf(item), pageTitle = fileName)
    }
}

class GalleryDlProvider : ExtractionProvider {
    override fun isApplicable(context: MediaRequestContext): Boolean {
        return GalleryDlEngine.isAvailable() && GalleryDlEngine.checkSupport(context.url).isSupported
    }

    override suspend fun extract(
        context: MediaRequestContext,
        onItemDiscovered: (ScrapedMediaItem) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): MediaExtractionResult {
        onStatusUpdate("Extracting with gallery-dl...")
        val res = GalleryDlEngine.extractGalleryDl(context.url)
        if (res.success) {
            for (item in res.items) {
                onItemDiscovered(item)
            }
            return MediaExtractionResult(items = res.items, pageTitle = res.pageTitle)
        }
        return MediaExtractionResult(items = emptyList(), errorMessage = res.errorMessage)
    }
}

class YtDlpProvider : ExtractionProvider {
    override fun isApplicable(context: MediaRequestContext): Boolean {
        if (!GalleryDlEngine.isAvailable()) return false
        val lower = context.url.lowercase()
        val videoHosts = listOf(
            "youtube.com", "youtu.be", "vimeo.com", "tiktok.com", "dailymotion.com",
            "twitch.tv", "soundcloud.com", "bandcamp.com", "facebook.com", "instagram.com"
        )
        return videoHosts.any { lower.contains(it) } || lower.contains(".m3u8") || lower.contains(".mpd")
    }

    override suspend fun extract(
        context: MediaRequestContext,
        onItemDiscovered: (ScrapedMediaItem) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): MediaExtractionResult {
        onStatusUpdate("Extracting with yt-dlp...")
        val res = GalleryDlEngine.extractYtDlp(context.url)
        if (res.success) {
            for (item in res.items) {
                onItemDiscovered(item)
            }
            return MediaExtractionResult(items = res.items, pageTitle = res.pageTitle)
        }
        return MediaExtractionResult(items = emptyList(), errorMessage = res.errorMessage)
    }
}

class StaticHtmlProvider : ExtractionProvider {
    private val scraper = StaticMediaScraper()

    override fun isApplicable(context: MediaRequestContext): Boolean = true

    override suspend fun extract(
        context: MediaRequestContext,
        onItemDiscovered: (ScrapedMediaItem) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): MediaExtractionResult {
        onStatusUpdate("Parsing page HTML & DOM...")
        val res = scraper.scrape(context.url)
        for (item in res.items) {
            onItemDiscovered(item)
        }
        return MediaExtractionResult(
            items = res.items,
            pageTitle = res.pageTitle,
            errorMessage = res.errorMessage
        )
    }
}

class BrowserProvider(private val context: Context) : ExtractionProvider {
    private val browserExtractor = BrowserMediaExtractor(context)

    override fun isApplicable(context: MediaRequestContext): Boolean = true

    override suspend fun extract(
        context: MediaRequestContext,
        onItemDiscovered: (ScrapedMediaItem) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): MediaExtractionResult = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            browserExtractor.cleanup()
        }
        val discoveredList = mutableListOf<ScrapedMediaItem>()
        browserExtractor.startLiveExtraction(
            targetUrl = context.url,
            onStatusUpdate = onStatusUpdate,
            onMediaDiscovered = { item ->
                discoveredList.add(item)
                onItemDiscovered(item)
            },
            onFinished = {
                if (continuation.isActive) {
                    continuation.resume(
                        MediaExtractionResult(items = discoveredList),
                        onCancellation = { browserExtractor.cleanup() }
                    )
                }
            }
        )
    }

    fun cleanup() {
        browserExtractor.cleanup()
    }
}

/**
 * Multi-Provider Media Extraction Engine Coordinator.
 * Coordinates DirectMediaProvider, GalleryDlProvider, YtDlpProvider, StaticHtmlProvider,
 * and BrowserProvider independently.
 * Progressively merges discovered items with canonical URL deduplication and never
 * terminates prematurely when a single provider completes.
 */
class DualMediaExtractor(private val context: Context) {

    private var extractionJob: Job? = null
    private var activeBrowserProvider: BrowserProvider? = null

    data class ExtractionState(
        val isRunning: Boolean = false,
        val statusMessage: String = "",
        val phase: ExtractionPhase = ExtractionPhase.IDLE,
        val pageTitle: String = "",
        val domain: String = "",
        val items: List<ScrapedMediaItem> = emptyList(),
        val errorMessage: String? = null
    )

    enum class ExtractionPhase {
        IDLE,
        DIRECT_MEDIA,
        GALLERY_DL,
        STATIC_SCRAPING,
        BROWSER_INTERCEPTING,
        AUTO_SCROLLING,
        COMPLETED,
        ERROR
    }

    private val _state = MutableStateFlow(ExtractionState())
    val state: StateFlow<ExtractionState> = _state.asStateFlow()

    fun extract(targetUrl: String, scope: CoroutineScope) {
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

        _state.value = ExtractionState(
            isRunning = true,
            statusMessage = "Starting extraction engine...",
            phase = ExtractionPhase.DIRECT_MEDIA,
            domain = domain,
            items = emptyList()
        )

        extractionJob = scope.launch(Dispatchers.Main) {
            val itemsMap = LinkedHashMap<String, ScrapedMediaItem>()

            fun updateItems(
                newList: List<ScrapedMediaItem>,
                title: String? = null,
                msg: String? = null,
                phase: ExtractionPhase? = null
            ) {
                var modified = false
                for (item in newList) {
                    val key = MediaClassifier.deduplicationKey(item.url)
                    val existing = itemsMap[key]
                    if (existing == null) {
                        itemsMap[key] = item
                        modified = true
                    } else {
                        // Merge and deduplicate across scrapers
                        val preferred = when {
                            !item.url.contains("/thumb/") && !item.url.contains("/transcoded/") &&
                                    (existing.url.contains("/thumb/") || existing.url.contains("/transcoded/")) -> item
                            !existing.url.contains("/thumb/") && !existing.url.contains("/transcoded/") &&
                                    (item.url.contains("/thumb/") || item.url.contains("/transcoded/")) -> existing
                            (item.sizeBytes ?: 0L) > (existing.sizeBytes ?: 0L) -> item
                            else -> existing
                        }
                        val engines = listOf(existing.provenance.extractorEngine, item.provenance.extractorEngine).distinct()
                        val mergedEngine = engines.joinToString(" + ")
                        val mergedThumb = preferred.thumbnailUrl ?: item.thumbnailUrl ?: existing.thumbnailUrl
                        val mergedResolution = when {
                            !preferred.resolutionHint.isNullOrBlank() && preferred.resolutionHint != "Network" -> preferred.resolutionHint
                            !item.resolutionHint.isNullOrBlank() && item.resolutionHint != "Network" -> item.resolutionHint
                            !existing.resolutionHint.isNullOrBlank() && existing.resolutionHint != "Network" -> existing.resolutionHint
                            else -> preferred.resolutionHint ?: existing.resolutionHint ?: item.resolutionHint
                        }
                        val mergedHeaders = existing.downloadHeaders + item.downloadHeaders
                        val upgraded = preferred.copy(
                            thumbnailUrl = mergedThumb,
                            resolutionHint = mergedResolution,
                            provenance = preferred.provenance.copy(extractorEngine = mergedEngine),
                            downloadHeaders = mergedHeaders
                        )
                        if (upgraded != existing) {
                            itemsMap[key] = upgraded
                            modified = true
                        }
                    }
                }
                if (modified || msg != null || phase != null || title != null) {
                    _state.value = _state.value.copy(
                        items = itemsMap.values.toList(),
                        pageTitle = if (_state.value.pageTitle.isBlank() && !title.isNullOrBlank()) title else _state.value.pageTitle,
                        statusMessage = msg ?: _state.value.statusMessage,
                        phase = phase ?: _state.value.phase
                    )
                }
            }

            try {
                // Provider 1: Direct Media Provider
                val directProvider = DirectMediaProvider()
                if (directProvider.isApplicable(requestContext)) {
                    _state.value = _state.value.copy(
                        phase = ExtractionPhase.DIRECT_MEDIA,
                        statusMessage = "Identified direct media link..."
                    )
                    val directRes = directProvider.extract(
                        context = requestContext,
                        onItemDiscovered = { item -> updateItems(listOf(item)) },
                        onStatusUpdate = {}
                    )
                    if (directRes.items.isNotEmpty()) {
                        updateItems(
                            newList = directRes.items,
                            title = directRes.pageTitle ?: domain,
                            msg = "Discovered direct media file",
                            phase = ExtractionPhase.COMPLETED
                        )
                        _state.value = _state.value.copy(
                            isRunning = false,
                            phase = ExtractionPhase.COMPLETED,
                            statusMessage = "Discovered direct media file"
                        )
                        return@launch
                    }
                }

                // Stage 2: Independent Concurrent Extractor Execution (gallery-dl, yt-dlp, static HTML)
                _state.value = _state.value.copy(
                    phase = ExtractionPhase.GALLERY_DL,
                    statusMessage = "Querying extraction providers..."
                )

                val galleryDlProvider = GalleryDlProvider()
                val ytDlpProvider = YtDlpProvider()
                val staticHtmlProvider = StaticHtmlProvider()

                // Launch providers independently without letting one's failure abort others
                val gdlDeferred = async(Dispatchers.IO) {
                    try {
                        if (galleryDlProvider.isApplicable(requestContext)) {
                            galleryDlProvider.extract(
                                context = requestContext,
                                onItemDiscovered = { item ->
                                    launch(Dispatchers.Main) { updateItems(listOf(item)) }
                                },
                                onStatusUpdate = { status ->
                                    launch(Dispatchers.Main) {
                                        _state.value = _state.value.copy(statusMessage = status)
                                    }
                                }
                            )
                        } else null
                    } catch (e: Exception) {
                        Log.w("DualMediaExtractor", "gallery-dl extraction error", e)
                        null
                    }
                }

                val ytdlpDeferred = async(Dispatchers.IO) {
                    try {
                        if (ytDlpProvider.isApplicable(requestContext)) {
                            ytDlpProvider.extract(
                                context = requestContext,
                                onItemDiscovered = { item ->
                                    launch(Dispatchers.Main) { updateItems(listOf(item)) }
                                },
                                onStatusUpdate = { status ->
                                    launch(Dispatchers.Main) {
                                        _state.value = _state.value.copy(statusMessage = status)
                                    }
                                }
                            )
                        } else null
                    } catch (e: Exception) {
                        Log.w("DualMediaExtractor", "yt-dlp extraction error", e)
                        null
                    }
                }

                val staticDeferred = async(Dispatchers.IO) {
                    try {
                        staticHtmlProvider.extract(
                            context = requestContext,
                            onItemDiscovered = { item ->
                                launch(Dispatchers.Main) { updateItems(listOf(item)) }
                            },
                            onStatusUpdate = { status ->
                                launch(Dispatchers.Main) {
                                    _state.value = _state.value.copy(statusMessage = status)
                                }
                            }
                        )
                    } catch (e: Exception) {
                        Log.w("DualMediaExtractor", "StaticHtmlProvider error", e)
                        null
                    }
                }

                val gdlResult = gdlDeferred.await()
                val ytdlpResult = ytdlpDeferred.await()
                val staticResult = staticDeferred.await()

                if (gdlResult != null && gdlResult.items.isNotEmpty()) {
                    updateItems(gdlResult.items, title = gdlResult.pageTitle)
                }
                if (ytdlpResult != null && ytdlpResult.items.isNotEmpty()) {
                    updateItems(ytdlpResult.items, title = ytdlpResult.pageTitle)
                }
                if (staticResult != null && staticResult.items.isNotEmpty()) {
                    updateItems(staticResult.items, title = staticResult.pageTitle)
                }

                // Stage 3: Live Chromium Browser Extraction (Network Interception, MutationObserver, Adaptive Scroll)
                val browserProvider = BrowserProvider(context)
                activeBrowserProvider = browserProvider

                _state.value = _state.value.copy(
                    phase = ExtractionPhase.BROWSER_INTERCEPTING,
                    statusMessage = "Live browser: intercepting streaming requests & dynamic DOM..."
                )

                try {
                    browserProvider.extract(
                        context = requestContext,
                        onItemDiscovered = { item ->
                            updateItems(listOf(item))
                        },
                        onStatusUpdate = { status ->
                            val phase = if (status.contains("scrolling", ignoreCase = true)) {
                                ExtractionPhase.AUTO_SCROLLING
                            } else {
                                ExtractionPhase.BROWSER_INTERCEPTING
                            }
                            _state.value = _state.value.copy(
                                statusMessage = status,
                                phase = phase
                            )
                        }
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    Log.w("DualMediaExtractor", "Browser extraction error", e)
                } finally {
                    activeBrowserProvider = null
                }

                val finalCount = itemsMap.size
                _state.value = _state.value.copy(
                    isRunning = false,
                    phase = ExtractionPhase.COMPLETED,
                    statusMessage = if (finalCount > 0) "Extraction completed. Discovered $finalCount media items." else "No media discovered on this page."
                )
            } catch (ce: CancellationException) {
                _state.value = _state.value.copy(
                    isRunning = false,
                    phase = ExtractionPhase.IDLE,
                    statusMessage = "Extraction stopped"
                )
            } catch (e: Exception) {
                Log.e("DualMediaExtractor", "Orchestration error", e)
                _state.value = _state.value.copy(
                    isRunning = false,
                    phase = ExtractionPhase.ERROR,
                    statusMessage = "Extraction failed: ${e.localizedMessage}",
                    errorMessage = e.localizedMessage
                )
            }
        }
    }

    fun addDiscoveredItems(newList: List<ScrapedMediaItem>, title: String? = null, domain: String? = null) {
        val existingMap = LinkedHashMap<String, ScrapedMediaItem>()
        for (item in _state.value.items) {
            existingMap[MediaClassifier.deduplicationKey(item.url)] = item
        }
        var modified = false
        for (item in newList) {
            val key = MediaClassifier.deduplicationKey(item.url)
            val existing = existingMap[key]
            if (existing == null) {
                existingMap[key] = item
                modified = true
            } else {
                val preferred = if (!item.url.contains("/thumb/") && existing.url.contains("/thumb/")) item else existing
                val engines = listOf(existing.provenance.extractorEngine, item.provenance.extractorEngine).distinct()
                val mergedEngine = engines.joinToString(" + ")
                val mergedThumb = preferred.thumbnailUrl ?: item.thumbnailUrl ?: existing.thumbnailUrl
                val mergedHeaders = existing.downloadHeaders + item.downloadHeaders
                val upgraded = preferred.copy(
                    thumbnailUrl = mergedThumb,
                    provenance = preferred.provenance.copy(extractorEngine = mergedEngine),
                    downloadHeaders = mergedHeaders
                )
                if (upgraded != existing) {
                    existingMap[key] = upgraded
                    modified = true
                }
            }
        }
        if (modified || title != null || domain != null) {
            _state.value = _state.value.copy(
                items = existingMap.values.toList(),
                pageTitle = title ?: _state.value.pageTitle,
                domain = domain ?: _state.value.domain,
                statusMessage = "Discovered ${existingMap.size} media items from Browser"
            )
        }
    }

    fun addDiscoveredItem(item: ScrapedMediaItem) {
        addDiscoveredItems(listOf(item))
    }

    fun cancel() {
        extractionJob?.cancel()
        extractionJob = null
        activeBrowserProvider?.cleanup()
        activeBrowserProvider = null
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
