package com.amresalehin.emregallery.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        val cookies = OkHttpDownloader.sharedCookieJar.getCookieString(context.url).takeIf { it.isNotBlank() }
        val res = GalleryDlEngine.extractGalleryDl(
            context.url,
            GalleryDlOptions(cookies = cookies)
        )
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
        val cookies = OkHttpDownloader.sharedCookieJar.getCookieString(context.url).takeIf { it.isNotBlank() }
        val res = GalleryDlEngine.extractYtDlp(
            context.url,
            GalleryDlOptions(cookies = cookies)
        )
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
            onFinished = { failureReason ->
                if (continuation.isActive) {
                    continuation.resume(
                        MediaExtractionResult(
                            items = discoveredList,
                            errorMessage = if (discoveredList.isEmpty()) failureReason else null
                        ),
                        onCancellation = { browserExtractor.cleanup() }
                    )
                }
                browserExtractor.cleanup()
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

        _state.value = ExtractionState(
            isRunning = true,
            statusMessage = "Starting extraction engine...",
            phase = ExtractionPhase.DIRECT_MEDIA,
            domain = domain,
            items = emptyList()
        )

        extractionJob = scope.launch {
            val itemsMap = LinkedHashMap<String, ScrapedMediaItem>()
            val itemsMutex = Mutex()

            suspend fun updateItems(
                newList: List<ScrapedMediaItem>,
                title: String? = null,
                msg: String? = null,
                phase: ExtractionPhase? = null
            ) = itemsMutex.withLock {
                var modified = false

                fun getVideoThumbKeys(): Set<String> {
                    val keys = mutableSetOf<String>()
                    for (entry in itemsMap.values) {
                        if (entry.isVideo) {
                            entry.thumbnailUrl?.let { keys.add(MediaClassifier.deduplicationKey(it)) }
                            val ytId = MediaClassifier.extractYouTubeVideoId(entry.url) ?:
                                       entry.thumbnailUrl?.let { MediaClassifier.extractYouTubeVideoId(it) } ?:
                                       MediaClassifier.extractYouTubeVideoId(entry.provenance.pageUrl)
                            if (ytId != null) {
                                keys.add("ytthumb:${ytId.lowercase(java.util.Locale.ROOT)}")
                            }
                        }
                    }
                    return keys
                }

                for (item in newList) {
                    val key = MediaClassifier.deduplicationKey(item.url)

                    // Case A: Incoming item is a Video
                    if (item.isVideo) {
                        val ytId = MediaClassifier.extractYouTubeVideoId(item.url) ?:
                                   item.thumbnailUrl?.let { MediaClassifier.extractYouTubeVideoId(it) } ?:
                                   MediaClassifier.extractYouTubeVideoId(item.provenance.pageUrl)
                        val videoPosterKeys = mutableSetOf<String>()
                        item.thumbnailUrl?.let { videoPosterKeys.add(MediaClassifier.deduplicationKey(it)) }
                        if (ytId != null) videoPosterKeys.add("ytthumb:${ytId.lowercase(java.util.Locale.ROOT)}")

                        var bestThumb = item.thumbnailUrl
                        for (pKey in videoPosterKeys) {
                            val existingImg = itemsMap[pKey]
                            if (existingImg != null && !existingImg.isVideo) {
                                if (bestThumb == null || MediaClassifier.compareMediaQuality(existingImg, item.copy(url = bestThumb)) > 0) {
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
                            val cmp = MediaClassifier.compareMediaQuality(candidateVideo, existing)
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
                        continue
                    }

                    // Case B: Incoming item is an Image
                    val currentVideoThumbKeys = getVideoThumbKeys()
                    if (key in currentVideoThumbKeys) {
                        for ((vKey, vItem) in itemsMap.entries.toList()) {
                            if (vItem.isVideo) {
                                val ytId = MediaClassifier.extractYouTubeVideoId(vItem.url) ?:
                                           vItem.thumbnailUrl?.let { MediaClassifier.extractYouTubeVideoId(it) } ?:
                                           MediaClassifier.extractYouTubeVideoId(vItem.provenance.pageUrl)
                                val matches = (vItem.thumbnailUrl != null && MediaClassifier.deduplicationKey(vItem.thumbnailUrl) == key) ||
                                              (ytId != null && "ytthumb:${ytId.lowercase(java.util.Locale.ROOT)}" == key)
                                if (matches) {
                                    if (vItem.thumbnailUrl == null || MediaClassifier.compareMediaQuality(item, vItem.copy(url = vItem.thumbnailUrl)) > 0) {
                                        itemsMap[vKey] = vItem.copy(thumbnailUrl = item.thumbnailUrl ?: item.url)
                                        modified = true
                                    }
                                }
                            }
                        }
                        continue
                    }

                    val existing = itemsMap[key]
                    if (existing == null) {
                        itemsMap[key] = item
                        modified = true
                    } else {
                        // Merge and deduplicate across scrapers and variants
                        val cmp = MediaClassifier.compareMediaQuality(item, existing)
                        val preferred = if (cmp > 0) item else existing
                        val secondary = if (preferred === item) existing else item

                        val engines = listOf(existing.provenance.extractorEngine, item.provenance.extractorEngine).distinct()
                        val mergedEngine = engines.joinToString(" + ")
                        val mergedThumb = preferred.thumbnailUrl ?: secondary.thumbnailUrl
                        val mergedResolution = when {
                            MediaClassifier.parsePixelArea(preferred) != null -> preferred.resolutionHint ?: secondary.resolutionHint
                            MediaClassifier.parsePixelArea(secondary) != null -> secondary.resolutionHint ?: preferred.resolutionHint
                            !preferred.resolutionHint.isNullOrBlank() && preferred.resolutionHint != "Network" -> preferred.resolutionHint
                            !secondary.resolutionHint.isNullOrBlank() && secondary.resolutionHint != "Network" -> secondary.resolutionHint
                            else -> preferred.resolutionHint ?: secondary.resolutionHint
                        }
                        val mergedHeaders = secondary.downloadHeaders + preferred.downloadHeaders
                        val resolvedUrl = MediaClassifier.resolveOriginalMedia(preferred.url).mediaUrl

                        val upgraded = preferred.copy(
                            id = existing.id, // Preserve ID for stable Compose keys & selection
                            url = resolvedUrl,
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
                    val currentItems = itemsMap.values.toList()
                    _state.value = _state.value.copy(
                        items = currentItems,
                        pageTitle = if (_state.value.pageTitle.isBlank() && !title.isNullOrBlank()) title else _state.value.pageTitle,
                        statusMessage = msg ?: _state.value.statusMessage,
                        phase = phase ?: _state.value.phase
                    )
                    DownloaderSession.addDiscoveredItems(currentItems)
                    if (title != null && title.isNotBlank()) {
                        DownloaderSession.setPageTitle(title)
                    }
                    if (msg != null) {
                        DownloaderSession.setStatusMessage(msg)
                    }
                }
            }

            suspend fun updateStatus(status: String, phase: ExtractionPhase? = null) = itemsMutex.withLock {
                _state.value = _state.value.copy(
                    statusMessage = status,
                    phase = phase ?: _state.value.phase
                )
            }

            try {
                // Provider 1: Direct Media Provider (Fast-path initial discovery)
                val directProvider = DirectMediaProvider()
                if (directProvider.isApplicable(requestContext)) {
                    updateStatus("Identified direct media link...", ExtractionPhase.DIRECT_MEDIA)
                    val directRes = coroutineScope {
                        directProvider.extract(
                            context = requestContext,
                            onItemDiscovered = { item -> launch { updateItems(listOf(item)) } },
                            onStatusUpdate = {}
                        )
                    }
                    if (directRes.items.isNotEmpty()) {
                        updateItems(
                            newList = directRes.items,
                            title = directRes.pageTitle ?: domain,
                            msg = "Discovered direct media file"
                        )
                    }
                }

                if (directOnly) {
                    itemsMutex.withLock {
                        _state.value = _state.value.copy(
                            isRunning = false,
                            phase = ExtractionPhase.COMPLETED,
                            statusMessage = if (itemsMap.isNotEmpty()) "Discovered direct media file" else "No direct media found"
                        )
                    }
                    return@launch
                }

                // Stage 2: Independent Concurrent Extractor Execution
                // (gallery-dl, yt-dlp, static HTML, AND Live Chromium Browser)
                updateStatus("Searching media with concurrent engines...", ExtractionPhase.BROWSER_INTERCEPTING)

                val galleryDlProvider = GalleryDlProvider()
                val ytDlpProvider = YtDlpProvider()
                val staticHtmlProvider = StaticHtmlProvider()
                val browserProvider = BrowserProvider(context)
                activeBrowserProvider = browserProvider

                var gdlResult: MediaExtractionResult? = null
                var ytdlpResult: MediaExtractionResult? = null
                var staticResult: MediaExtractionResult? = null
                var browserResult: MediaExtractionResult? = null

                try {
                    // Launch all providers concurrently using structured concurrency under coroutineScope
                    coroutineScope {
                        val gdlDeferred = async(Dispatchers.IO) {
                            try {
                                if (galleryDlProvider.isApplicable(requestContext)) {
                                    galleryDlProvider.extract(
                                        context = requestContext,
                                        onItemDiscovered = { item ->
                                            launch { updateItems(listOf(item)) }
                                        },
                                        onStatusUpdate = { status ->
                                            launch { updateStatus(status) }
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
                                            launch { updateItems(listOf(item)) }
                                        },
                                        onStatusUpdate = { status ->
                                            launch { updateStatus(status) }
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
                                        launch { updateItems(listOf(item)) }
                                    },
                                    onStatusUpdate = { status ->
                                        launch { updateStatus(status) }
                                    }
                                )
                            } catch (e: Exception) {
                                Log.w("DualMediaExtractor", "StaticHtmlProvider error", e)
                                null
                            }
                        }

                        val browserDeferred = async {
                            try {
                                browserProvider.extract(
                                    context = requestContext,
                                    onItemDiscovered = { item ->
                                        launch { updateItems(listOf(item)) }
                                    },
                                    onStatusUpdate = { status ->
                                        val phase = if (status.contains("scrolling", ignoreCase = true)) {
                                            ExtractionPhase.AUTO_SCROLLING
                                        } else {
                                            ExtractionPhase.BROWSER_INTERCEPTING
                                        }
                                        launch { updateStatus(status, phase) }
                                    }
                                )
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (e: Exception) {
                                Log.w("DualMediaExtractor", "Browser extraction error", e)
                                null
                            }
                        }

                        gdlResult = gdlDeferred.await()
                        ytdlpResult = ytdlpDeferred.await()
                        staticResult = staticDeferred.await()
                        browserResult = browserDeferred.await()

                        if (gdlResult != null && gdlResult.items.isNotEmpty()) {
                            updateItems(gdlResult.items, title = gdlResult.pageTitle)
                        }
                        if (ytdlpResult != null && ytdlpResult.items.isNotEmpty()) {
                            updateItems(ytdlpResult.items, title = ytdlpResult.pageTitle)
                        }
                        if (staticResult != null && staticResult.items.isNotEmpty()) {
                            updateItems(staticResult.items, title = staticResult.pageTitle)
                        }
                        if (browserResult != null && browserResult.items.isNotEmpty()) {
                            updateItems(browserResult.items, title = browserResult.pageTitle)
                        }
                    }
                } finally {
                    browserProvider.cleanup()
                    activeBrowserProvider = null
                }

                itemsMutex.withLock {
                    val finalCount = itemsMap.size
                    val detectedReason: String? = if (finalCount == 0) {
                        browserResult?.errorMessage?.takeIf { it.isNotBlank() }
                            ?: staticResult?.errorMessage?.takeIf { it.isNotBlank() }
                            ?: gdlResult?.errorMessage?.takeIf { it.isNotBlank() }
                    } else null

                    _state.value = _state.value.copy(
                        isRunning = false,
                        phase = if (detectedReason != null) ExtractionPhase.ERROR else ExtractionPhase.COMPLETED,
                        statusMessage = if (finalCount > 0) "Extraction completed. Discovered $finalCount media items." else (detectedReason ?: "No media discovered on this page."),
                        errorMessage = detectedReason
                    )
                    if (detectedReason != null && finalCount == 0) {
                        DownloaderSession.setStatusMessage(detectedReason)
                    }
                }
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

    @Synchronized
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
                val cmp = MediaClassifier.compareMediaQuality(item, existing)
                val preferred = if (cmp > 0) item else existing
                val secondary = if (preferred === item) existing else item
                val engines = listOf(existing.provenance.extractorEngine, item.provenance.extractorEngine).distinct()
                val mergedEngine = engines.joinToString(" + ")
                val mergedThumb = preferred.thumbnailUrl ?: secondary.thumbnailUrl
                val mergedHeaders = secondary.downloadHeaders + preferred.downloadHeaders
                val resolvedUrl = MediaClassifier.resolveOriginalMedia(preferred.url).mediaUrl
                val upgraded = preferred.copy(
                    id = existing.id,
                    url = resolvedUrl,
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
