package com.amresalehin.emregallery.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Multi-Provider Media Extraction Engine Coordinator.
 * Coordinates [DirectMediaProvider], [GalleryDlProvider], [YtDlpProvider], [StaticHtmlProvider],
 * and [BrowserProvider] independently, progressively merging discovered items via [DiscoveredMediaMerger].
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
        if (!WebUrlUtils.isValidWebUrl(trimmed)) {
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
                val modified = DiscoveredMediaMerger.mergeProgressively(itemsMap, newList)
                if (modified || msg != null || phase != null || title != null) {
                    val currentItems = itemsMap.values.toList()
                    _state.value = _state.value.copy(
                        items = currentItems,
                        pageTitle = if (_state.value.pageTitle.isBlank() && !title.isNullOrBlank()) title else _state.value.pageTitle,
                        statusMessage = msg ?: _state.value.statusMessage,
                        phase = phase ?: _state.value.phase
                    )
                    DownloaderSession.addDiscoveredItems(currentItems)
                    if (!title.isNullOrBlank()) {
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

                updateStatus("Searching media with concurrent engines...", ExtractionPhase.BROWSER_INTERCEPTING)

                val galleryDlProvider = GalleryDlProvider()
                val ytDlpProvider = YtDlpProvider()
                val staticHtmlProvider = StaticHtmlProvider()
                val browserProvider = BrowserProvider(context)
                activeBrowserProvider = browserProvider

                try {
                    coroutineScope {
                        val gdlDeferred = launchIoProviderAsync(
                            provider = galleryDlProvider,
                            requestContext = requestContext,
                            tag = "gallery-dl extraction",
                            onItem = { updateItems(listOf(it)) },
                            onStatus = { updateStatus(it) }
                        )
                        val ytdlpDeferred = launchIoProviderAsync(
                            provider = ytDlpProvider,
                            requestContext = requestContext,
                            tag = "yt-dlp extraction",
                            onItem = { updateItems(listOf(it)) },
                            onStatus = { updateStatus(it) }
                        )
                        val staticDeferred = launchIoProviderAsync(
                            provider = staticHtmlProvider,
                            requestContext = requestContext,
                            tag = "StaticHtmlProvider",
                            checkApplicable = false,
                            onItem = { updateItems(listOf(it)) },
                            onStatus = { updateStatus(it) }
                        )
                        val browserDeferred = async {
                            try {
                                browserProvider.extract(
                                    context = requestContext,
                                    onItemDiscovered = { item -> launch { updateItems(listOf(item)) } },
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

                        listOf(gdlDeferred, ytdlpDeferred, staticDeferred, browserDeferred).forEach { deferred ->
                            val result = deferred.await()
                            if (result != null && result.items.isNotEmpty()) {
                                updateItems(result.items, title = result.pageTitle)
                            }
                        }
                    }
                } finally {
                    browserProvider.cleanup()
                    activeBrowserProvider = null
                }

                itemsMutex.withLock {
                    val finalCount = itemsMap.size
                    _state.value = _state.value.copy(
                        isRunning = false,
                        phase = ExtractionPhase.COMPLETED,
                        statusMessage = if (finalCount > 0) "Extraction completed. Discovered $finalCount media items." else "No media discovered on this page."
                    )
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

    private fun CoroutineScope.launchIoProviderAsync(
        provider: ExtractionProvider,
        requestContext: MediaRequestContext,
        tag: String,
        checkApplicable: Boolean = true,
        onItem: suspend (ScrapedMediaItem) -> Unit,
        onStatus: suspend (String) -> Unit
    ): Deferred<MediaExtractionResult?> = async(Dispatchers.IO) {
        try {
            if (!checkApplicable || provider.isApplicable(requestContext)) {
                provider.extract(
                    context = requestContext,
                    onItemDiscovered = { item -> launch { onItem(item) } },
                    onStatusUpdate = { status -> launch { onStatus(status) } }
                )
            } else {
                null
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w("DualMediaExtractor", "$tag error", e)
            null
        }
    }

    @Synchronized
    fun addDiscoveredItems(newList: List<ScrapedMediaItem>, title: String? = null, domain: String? = null) {
        val mergedItems = DiscoveredMediaMerger.mergeBrowserBatch(_state.value.items, newList)
        if (mergedItems != null || title != null || domain != null) {
            val resultItems = mergedItems ?: _state.value.items
            _state.value = _state.value.copy(
                items = resultItems,
                pageTitle = title ?: _state.value.pageTitle,
                domain = domain ?: _state.value.domain,
                statusMessage = "Discovered ${resultItems.size} media items from Browser"
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
