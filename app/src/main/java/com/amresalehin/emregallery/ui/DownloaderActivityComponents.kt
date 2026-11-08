package com.amresalehin.emregallery.ui
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.DownloadActivityItem
import com.amresalehin.emregallery.data.DownloadActivityManager
import kotlinx.coroutines.delay
fun DownloaderActivityPage(
    onBack: () -> Unit,
    onAddNewDownload: () -> Unit,
    onOpenMedia: ((DownloadActivityItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        DownloadActivityManager.initialize(context)
        DownloadActivityManager.syncWithStorageDir(context)
        DownloadActivityManager.syncWithInboxRepository()
    }
    val activities by DownloadActivityManager.activities.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    var showControlsSheet by remember { mutableStateOf(false) }
    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activities) {
        if (activities.any { it.isRetrying }) {
            while (true) {
                delay(500L)
                currentTimeMillis = System.currentTimeMillis()
            }
        }
    }
    BackHandler { onBack() }
    val downloadingItems = remember(activities) {
        activities.filter { it.isDownloading || it.isPaused || it.isRetrying }.distinctBy { it.id }
    }
    val failedItems = remember(activities) {
        activities.filter { it.isFailed }.distinctBy { it.id }
    }
    val finishedItems = remember(activities) {
        activities.filter { it.isFinished }.distinctBy { it.id }
    }
    val filteredList = remember(activities, selectedFilter, downloadingItems, failedItems, finishedItems) {
        when (selectedFilter) {
            "Downloading" -> downloadingItems
            "Failed" -> failedItems
            "Finished" -> finishedItems
            else -> activities.distinctBy { it.id }
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("downloader_activity_page"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(44.dp).testTag("activity_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = { showControlsSheet = true },
                        modifier = Modifier.size(44.dp).testTag("activity_controls_tune_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Activity Controls",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
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
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("activity_filter_pills_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val filters = listOf(
                    "All" to activities.size,
                    "Downloading" to downloadingItems.size,
                    "Failed" to failedItems.size,
                    "Finished" to finishedItems.size
                )
                items(filters) { (name, count) ->
                    val isSelected = selectedFilter == name
                    val labelText = if (name == "All") name else "$name ($count)"
                    Surface(
                        onClick = { selectedFilter = name },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                        },
                        border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)) else null,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = labelText,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.surface
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (filteredList.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (selectedFilter == "All" || selectedFilter == "Downloading") {
                        if (downloadingItems.isNotEmpty()) {
                            itemsIndexed(downloadingItems, key = { index, item -> "active_${item.id}_$index" }) { _, item ->
                                ActiveDownloadRow(
                                    item = item,
                                    currentTimeMillis = currentTimeMillis,
                                    onPauseResume = {
                                        if (item.isPaused) {
                                            DownloadActivityManager.resumeDownload(context, item.id)
                                        } else {
                                            DownloadActivityManager.pauseDownload(context, item.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    if (selectedFilter == "All" || selectedFilter == "Failed") {
                        if (failedItems.isNotEmpty()) {
                            itemsIndexed(failedItems, key = { index, item -> "failed_${item.id}_$index" }) { _, item ->
                                FailedDownloadRow(
                                    item = item,
                                    currentTimeMillis = currentTimeMillis,
                                    onRetry = {
                                        DownloadActivityManager.retryDownload(context, item.id)
                                    },
                                    onDelete = {
                                        DownloadActivityManager.deleteActivity(item.id)
                                    }
                                )
                            }
                        }
                    }
                    if (selectedFilter == "All" || selectedFilter == "Finished") {
                        if (finishedItems.isNotEmpty()) {
                            item(key = "past_downloads_header_label") {
                                Text(
                                    text = "Past downloads",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                )
                            }
                            itemsIndexed(finishedItems, key = { index, item -> "finished_${item.id}_$index" }) { _, item ->
                                FinishedDownloadRow(
                                    item = item,
                                    onClick = {
                                        onOpenMedia?.invoke(item)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
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
                                "Downloading" -> "No active downloads"
                                "Failed" -> "No failed downloads"
                                "Finished" -> "No past downloads"
                                else -> "No download activity yet"
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
        }
    }
    DownloaderControlsBottomSheet(
        context = context,
        showControlsSheet = showControlsSheet,
        onDismiss = { showControlsSheet = false },
        failedItems = failedItems,
        downloadingItems = downloadingItems,
        finishedItems = finishedItems
    )
}
