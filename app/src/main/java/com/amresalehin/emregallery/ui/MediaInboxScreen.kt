package com.amresalehin.emregallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Root coordinator for the Media Inbox & Downloader flow, routing between
 * Search, Scraper Results, Saved Library, Activity feed, and overlay sheets.
 */
@Composable
fun MediaInboxScreen(
    onBack: () -> Unit,
    onOpenMediaItem: ((String) -> Unit)? = null,
    onOpenMediaWithList: ((String, List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val controller = rememberMediaInboxController()
    val filteredStacks = controller.filteredStacks

    BackHandler(enabled = !controller.showFullScreenBrowser) {
        controller.handleBackPress(onBack)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("media_inbox_screen")
    ) {
        if (controller.currentViewMode == DownloaderViewMode.DOWNLOADER_PAGE && controller.activeFetchUrl.isNotBlank()) {
            DownloaderResultsPage(
                targetUrl = controller.activeFetchUrl,
                onBack = { controller.currentViewMode = DownloaderViewMode.SEARCH_PAGE },
                onOpenBrowser = controller::openBrowser,
                onDownloadComplete = { /* Silent foreground download with no toast */ },
                onViewSavedLibrary = { controller.currentViewMode = DownloaderViewMode.SAVED_LIBRARY },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(controller.snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    if (controller.currentViewMode == DownloaderViewMode.SAVED_LIBRARY) {
                        val allSelected = filteredStacks.isNotEmpty() &&
                            filteredStacks.all { it.id in controller.selectedStackIds }
                        MediaInboxTopBar(
                            isSelectionMode = controller.isSelectionMode,
                            selectedCount = controller.selectedStackIds.size,
                            allSelected = allSelected,
                            currentViewMode = controller.currentViewMode,
                            searchQuery = controller.searchQuery,
                            isSearchFocused = controller.isSearchFocused,
                            onExitSelection = { controller.selectedStackIds = emptySet() },
                            onToggleSelectAll = controller::toggleSelectAllFiltered,
                            onDeleteSelected = { controller.showDeleteConfirmationDialog = true },
                            onSearchQueryChange = { controller.searchQuery = it },
                            onSearchFocusChange = { controller.isSearchFocused = it },
                            onClearSearchFocus = controller::clearSearchFocus,
                            onHideKeyboard = controller::hideKeyboard,
                            onSwitchViewMode = { controller.currentViewMode = it },
                            onShowFilterSheet = { controller.showFilterSheet = true }
                        )
                    }
                },
                floatingActionButton = {
                    if (controller.currentViewMode == DownloaderViewMode.SAVED_LIBRARY && !controller.isSelectionMode) {
                        FloatingActionButton(
                            onClick = { controller.currentViewMode = DownloaderViewMode.SEARCH_PAGE },
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                            modifier = Modifier
                                .padding(bottom = 16.dp, end = 8.dp)
                                .size(56.dp)
                                .testTag("media_inbox_fab_add")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Media Download",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (controller.currentViewMode) {
                        DownloaderViewMode.SEARCH_PAGE -> {
                            DownloaderSearchMainPage(
                                onFetchUrl = controller::openFetchResults,
                                onOpenBrowser = controller::openBrowser,
                                onViewSavedLibrary = { controller.currentViewMode = DownloaderViewMode.SAVED_LIBRARY },
                                onOpenActivity = { controller.currentViewMode = DownloaderViewMode.ACTIVITY_PAGE },
                                onBack = onBack,
                                savedStacks = controller.stacks,
                                onSelectStack = { stack -> controller.selectedStackForDetail = stack },
                                activeDownloadsCount = controller.activeDownloadsCount,
                                savedStacksCount = controller.stacks.size,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        DownloaderViewMode.ACTIVITY_PAGE -> {
                            DownloaderActivityPage(
                                onBack = { controller.currentViewMode = DownloaderViewMode.SEARCH_PAGE },
                                onAddNewDownload = { controller.currentViewMode = DownloaderViewMode.SEARCH_PAGE },
                                onOpenMedia = { item ->
                                    item.localUri?.let { uriStr ->
                                        onOpenMediaItem?.invoke(uriStr)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            SavedLibraryGridContent(
                                filteredStacks = filteredStacks,
                                isDownloading = controller.isDownloading,
                                selectedStackIds = controller.selectedStackIds,
                                isSelectionMode = controller.isSelectionMode,
                                onStackClick = controller::onStackClick,
                                onStackLongClick = controller::onStackLongClick,
                                onDeleteStack = controller::deleteSingleStack
                            )
                        }
                    }
                }
            }
        }

        MediaInboxOverlayHost(
            controller = controller,
            onOpenMediaItem = onOpenMediaItem,
            onOpenMediaWithList = onOpenMediaWithList
        )
    }
}
