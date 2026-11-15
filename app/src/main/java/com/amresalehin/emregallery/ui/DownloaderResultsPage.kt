package com.amresalehin.emregallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Dedicated Downloader Page that opens when the user enters or pastes a URL and chooses "Fetch".
 * Coordinates the results controller, top bar, filter drawers, and results grid body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderResultsPage(
    targetUrl: String,
    onBack: () -> Unit,
    onOpenBrowser: ((String) -> Unit)? = null,
    onDownloadComplete: (Int) -> Unit,
    onViewSavedLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (controller, observed) = rememberDownloaderResultsController(targetUrl)

    val summary = remember(
        observed.allScrapedItems,
        controller.selectedFilter,
        controller.sortOption,
        controller.minWidthFilter,
        controller.minHeightFilter,
        controller.showSvg,
        controller.minKbInput,
        controller.dynamicDimensions.size
    ) {
        filterAndSortScrapedResults(
            allItems = observed.allScrapedItems,
            selectedFilter = controller.selectedFilter,
            sortOption = controller.sortOption,
            minWidthFilter = controller.minWidthFilter,
            minHeightFilter = controller.minHeightFilter,
            showSvg = controller.showSvg,
            minKbInput = controller.minKbInput,
            dynamicDimensions = controller.dynamicDimensions
        )
    }

    val filteredItems = summary.filteredItems
    val isAllSelected = filteredItems.isNotEmpty() &&
        controller.selectedItemIds.containsAll(filteredItems.map { it.id })

    BackHandler {
        when {
            controller.isEditingUrl -> controller.cancelUrlEdit()
            controller.showDimensionFilter -> controller.showDimensionFilter = false
            else -> onBack()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("downloader_results_page")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DownloaderResultsTopBar(
                cleanDisplayUrl = controller.cleanDisplayUrl,
                isEditingUrl = controller.isEditingUrl,
                urlTextFieldValue = controller.urlTextFieldValue,
                isExtracting = observed.isExtracting,
                focusRequester = controller.focusRequester,
                onBackClick = {
                    if (controller.isEditingUrl) controller.cancelUrlEdit() else onBack()
                },
                onStartEditingUrl = controller::startEditingUrl,
                onUrlTextFieldValueChange = { controller.urlTextFieldValue = it },
                onSubmitEditedUrl = controller::submitEditedUrl,
                onClearOrCancelEdit = controller::clearOrCancelEdit,
                onRefresh = controller::refreshExtraction,
                onOpenInBrowser = { controller.openExternalOrLiveBrowser(onOpenBrowser) }
            )

            DownloaderCategoryToolbar(
                categories = summary.categoryTabs,
                selectedFilter = controller.selectedFilter,
                onSelectFilter = { controller.selectedFilter = it },
                sortOption = controller.sortOption,
                sortDropdownExpanded = controller.sortDropdownExpanded,
                onSortDropdownExpandedChange = { controller.sortDropdownExpanded = it },
                onSelectSortOption = { controller.sortOption = it },
                hasActiveDimensionFilter = controller.minWidthFilter > 0 || controller.minHeightFilter > 0 || controller.showSvg,
                onToggleDimensionFilter = { controller.showDimensionFilter = !controller.showDimensionFilter },
                isAllSelected = isAllSelected,
                onToggleSelectAll = { controller.toggleSelectAll(filteredItems, isAllSelected) }
            )

            DownloaderDimensionFilterDrawer(
                visible = controller.showDimensionFilter,
                minWidthFilter = controller.minWidthFilter,
                minHeightFilter = controller.minHeightFilter,
                showSvg = controller.showSvg,
                onMinWidthChange = { controller.minWidthFilter = it },
                onMinHeightChange = { controller.minHeightFilter = it },
                onShowSvgChange = { controller.showSvg = it },
                onResetFilters = controller::resetDimensionFilters
            )

            DownloaderResultsBody(
                filteredItems = filteredItems,
                isExtracting = observed.isExtracting,
                selectedItemIds = controller.selectedItemIds,
                finishedIds = observed.finishedIds,
                finishedMediaUrls = observed.finishedMediaUrls,
                dynamicDimensions = controller.dynamicDimensions,
                isDownloading = controller.isDownloading,
                isDownloaded = controller.isDownloaded,
                onToggleItem = controller::toggleSelection,
                onOpenInLiveBrowser = { onOpenBrowser?.invoke(controller.activeUrl) },
                onDownloadSelected = {
                    controller.enqueueSelectedDownloads(
                        filteredItems = filteredItems,
                        pageTitle = observed.pageTitle,
                        domain = observed.domain,
                        onDownloadComplete = onDownloadComplete
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}
