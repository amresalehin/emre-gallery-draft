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
 * Extraction Coordinator - Intelligent, routing-based media extraction engine.
 *
 * Instead of running direct, gallery-dl, yt-dlp, static HTML, and browser in parallel,
 * this coordinator routes the request based on URL classification:
 * 1. Direct Media Files -> Direct Resolver (instant)
 * 2. Video / Stream Platforms (YouTube, TikTok, Vimeo, Twitch, etc.) -> yt-dlp Adapter
 * 3. Gallery / Image Platforms (Reddit, Imgur, Pinterest, ArtStation, etc.) -> gallery-dl Adapter
 * 4. Generic Web Pages -> Static HTML Adapter (Jsoup + OpenGraph + DOM)
 * 5. Dynamic JavaScript Fallback -> BrowserSession headless extraction only if static extraction yielded 0 items
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

                val lower = requestContext.url.lowercase(java.util.Locale.ROOT)

                // Route 2: Video / Stream Platforms -> yt-dlp
                val isVideoPlatform = isVideoPlatformUrl(lower)
                if (isVideoPlatform && GalleryDlEngine.isAvailable()) {
                    updateStatus("Extracting via media platform adapter...", ExtractionPhase.GALLERY_DL)
                    val cookies = OkHttpDownloader.sharedCookieJar.getCookieString(requestContext.url).takeIf { it.isNotBlank() }
                    val ytdlpRes = withContext(Dispatchers.IO) {
                        runCatching {
                            GalleryDlEngine.extractYtDlp(requestContext.url, GalleryDlOptions(cookies = cookies))
                        }.getOrNull()
                    }
                    if (ytdlpRes != null && ytdlpRes.success && ytdlpRes.items.isNotEmpty()) {
                        updateItems(ytdlpRes.items, title = ytdlpRes.pageTitle)
                        finishSuccess(itemsMap.size)
                        return@launch
                    }
                }

                // Route 3: Gallery / Image Platforms -> gallery-dl
                val isGalleryPlatform = GalleryDlEngine.isAvailable() && GalleryDlEngine.checkSupport(requestContext.url).isSupported
                if (isGalleryPlatform) {
                    updateStatus("Extracting via gallery platform adapter...", ExtractionPhase.GALLERY_DL)
                    val cookies = OkHttpDownloader.sharedCookieJar.getCookieString(requestContext.url).takeIf { it.isNotBlank() }
                    val gdlRes = withContext(Dispatchers.IO) {
                        runCatching {
                            GalleryDlEngine.extractGalleryDl(requestContext.url, GalleryDlOptions(cookies = cookies))
                        }.getOrNull()
                    }
                    if (gdlRes != null && gdlRes.success && gdlRes.items.isNotEmpty()) {
                        updateItems(gdlRes.items, title = gdlRes.pageTitle)
                        finishSuccess(itemsMap.size)
                        return@launch
                    }
                }

                // Route 4: Generic Webpage -> Static HTML Scraper
                updateStatus("Parsing webpage HTML & DOM...", ExtractionPhase.STATIC_SCRAPING)
                val scraper = StaticMediaScraper()
                val staticRes = withContext(Dispatchers.IO) {
                    runCatching { scraper.scrape(requestContext.url) }.getOrNull()
                }

                if (staticRes != null && staticRes.items.isNotEmpty()) {
                    updateItems(staticRes.items, title = staticRes.pageTitle)
                    finishSuccess(itemsMap.size)
                    return@launch
                }

                // Route 5: Dynamic JavaScript Fallback (only when static extraction found 0 items)
                updateStatus("Probing dynamic page content...", ExtractionPhase.BROWSER_INTERCEPTING)
                val browserRes = BrowserSession.extractHeadless(
                    context = context,
                    targetUrl = requestContext.url,
                    onStatusUpdate = { s -> launch { updateStatus(s) } },
                    onMediaDiscovered = { item -> launch { updateItems(listOf(item)) } }
                )

                if (browserRes.items.isNotEmpty()) {
                    updateItems(browserRes.items, title = browserRes.pageTitle)
                    finishSuccess(itemsMap.size)
                } else {
                    val finalReason = browserRes.errorMessage ?: staticRes?.errorMessage ?: "No media discovered on this page."
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
                _state.value = _state.value.copy(
                    isRunning = false,
                    phase = ExtractionPhase.ERROR,
                    statusMessage = "Extraction failed: ${e.localizedMessage}",
                    errorMessage = e.localizedMessage
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

    private fun isDirectMediaUrl(url: String): Boolean {
        if (!MediaClassifier.isValidMedia(url)) return false
        val classification = MediaClassifier.classify(url)
        return classification.isVideo || classification.isAudio || classification.isStream ||
                url.endsWith(".jpg", true) || url.endsWith(".jpeg", true) ||
                url.endsWith(".png", true) || url.endsWith(".webp", true) ||
                url.endsWith(".gif", true) || url.endsWith(".svg", true)
    }

    private fun isVideoPlatformUrl(lower: String): Boolean {
        val videoHosts = listOf(
            "youtube.com", "youtu.be", "vimeo.com", "tiktok.com", "dailymotion.com",
            "twitch.tv", "soundcloud.com", "bandcamp.com", "facebook.com", "instagram.com"
        )
        return videoHosts.any { lower.contains(it) } || lower.contains(".m3u8") || lower.contains(".mpd")
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
