package com.amresalehin.emregallery.ui

import android.net.Uri
import com.amresalehin.emregallery.GalleryApplication
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.work.WorkInfo
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.DownloadQueueManager
import com.amresalehin.emregallery.data.MediaCategory
import com.amresalehin.emregallery.data.MediaNormalizer
import com.amresalehin.emregallery.data.ScrapedMediaItem
import kotlinx.coroutines.launch
import java.net.URL

enum class DownloaderViewMode {
    SEARCH_PAGE,
    DOWNLOADER_PAGE,
    ACTIVITY_PAGE
}

/**
 * Downloader-only surface. Saved web content belongs to Pocket and is no longer
 * rendered or managed from the downloader destination.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInboxScreen(
    onBack: () -> Unit,
    initialViewMode: DownloaderViewMode = DownloaderViewMode.SEARCH_PAGE,
    initialOpenBrowser: Boolean = false,
    onOpenMediaItem: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val workInfos by remember(context) {
        DownloadQueueManager.getActiveDownloadsFlow(context)
    }.collectAsState(initial = emptyList())
    val activeDownloads = remember(workInfos) {
        workInfos.count { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    }
    var currentViewMode by remember(initialViewMode) {
        mutableStateOf(if (initialViewMode == DownloaderViewMode.DOWNLOADER_PAGE) DownloaderViewMode.SEARCH_PAGE else initialViewMode)
    }
    var showFullScreenBrowser by remember(initialOpenBrowser) { mutableStateOf(initialOpenBrowser) }
    var browserInitialUrl by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        GalleryApplication.warmupChromium(context)
    }

    BackHandler(enabled = !showFullScreenBrowser) {
        if (currentViewMode != DownloaderViewMode.SEARCH_PAGE) {
            currentViewMode = DownloaderViewMode.SEARCH_PAGE
        } else {
            onBack()
        }
    }

    Box(modifier.fillMaxSize().testTag("media_inbox_screen")) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                when (currentViewMode) {
                    DownloaderViewMode.SEARCH_PAGE -> DownloaderSearchMainPage(
                        onFetchUrl = { url ->
                            browserInitialUrl = url
                            showFullScreenBrowser = true
                        },
                        onOpenBrowser = { url ->
                            browserInitialUrl = url
                            showFullScreenBrowser = true
                        },
                        onViewSavedLibrary = {},
                        onOpenActivity = { currentViewMode = DownloaderViewMode.ACTIVITY_PAGE },
                        onBack = onBack,
                        savedStacks = emptyList(),
                        onSelectStack = {},
                        activeDownloadsCount = activeDownloads,
                        savedStacksCount = 0,
                        modifier = Modifier.fillMaxSize()
                    )
                    DownloaderViewMode.ACTIVITY_PAGE -> DownloaderActivityPage(
                        onBack = { currentViewMode = DownloaderViewMode.SEARCH_PAGE },
                        onAddNewDownload = { currentViewMode = DownloaderViewMode.SEARCH_PAGE },
                        onOpenMedia = { item -> item.localUri?.let { onOpenMediaItem?.invoke(it) } },
                        modifier = Modifier.fillMaxSize()
                    )
                    DownloaderViewMode.DOWNLOADER_PAGE -> {
                        currentViewMode = DownloaderViewMode.SEARCH_PAGE
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showFullScreenBrowser,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize().zIndex(25f)
        ) {
            FullScreenBrowserView(
                initialUrl = browserInitialUrl,
                onClose = {
                    showFullScreenBrowser = false
                    browserInitialUrl = ""
                },
                onDownloadComplete = { count ->
                    scope.launch { snackbarHostState.showSnackbar("Queued $count media downloads") }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Full-screen in-app Chromium browser launched from the Media Downloader page.
 * Allows web browsing and auto-detects downloadable media on any visited site.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenBrowserView(
    initialUrl: String = "",
    onClose: () -> Unit,
    onDownloadComplete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val inboxRepo = remember { MediaInboxRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    var currentUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("") }
    var discoveredItems by remember { mutableStateOf<List<ScrapedMediaItem>>(emptyList()) }
    var showDiscoveredMediaSheet by remember { mutableStateOf(false) }

    var selectedItemIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    // Intercept back button when modal sheet is open or to close browser
    BackHandler {
        if (showDiscoveredMediaSheet) {
            showDiscoveredMediaSheet = false
        } else {
            onClose()
        }
    }

    // Auto-select newly discovered items if none explicitly selected
    LaunchedEffect(discoveredItems.size) {
        if (discoveredItems.isNotEmpty() && selectedItemIds.isEmpty()) {
            selectedItemIds = discoveredItems.map { it.id }.toSet()
        }
    }

    val filterOptions = listOf("Images", "Videos", "Audio", "Streams")
    val filteredItems = remember(discoveredItems, selectedFilter) {
        when (selectedFilter) {
            "Images" -> discoveredItems.filter { it.category == MediaCategory.IMAGES }
            "Videos" -> discoveredItems.filter { it.category == MediaCategory.VIDEOS }
            "Audio" -> discoveredItems.filter { it.category == MediaCategory.AUDIO }
            "Streams" -> discoveredItems.filter { it.category == MediaCategory.STREAMS }
            else -> discoveredItems
        }
    }

    Surface(
        modifier = modifier.testTag("full_screen_browser_view"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // DirectBrowserView taking full screen with its unified, interactive toolbar
            DirectBrowserView(
                initialUrl = currentUrl,
                onClose = onClose,
                onUrlChange = { currentUrl = it },
                onPageTitleChanged = { pageTitle = it },
                onMediaExtracted = { item ->
                    discoveredItems = MediaNormalizer.deduplicateList(discoveredItems + item)
                },
                onBatchExtracted = { batch, title, domain ->
                    title?.let { if (it.isNotBlank()) pageTitle = it }
                    discoveredItems = MediaNormalizer.deduplicateList(discoveredItems + batch)
                },
                onSwitchToMediaTab = {
                    showDiscoveredMediaSheet = true
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Modal Sheet for Discovered Media in Full Screen Browser
        if (showDiscoveredMediaSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDiscoveredMediaSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val isAllSelected = filteredItems.isNotEmpty() &&
                            selectedItemIds.containsAll(filteredItems.map { it.id }.toSet())

                    // Pinned Filter & Action Row: Category Chips with Icons + Pinned Select All
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .testTag("discovered_media_filters_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category filter chips using icons instead of texts to save space
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(filterOptions) { filter ->
                                val isSelected = selectedFilter == filter
                                val count = when (filter) {
                                    "Images" -> discoveredItems.count { it.category == MediaCategory.IMAGES }
                                    "Videos" -> discoveredItems.count { it.category == MediaCategory.VIDEOS }
                                    "Audio" -> discoveredItems.count { it.category == MediaCategory.AUDIO }
                                    "Streams" -> discoveredItems.count { it.category == MediaCategory.STREAMS }
                                    else -> 0
                                }
                                val icon = when (filter) {
                                    "Images" -> Icons.Default.Image
                                    "Videos" -> Icons.Default.Videocam
                                    "Audio" -> Icons.Default.Audiotrack
                                    "Streams" -> Icons.Default.LiveTv
                                    else -> Icons.Default.PermMedia
                                }

                                Surface(
                                    onClick = {
                                        selectedFilter = if (selectedFilter == filter) null else filter
                                    },
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("discovered_filter_chip_$filter")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = "$filter ($count)",
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "$count",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        // Pinned Select All / Deselect Pill matching scraper drawer
                        Surface(
                            onClick = {
                                val currentFilteredIds = filteredItems.map { it.id }.toSet()
                                selectedItemIds = if (isAllSelected) {
                                    selectedItemIds - currentFilteredIds
                                } else {
                                    selectedItemIds + currentFilteredIds
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("discovered_select_all_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = if (isAllSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    border = BorderStroke(
                                        1.2.dp,
                                        if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.size(14.dp)
                                ) {
                                    if (isAllSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isAllSelected) "Deselect" else "Select All",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Media items list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredItems) { item ->
                            val isSelected = item.id in selectedItemIds
                            Surface(
                                onClick = {
                                    selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Thumbnail / Icon preview
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        if (item.category == MediaCategory.IMAGES) {
                                            AsyncImage(
                                                model = item.thumbnailUrl ?: item.url,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                Icon(
                                                    imageVector = when {
                                                        item.isStream -> Icons.Default.LiveTv
                                                        item.isVideo -> Icons.Default.Videocam
                                                        item.isAudio -> Icons.Default.Audiotrack
                                                        else -> Icons.Default.Image
                                                    },
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.category.label} • ${item.format}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedItemIds = if (checked) selectedItemIds + item.id else selectedItemIds - item.id
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Download Button
                    Button(
                        onClick = {
                            if (selectedItemIds.isEmpty() || isDownloading) return@Button
                            isDownloading = true
                            downloadProgress = 0.2f
                            scope.launch {
                                val toDownload = filteredItems.filter { it.id in selectedItemIds }
                                val domain = runCatching { URL(currentUrl).host }.getOrDefault("Web")
                                val stackId = "stack_${System.currentTimeMillis()}"
                                val stackTitle = pageTitle.ifBlank { domain.ifBlank { "Browser Download" } }

                                // Persistently download using WorkManager
                                com.amresalehin.emregallery.data.DownloadQueueManager.enqueueBatch(
                                    context = context,
                                    items = toDownload,
                                    referer = currentUrl,
                                    stackId = stackId,
                                    stackTitle = stackTitle,
                                    sourceDomain = domain,
                                    sourceUrl = currentUrl
                                )

                                downloadProgress = 1.0f
                                kotlinx.coroutines.delay(300)
                                isDownloading = false
                                showDiscoveredMediaSheet = false
                                onDownloadComplete(toDownload.size)
                            }
                        },
                        enabled = selectedItemIds.isNotEmpty() && !isDownloading,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                progress = { downloadProgress },
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Downloading (${(downloadProgress * 100).toInt()}%)...")
                        } else {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Download (${selectedItemIds.size}) to Inbox")
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
