package com.amresalehin.emregallery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.DownloadActivityItem
import com.amresalehin.emregallery.data.DownloadActivityManager
import kotlinx.coroutines.delay

/**
 * Coordinates the Activity screen state, filter tabs, download feed, and global controls sheet.
 */
@Composable
fun DownloaderActivityPage(
    onBack: () -> Unit,
    onAddNewDownload: () -> Unit,
    onOpenMedia: ((DownloadActivityItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        DownloadActivityManager.initialize(context)
        DownloadActivityManager.syncWithInboxRepository()
    }

    val activities by DownloadActivityManager.activities.collectAsState()

    var selectedFilter by remember { mutableStateOf(ActivityFilter.ALL) }
    var showControlsSheet by remember { mutableStateOf(false) }

    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activities) {
        if (activities.any { it.isRetrying }) {
            while (true) {
                delay(500L)
                currentTimeMillis = System.currentTimeMillis()
            }
        }
    }

    val downloadingItems = remember(activities) { activities.filter { it.isDownloading || it.isPaused || it.isRetrying } }
    val failedItems = remember(activities) { activities.filter { it.isFailed } }
    val finishedItems = remember(activities) { activities.filter { it.isFinished } }

    val isFilteredEmpty = remember(activities, selectedFilter, downloadingItems, failedItems, finishedItems) {
        when (selectedFilter) {
            ActivityFilter.DOWNLOADING -> downloadingItems.isEmpty()
            ActivityFilter.FAILED -> failedItems.isEmpty()
            ActivityFilter.FINISHED -> finishedItems.isEmpty()
            ActivityFilter.ALL -> activities.isEmpty()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("downloader_activity_page"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ActivityPageTopBar(
                onBack = onBack,
                onOpenControls = { showControlsSheet = true }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddNewDownload,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp, end = 6.dp)
                    .size(56.dp)
                    .testTag("activity_fab_add")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Download",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ActivityFilterPillsRow(
                selectedFilter = selectedFilter,
                totalCount = activities.size,
                downloadingCount = downloadingItems.size,
                failedCount = failedItems.size,
                finishedCount = finishedItems.size,
                onSelectFilter = { selectedFilter = it }
            )

            Spacer(Modifier.height(8.dp))

            DownloaderActivityFeed(
                selectedFilter = selectedFilter,
                downloadingItems = downloadingItems,
                failedItems = failedItems,
                finishedItems = finishedItems,
                isFilteredEmpty = isFilteredEmpty,
                currentTimeMillis = currentTimeMillis,
                onPauseResume = { item ->
                    if (item.isPaused) {
                        DownloadActivityManager.resumeDownload(context, item.id)
                    } else {
                        DownloadActivityManager.pauseDownload(context, item.id)
                    }
                },
                onRetry = { item -> DownloadActivityManager.retryDownload(context, item.id) },
                onDelete = { item -> DownloadActivityManager.deleteActivity(item.id) },
                onOpenMedia = onOpenMedia
            )
        }
    }

    if (showControlsSheet) {
        ActivityControlsBottomSheet(
            failedCount = failedItems.size,
            hasDownloading = downloadingItems.isNotEmpty(),
            hasFinished = finishedItems.isNotEmpty(),
            onDismiss = { showControlsSheet = false },
            onRetryAllFailed = {
                DownloadActivityManager.retryAllFailed(context)
                showControlsSheet = false
            },
            onCancelAllActive = {
                DownloadActivityManager.cancelAllActive(context)
                showControlsSheet = false
            },
            onClearFinished = {
                DownloadActivityManager.clearFinished()
                showControlsSheet = false
            }
        )
    }
}
