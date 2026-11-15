package com.amresalehin.emregallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.ScrapedMediaItem

@Composable
internal fun DownloaderResultsBody(
    filteredItems: List<ScrapedMediaItem>,
    isExtracting: Boolean,
    selectedItemIds: Set<String>,
    finishedIds: Set<String>,
    finishedMediaUrls: Set<String>,
    dynamicDimensions: Map<String, Pair<Int, Int>>,
    isDownloading: Boolean,
    isDownloaded: Boolean,
    onToggleItem: (String) -> Unit,
    onOpenInLiveBrowser: () -> Unit,
    onDownloadSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when {
            filteredItems.isNotEmpty() -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                        .testTag("downloader_results_grid"),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        DownloaderMediaCard(
                            item = item,
                            isSelected = item.id in selectedItemIds,
                            isDownloaded = item.id in finishedIds || item.url in finishedMediaUrls,
                            dynamicDimensions = dynamicDimensions,
                            onToggle = { onToggleItem(item.id) }
                        )
                    }
                }
            }
            isExtracting -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp)
                ) {
                    item {
                        ThreeColumnSkeletonGrid()
                    }
                }
            }
            else -> {
                DownloaderEmptyResultsState(
                    onOpenInLiveBrowser = onOpenInLiveBrowser
                )
            }
        }

        DownloaderFloatingActionPill(
            selectedCount = selectedItemIds.size,
            isDownloading = isDownloading,
            isDownloaded = isDownloaded,
            onClick = onDownloadSelected,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
