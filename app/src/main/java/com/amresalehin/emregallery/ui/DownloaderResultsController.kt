package com.amresalehin.emregallery.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.amresalehin.emregallery.data.DownloadActivityManager
import com.amresalehin.emregallery.data.DownloadActivityStatus
import com.amresalehin.emregallery.data.DownloadQueueManager
import com.amresalehin.emregallery.data.DownloaderSession
import com.amresalehin.emregallery.data.DualMediaExtractor
import com.amresalehin.emregallery.data.ImageDimensionProber
import com.amresalehin.emregallery.data.MediaCategory
import com.amresalehin.emregallery.data.MediaDeduplicator
import com.amresalehin.emregallery.data.ScrapedMediaItem
import com.amresalehin.emregallery.data.WebUrlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Stable
internal class DownloaderResultsController(
    targetUrl: String,
    private val context: Context,
    private val scope: CoroutineScope,
    val dualExtractor: DualMediaExtractor,
    val focusRequester: FocusRequester,
    private val focusManager: FocusManager,
    private val keyboardController: SoftwareKeyboardController?
) {
    var selectedFilter by mutableStateOf<String?>(null)
    var selectedItemIds by mutableStateOf(setOf<String>())
    var isDownloading by mutableStateOf(false)
    var isDownloaded by mutableStateOf(false)
    var sortDropdownExpanded by mutableStateOf(false)
    var sortOption by mutableStateOf("Default")

    var activeUrl by mutableStateOf(targetUrl)
    var isEditingUrl by mutableStateOf(false)

    val cleanDisplayUrl: String
        get() = activeUrl.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")

    var urlTextFieldValue by mutableStateOf(
        TextFieldValue(text = cleanDisplayUrl, selection = TextRange(cleanDisplayUrl.length))
    )

    var showDimensionFilter by mutableStateOf(false)
    var minWidthFilter by mutableFloatStateOf(0f)
    var minHeightFilter by mutableFloatStateOf(0f)
    var showSvg by mutableStateOf(false)
    var minKbInput by mutableStateOf("1")

    val dynamicDimensions = mutableStateMapOf<String, Pair<Int, Int>>()

    fun startEditingUrl() {
        if (!isEditingUrl) {
            urlTextFieldValue = TextFieldValue(
                text = cleanDisplayUrl,
                selection = TextRange(cleanDisplayUrl.length)
            )
            isEditingUrl = true
        }
    }

    fun cancelUrlEdit() {
        isEditingUrl = false
        urlTextFieldValue = TextFieldValue(cleanDisplayUrl, selection = TextRange(cleanDisplayUrl.length))
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    fun submitEditedUrl(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isNotBlank()) {
            val sanitized = WebUrlUtils.sanitizeUrl(trimmed)
            activeUrl = sanitized
            selectedItemIds = emptySet()
            isDownloaded = false
            dynamicDimensions.clear()
            dualExtractor.extract(sanitized, scope)
        }
        isEditingUrl = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    fun clearOrCancelEdit() {
        if (urlTextFieldValue.text.isNotEmpty()) {
            urlTextFieldValue = TextFieldValue("", selection = TextRange(0))
        } else {
            cancelUrlEdit()
        }
    }

    fun refreshExtraction() {
        selectedItemIds = emptySet()
        dynamicDimensions.clear()
        dualExtractor.extract(activeUrl, scope)
    }

    fun openExternalOrLiveBrowser(onOpenBrowser: ((String) -> Unit)?) {
        if (onOpenBrowser != null) {
            onOpenBrowser(activeUrl)
        } else {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(activeUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }

    fun toggleSelection(itemId: String) {
        isDownloaded = false
        selectedItemIds = if (itemId in selectedItemIds) {
            selectedItemIds - itemId
        } else {
            selectedItemIds + itemId
        }
    }

    fun toggleSelectAll(filteredItems: List<ScrapedMediaItem>, isAllSelected: Boolean) {
        isDownloaded = false
        val ids = filteredItems.map { it.id }.toSet()
        selectedItemIds = if (isAllSelected) selectedItemIds - ids else selectedItemIds + ids
    }

    fun resetDimensionFilters() {
        minWidthFilter = 0f
        minHeightFilter = 0f
        showSvg = false
        minKbInput = "1"
    }

    fun enqueueSelectedDownloads(
        filteredItems: List<ScrapedMediaItem>,
        pageTitle: String,
        domain: String,
        onDownloadComplete: (Int) -> Unit
    ) {
        if (selectedItemIds.isEmpty() || isDownloading) return
        isDownloading = true
        scope.launch {
            val selectedItems = filteredItems.filter { it.id in selectedItemIds }
            val deduplicated = MediaDeduplicator.deduplicateScrapedItems(selectedItems)
            val stackId = "stack_${System.currentTimeMillis()}"
            val stackTitle = pageTitle.ifBlank { domain.ifBlank { "Media Download" } }

            DownloadQueueManager.enqueueBatch(
                context = context,
                items = deduplicated,
                referer = activeUrl,
                stackId = stackId,
                stackTitle = stackTitle,
                sourceDomain = domain,
                sourceUrl = activeUrl
            )

            delay(300)
            isDownloading = false
            onDownloadComplete(deduplicated.size)
        }
    }
}

internal data class DownloaderResultsObservedData(
    val isExtracting: Boolean,
    val pageTitle: String,
    val domain: String,
    val allScrapedItems: List<ScrapedMediaItem>,
    val finishedIds: Set<String>,
    val finishedMediaUrls: Set<String>
)

@Composable
internal fun rememberDownloaderResultsController(targetUrl: String): Pair<DownloaderResultsController, DownloaderResultsObservedData> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dualExtractor = remember { DualMediaExtractor(context) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val controller = remember(targetUrl) {
        DownloaderResultsController(
            targetUrl = targetUrl,
            context = context,
            scope = scope,
            dualExtractor = dualExtractor,
            focusRequester = focusRequester,
            focusManager = focusManager,
            keyboardController = keyboardController
        )
    }

    DisposableEffect(Unit) {
        onDispose { dualExtractor.cleanup() }
    }

    val extractorState by dualExtractor.state.collectAsState()
    val sessionItems by DownloaderSession.discoveredItems.collectAsState()
    val activities by DownloadActivityManager.activities.collectAsState()

    val finishedMediaUrls = remember(activities) {
        activities.filter { it.status == DownloadActivityStatus.FINISHED }.map { it.mediaUrl }.toSet()
    }
    val finishedIds = remember(activities) {
        activities.filter { it.status == DownloadActivityStatus.FINISHED }.map { it.id }.toSet()
    }

    val allScrapedItems = remember(extractorState.items, sessionItems) {
        MediaDeduplicator.deduplicateScrapedItems(extractorState.items + sessionItems)
    }

    LaunchedEffect(controller.isEditingUrl) {
        if (controller.isEditingUrl) {
            val clean = controller.cleanDisplayUrl
            controller.urlTextFieldValue = TextFieldValue(text = clean, selection = TextRange(clean.length))
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(controller.activeUrl) {
        if (controller.activeUrl.isNotBlank()) {
            controller.selectedItemIds = emptySet()
            controller.isDownloaded = false
            controller.dynamicDimensions.clear()
            DownloaderSession.startSession(controller.activeUrl)
            dualExtractor.extract(controller.activeUrl, scope)
        }
    }

    LaunchedEffect(allScrapedItems.size, extractorState.isRunning) {
        if (allScrapedItems.isNotEmpty() && controller.selectedItemIds.isEmpty() && !extractorState.isRunning) {
            controller.selectedItemIds = allScrapedItems.map { it.id }.toSet()
        }
    }

    LaunchedEffect(allScrapedItems) {
        val itemsToProbe = allScrapedItems.filter { item ->
            item.category == MediaCategory.IMAGES &&
                controller.dynamicDimensions[item.id] == null &&
                parseScrapedMediaDimensions(item).let { (w, h) -> w == null && h == null }
        }
        for (item in itemsToProbe.take(50)) {
            launch {
                val probed = ImageDimensionProber.probe(item.url, referer = controller.activeUrl)
                if (probed != null) {
                    controller.dynamicDimensions[item.id] = probed
                }
            }
        }
    }

    val observed = DownloaderResultsObservedData(
        isExtracting = extractorState.isRunning,
        pageTitle = extractorState.pageTitle,
        domain = extractorState.domain,
        allScrapedItems = allScrapedItems,
        finishedIds = finishedIds,
        finishedMediaUrls = finishedMediaUrls
    )

    return controller to observed
}
