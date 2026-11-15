package com.amresalehin.emregallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.DownloadActivityItem

@Composable
internal fun DownloaderActivityFeed(
    selectedFilter: ActivityFilter,
    downloadingItems: List<DownloadActivityItem>,
    failedItems: List<DownloadActivityItem>,
    finishedItems: List<DownloadActivityItem>,
    isFilteredEmpty: Boolean,
    currentTimeMillis: Long,
    onPauseResume: (DownloadActivityItem) -> Unit,
    onRetry: (DownloadActivityItem) -> Unit,
    onDelete: (DownloadActivityItem) -> Unit,
    onOpenMedia: ((DownloadActivityItem) -> Unit)?
) {
    if (isFilteredEmpty) {
        ActivityEmptyState(selectedFilter = selectedFilter)
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if ((selectedFilter == ActivityFilter.ALL || selectedFilter == ActivityFilter.DOWNLOADING) &&
            downloadingItems.isNotEmpty()
        ) {
            items(downloadingItems, key = { "active_${it.id}" }) { item ->
                ActiveDownloadRow(
                    item = item,
                    currentTimeMillis = currentTimeMillis,
                    onPauseResume = { onPauseResume(item) }
                )
            }
        }

        if ((selectedFilter == ActivityFilter.ALL || selectedFilter == ActivityFilter.FAILED) &&
            failedItems.isNotEmpty()
        ) {
            items(failedItems, key = { "failed_${it.id}" }) { item ->
                FailedDownloadRow(
                    item = item,
                    currentTimeMillis = currentTimeMillis,
                    onRetry = { onRetry(item) },
                    onDelete = { onDelete(item) }
                )
            }
        }

        if ((selectedFilter == ActivityFilter.ALL || selectedFilter == ActivityFilter.FINISHED) &&
            finishedItems.isNotEmpty()
        ) {
            item(key = "past_downloads_header") {
                Text(
                    text = "Past downloads",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
            }
            items(finishedItems, key = { "finished_${it.id}" }) { item ->
                FinishedDownloadRow(
                    item = item,
                    onClick = { onOpenMedia?.invoke(item) }
                )
            }
        }
    }
}

@Composable
internal fun ActivityEmptyState(selectedFilter: ActivityFilter) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(52.dp)
            )
            Text(
                text = when (selectedFilter) {
                    ActivityFilter.DOWNLOADING -> "No active downloads"
                    ActivityFilter.FAILED -> "No failed downloads"
                    ActivityFilter.FINISHED -> "No past downloads"
                    ActivityFilter.ALL -> "No download activity yet"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Add media links to start downloading.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
