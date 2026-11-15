package com.amresalehin.emregallery.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.work.WorkInfo
import com.amresalehin.emregallery.data.DownloadQueueManager
import com.amresalehin.emregallery.data.MediaInboxRepository
import com.amresalehin.emregallery.data.MediaInboxStack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class DownloaderViewMode {
    SEARCH_PAGE,
    DOWNLOADER_PAGE,
    SAVED_LIBRARY,
    ACTIVITY_PAGE
}

/**
 * Coordinates Media Inbox navigation modes, Saved Library search/sort/multi-selection,
 * active download work observation, snackbar feedback, and back-stack precedence.
 */
@Stable
internal class MediaInboxController(
    private val inboxRepo: MediaInboxRepository,
    private val focusManager: FocusManager,
    private val keyboardController: SoftwareKeyboardController?,
    val snackbarHostState: SnackbarHostState,
    private val scope: CoroutineScope
) {
    var stacks by mutableStateOf<List<MediaInboxStack>>(emptyList())
    var workInfos by mutableStateOf<List<WorkInfo>>(emptyList())

    var searchQuery by mutableStateOf("")
    var isSearchFocused by mutableStateOf(false)
    var selectedStackForDetail by mutableStateOf<MediaInboxStack?>(null)
    var showFilterSheet by mutableStateOf(false)
    var showFullScreenBrowser by mutableStateOf(false)
    var browserInitialUrl by mutableStateOf("")
    var sortOrder by mutableStateOf("Newest")
    var selectedStackIds by mutableStateOf(setOf<String>())
    var showDeleteConfirmationDialog by mutableStateOf(false)
    var currentViewMode by mutableStateOf(DownloaderViewMode.SEARCH_PAGE)
    var activeFetchUrl by mutableStateOf("")

    val isSelectionMode: Boolean
        get() = selectedStackIds.isNotEmpty()

    val activeDownloadsCount: Int
        get() = workInfos.count {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
        }

    val isDownloading: Boolean
        get() = activeDownloadsCount > 0

    val filteredStacks: List<MediaInboxStack>
        get() {
            val base = if (searchQuery.isBlank()) {
                stacks
            } else {
                stacks.filter { stack ->
                    stack.title.contains(searchQuery, ignoreCase = true) ||
                        stack.sourceDomain.contains(searchQuery, ignoreCase = true) ||
                        stack.items.any { it.title.contains(searchQuery, ignoreCase = true) }
                }
            }
            return if (sortOrder == "Newest") {
                base.sortedByDescending { it.timestampMillis }
            } else {
                base.sortedBy { it.timestampMillis }
            }
        }

    fun handleBackPress(onNavigateOut: () -> Unit) {
        when {
            isSelectionMode -> selectedStackIds = emptySet()
            isSearchFocused || searchQuery.isNotEmpty() -> clearSearchFocus()
            currentViewMode == DownloaderViewMode.DOWNLOADER_PAGE -> currentViewMode = DownloaderViewMode.SEARCH_PAGE
            currentViewMode == DownloaderViewMode.ACTIVITY_PAGE -> currentViewMode = DownloaderViewMode.SEARCH_PAGE
            selectedStackForDetail != null -> selectedStackForDetail = null
            showFilterSheet -> showFilterSheet = false
            currentViewMode == DownloaderViewMode.SAVED_LIBRARY -> currentViewMode = DownloaderViewMode.SEARCH_PAGE
            else -> onNavigateOut()
        }
    }

    fun clearSearchFocus() {
        searchQuery = ""
        isSearchFocused = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    fun hideKeyboard() {
        keyboardController?.hide()
    }

    fun openFetchResults(targetUrl: String) {
        activeFetchUrl = targetUrl
        currentViewMode = DownloaderViewMode.DOWNLOADER_PAGE
    }

    fun openBrowser(url: String) {
        browserInitialUrl = url
        showFullScreenBrowser = true
    }

    fun closeBrowser() {
        showFullScreenBrowser = false
        browserInitialUrl = ""
    }

    fun toggleSelectAllFiltered() {
        val currentFiltered = filteredStacks
        val allSelected = currentFiltered.isNotEmpty() && currentFiltered.all { it.id in selectedStackIds }
        selectedStackIds = if (allSelected) {
            emptySet()
        } else {
            currentFiltered.map { it.id }.toSet()
        }
    }

    fun onStackClick(stack: MediaInboxStack, isSelected: Boolean) {
        if (isSelectionMode) {
            selectedStackIds = if (isSelected) {
                selectedStackIds - stack.id
            } else {
                selectedStackIds + stack.id
            }
        } else {
            selectedStackForDetail = stack
        }
    }

    fun onStackLongClick(stack: MediaInboxStack, isSelected: Boolean) {
        selectedStackIds = when {
            !isSelectionMode -> setOf(stack.id)
            isSelected -> selectedStackIds - stack.id
            else -> selectedStackIds + stack.id
        }
    }

    fun deleteSingleStack(stack: MediaInboxStack, closeDetail: Boolean = false) {
        inboxRepo.deleteStack(stack.id)
        if (closeDetail) {
            selectedStackForDetail = null
        }
        scope.launch {
            snackbarHostState.showSnackbar("Stack removed from Inbox")
        }
    }

    fun confirmDeleteSelectedStacks() {
        val idsToDelete = selectedStackIds.toList()
        showDeleteConfirmationDialog = false
        selectedStackIds = emptySet()
        idsToDelete.forEach { id -> inboxRepo.deleteStack(id) }
        scope.launch {
            snackbarHostState.showSnackbar(
                if (idsToDelete.size == 1) "1 stack deleted" else "${idsToDelete.size} stacks deleted"
            )
        }
    }

    fun clearAllStacks() {
        inboxRepo.clearAll()
        showFilterSheet = false
        scope.launch {
            snackbarHostState.showSnackbar("All inbox stacks cleared")
        }
    }

    fun notifyBrowserDownloadsComplete(count: Int) {
        scope.launch {
            snackbarHostState.showSnackbar("Downloaded $count media items to Inbox!")
        }
    }
}

@Composable
internal fun rememberMediaInboxController(): MediaInboxController {
    val context = LocalContext.current
    val inboxRepo = remember { MediaInboxRepository.getInstance(context) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val stacks by inboxRepo.stacks.collectAsState()
    val workInfos by remember(context) {
        DownloadQueueManager.getActiveDownloadsFlow(context)
    }.collectAsState(initial = emptyList())

    val controller = remember(inboxRepo, focusManager, keyboardController, snackbarHostState, scope) {
        MediaInboxController(
            inboxRepo = inboxRepo,
            focusManager = focusManager,
            keyboardController = keyboardController,
            snackbarHostState = snackbarHostState,
            scope = scope
        )
    }

    controller.stacks = stacks
    controller.workInfos = workInfos
    return controller
}
