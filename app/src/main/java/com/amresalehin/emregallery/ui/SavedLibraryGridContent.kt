package com.amresalehin.emregallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.MediaInboxStack

/**
 * Saved Library 2-column grid displaying active download skeleton placeholders,
 * stacked media cards with multi-selection support, or an empty state surface.
 */
@Composable
internal fun SavedLibraryGridContent(
    filteredStacks: List<MediaInboxStack>,
    isDownloading: Boolean,
    selectedStackIds: Set<String>,
    isSelectionMode: Boolean,
    onStackClick: (MediaInboxStack, Boolean) -> Unit,
    onStackLongClick: (MediaInboxStack, Boolean) -> Unit,
    onDeleteStack: (MediaInboxStack) -> Unit,
    modifier: Modifier = Modifier
) {
    if (filteredStacks.isNotEmpty() || isDownloading) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 20.dp)
                .testTag("inbox_media_grid"),
            contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isDownloading) {
                item(key = "downloading_skeleton_card_item") {
                    DownloadingSkeletonCard()
                }
            }
            itemsIndexed(filteredStacks, key = { _, stack -> stack.id }) { index, stack ->
                val isSelected = stack.id in selectedStackIds
                ImageStackCard(
                    stack = stack,
                    accentIndex = index,
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode,
                    onClick = { onStackClick(stack, isSelected) },
                    onLongClick = { onStackLongClick(stack, isSelected) },
                    onDelete = { onDeleteStack(stack) }
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("inbox_empty_state")
        )
    }
}
