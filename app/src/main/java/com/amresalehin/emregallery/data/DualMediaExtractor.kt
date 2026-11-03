package com.amresalehin.emregallery.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URL

/**
 * Dual Media Extraction Engine Coordinator.
 * Merges high-speed static HTML parsing with live Android WebView browser network interception,
 * deep DOM scanning, computed CSS extraction, and automatic scrolling.
 */
class DualMediaExtractor(private val context: Context) {

    private val staticScraper = StaticMediaScraper()
    private val browserExtractor = BrowserMediaExtractor(context)
    private var extractionJob: Job? = null

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

        val normalizedUrl = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            "https://$trimmed"
        } else {
            trimmed
        }

        val domain = runCatching { URL(normalizedUrl).host }.getOrDefault("")

        _state.value = ExtractionState(
            isRunning = true,
            statusMessage = "Starting dual-engine extraction...",
            phase = ExtractionPhase.STATIC_SCRAPING,
            domain = domain,
            items = emptyList()
        )

        extractionJob = scope.launch(Dispatchers.Main) {
            val itemsMap = LinkedHashMap<String, ScrapedMediaItem>()

            fun updateItems(newList: List<ScrapedMediaItem>, title: String? = null, msg: String? = null, phase: ExtractionPhase? = null) {
                var modified = false
                for (item in newList) {
                    val key = MediaClassifier.canonicalKey(item.url)
                    if (!itemsMap.containsKey(key)) {
                        itemsMap[key] = item
                        modified = true
                    }
                }
                if (modified || msg != null || phase != null || title != null) {
                    _state.value = _state.value.copy(
                        items = itemsMap.values.toList(),
                        pageTitle = title ?: _state.value.pageTitle,
                        statusMessage = msg ?: _state.value.statusMessage,
                        phase = phase ?: _state.value.phase
                    )
                }
            }

            // Phase 1: Rapid Static HTML / DOM Scraping
            _state.value = _state.value.copy(
                statusMessage = "Fast HTML scan: parsing metadata, CSS & srcset...",
                phase = ExtractionPhase.STATIC_SCRAPING
            )

            val staticResult = staticScraper.scrape(normalizedUrl)
            if (staticResult.items.isNotEmpty()) {
                updateItems(
                    newList = staticResult.items,
                    title = staticResult.pageTitle.ifBlank { domain },
                    msg = "Fast scan found ${staticResult.items.size} media items. Starting live browser...",
                    phase = ExtractionPhase.BROWSER_INTERCEPTING
                )
            }

            // Phase 2: Live Browser Assisted Network Interception & DOM Auto-scroll
            _state.value = _state.value.copy(
                statusMessage = "Live Browser: Intercepting network requests (HLS, DASH, MP4)...",
                phase = ExtractionPhase.BROWSER_INTERCEPTING,
                pageTitle = if (_state.value.pageTitle.isBlank()) staticResult.pageTitle.ifBlank { domain } else _state.value.pageTitle
            )

            browserExtractor.startLiveExtraction(
                targetUrl = normalizedUrl,
                onStatusUpdate = { status ->
                    val phase = if (status.contains("Auto-scrolling", ignoreCase = true)) {
                        ExtractionPhase.AUTO_SCROLLING
                    } else {
                        ExtractionPhase.BROWSER_INTERCEPTING
                    }
                    _state.value = _state.value.copy(
                        statusMessage = status,
                        phase = phase
                    )
                },
                onMediaDiscovered = { discoveredItem ->
                    updateItems(listOf(discoveredItem))
                },
                onFinished = {
                    val count = itemsMap.size
                    _state.value = _state.value.copy(
                        isRunning = false,
                        phase = ExtractionPhase.COMPLETED,
                        statusMessage = if (count > 0) "Extraction completed. Discovered $count media items." else "No media discovered on this page.",
                        errorMessage = if (count == 0 && staticResult.errorMessage != null) staticResult.errorMessage else null
                    )
                }
            )
        }
    }

    fun addDiscoveredItems(newList: List<ScrapedMediaItem>, title: String? = null, domain: String? = null) {
        val existingMap = LinkedHashMap<String, ScrapedMediaItem>()
        for (item in _state.value.items) {
            existingMap[MediaClassifier.canonicalKey(item.url)] = item
        }
        var modified = false
        for (item in newList) {
            val key = MediaClassifier.canonicalKey(item.url)
            if (!existingMap.containsKey(key)) {
                existingMap[key] = item
                modified = true
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
        browserExtractor.cleanup()
        if (_state.value.isRunning) {
            _state.value = _state.value.copy(
                isRunning = false,
                phase = ExtractionPhase.IDLE,
                statusMessage = "Extraction stopped"
            )
        }
    }

    fun cleanup() {
        cancel()
    }
}
