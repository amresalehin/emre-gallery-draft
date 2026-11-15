package com.amresalehin.emregallery.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.amresalehin.emregallery.data.DownloadQueueManager
import com.amresalehin.emregallery.data.MediaCategory
import com.amresalehin.emregallery.data.MediaDeduplicator
import com.amresalehin.emregallery.data.MediaQualityEvaluator
import com.amresalehin.emregallery.data.ScrapedMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URL

internal val BROWSER_MEDIA_FILTER_OPTIONS = listOf("Images", "Videos", "Audio", "Streams")

/**
 * Coordinates browser navigation state, media discovery deduplication/quality upgrades,
 * category filtering, multi-selection, and batch download enqueueing for [FullScreenBrowserView].
 */
@Stable
internal class FullScreenBrowserController(
    initialUrl: String,
    private val context: Context,
    private val scope: CoroutineScope,
    private val onDownloadComplete: (Int) -> Unit
) {
    var currentUrl by mutableStateOf(initialUrl)
    var pageTitle by mutableStateOf("")
    var discoveredItems by mutableStateOf<List<ScrapedMediaItem>>(emptyList())
    var showDiscoveredMediaSheet by mutableStateOf(false)

    var selectedItemIds by mutableStateOf<Set<String>>(emptySet())
    var selectedFilter by mutableStateOf<String?>(null)
    var isDownloading by mutableStateOf(false)
    var downloadProgress by mutableFloatStateOf(0f)

    val filteredItems: List<ScrapedMediaItem>
        get() = when (selectedFilter) {
            "Images" -> discoveredItems.filter { it.category == MediaCategory.IMAGES }
            "Videos" -> discoveredItems.filter { it.category == MediaCategory.VIDEOS }
            "Audio" -> discoveredItems.filter { it.category == MediaCategory.AUDIO }
            "Streams" -> discoveredItems.filter { it.category == MediaCategory.STREAMS }
            else -> discoveredItems
        }

    fun onMediaExtracted(item: ScrapedMediaItem) {
        val key = MediaDeduplicator.canonicalKey(item.url)
        val existingIndex = discoveredItems.indexOfFirst {
            MediaDeduplicator.canonicalKey(it.url) == key
        }
        if (existingIndex == -1) {
            discoveredItems = discoveredItems + item
        } else {
            val existing = discoveredItems[existingIndex]
            if (MediaQualityEvaluator.compareQuality(item, existing) > 0) {
                val updated = discoveredItems.toMutableList()
                updated[existingIndex] = item.copy(
                    id = existing.id,
                    thumbnailUrl = item.thumbnailUrl ?: existing.thumbnailUrl
                )
                discoveredItems = updated
            }
        }
    }

    fun onBatchExtracted(batch: List<ScrapedMediaItem>, title: String?) {
        title?.let { if (it.isNotBlank()) pageTitle = it }
        discoveredItems = MediaDeduplicator.deduplicateScrapedItems(discoveredItems + batch)
    }

    fun autoSelectIfNeeded() {
        if (discoveredItems.isNotEmpty() && selectedItemIds.isEmpty()) {
            selectedItemIds = discoveredItems.map { it.id }.toSet()
        }
    }

    fun toggleFilter(filter: String) {
        selectedFilter = if (selectedFilter == filter) null else filter
    }

    fun toggleSelectAllFiltered() {
        val currentFilteredIds = filteredItems.map { it.id }.toSet()
        val isAllSelected = filteredItems.isNotEmpty() && selectedItemIds.containsAll(currentFilteredIds)
        selectedItemIds = if (isAllSelected) {
            selectedItemIds - currentFilteredIds
        } else {
            selectedItemIds + currentFilteredIds
        }
    }

    fun setItemSelected(itemId: String, selected: Boolean) {
        selectedItemIds = if (selected) {
            selectedItemIds + itemId
        } else {
            selectedItemIds - itemId
        }
    }

    fun downloadSelected() {
        if (selectedItemIds.isEmpty() || isDownloading) return
        isDownloading = true
        downloadProgress = 0.2f
        scope.launch {
            val toDownload = filteredItems.filter { it.id in selectedItemIds }
            val domain = runCatching { URL(currentUrl).host }.getOrDefault("Web")
            val stackId = "stack_${System.currentTimeMillis()}"
            val stackTitle = pageTitle.ifBlank { domain.ifBlank { "Browser Download" } }

            DownloadQueueManager.enqueueBatch(
                context = context,
                items = toDownload,
                referer = currentUrl,
                stackId = stackId,
                stackTitle = stackTitle,
                sourceDomain = domain,
                sourceUrl = currentUrl
            )

            downloadProgress = 1.0f
            delay(300)
            isDownloading = false
            showDiscoveredMediaSheet = false
            onDownloadComplete(toDownload.size)
        }
    }
}

@Composable
internal fun rememberFullScreenBrowserController(
    initialUrl: String,
    onDownloadComplete: (Int) -> Unit
): FullScreenBrowserController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val controller = remember(initialUrl) {
        FullScreenBrowserController(
            initialUrl = initialUrl,
            context = context,
            scope = scope,
            onDownloadComplete = onDownloadComplete
        )
    }

    LaunchedEffect(controller.discoveredItems.size) {
        controller.autoSelectIfNeeded()
    }

    return controller
}
