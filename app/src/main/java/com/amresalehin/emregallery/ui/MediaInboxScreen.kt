package com.amresalehin.emregallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.MediaCategory
import com.amresalehin.emregallery.data.MediaClassifier
import com.amresalehin.emregallery.data.MediaInboxItem
import com.amresalehin.emregallery.data.MediaInboxRepository
import com.amresalehin.emregallery.data.MediaInboxStack
import com.amresalehin.emregallery.data.MediaScraper
import com.amresalehin.emregallery.data.OkHttpDownloader
import com.amresalehin.emregallery.data.ScrapedMediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInboxScreen(
    onBack: () -> Unit,
    onOpenMediaItem: ((String) -> Unit)? = null,
    onOpenMediaWithList: ((String, List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val inboxRepo = remember { MediaInboxRepository.getInstance(context) }
    val stacks by inboxRepo.stacks.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedStackForDetail by remember { mutableStateOf<MediaInboxStack?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showScraperDrawer by remember { mutableStateOf(false) }
    var showFullScreenBrowser by remember { mutableStateOf(false) }
    var browserInitialUrl by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf("Newest") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedStackIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedStackIds.isNotEmpty()
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = !showFullScreenBrowser) {
        if (isSelectionMode) {
            selectedStackIds = emptySet()
        } else if (showScraperDrawer) {
            showScraperDrawer = false
        } else if (selectedStackForDetail != null) {
            selectedStackForDetail = null
        } else if (showFilterSheet) {
            showFilterSheet = false
        } else {
            onBack()
        }
    }

    // Filter stacks based on search query
    val filteredStacks = remember(stacks, searchQuery, sortOrder) {
        val base = if (searchQuery.isBlank()) {
            stacks
        } else {
            stacks.filter { stack ->
                stack.title.contains(searchQuery, ignoreCase = true) ||
                        stack.sourceDomain.contains(searchQuery, ignoreCase = true) ||
                        stack.items.any { it.title.contains(searchQuery, ignoreCase = true) }
            }
        }
        if (sortOrder == "Newest") {
            base.sortedByDescending { it.timestampMillis }
        } else {
            base.sortedBy { it.timestampMillis }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("media_inbox_screen")
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!isSelectionMode) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
                ) {
                    // Browser Button above the plus button
                    FloatingActionButton(
                        onClick = { showFullScreenBrowser = true },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("media_inbox_fab_browser")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Open In-App Browser",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Floating Action Button (+) matching Screenshot 1
                    FloatingActionButton(
                        onClick = { showScraperDrawer = true },
                        shape = RoundedCornerShape(18.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("media_inbox_fab_add")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Open Media Downloader",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (filteredStacks.isNotEmpty()) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 80.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 14.dp
                ) {
                    items(filteredStacks, key = { it.id }) { stack ->
                        val isSelected = stack.id in selectedStackIds
                        ImageStackCard(
                            stack = stack,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedStackIds = if (isSelected) {
                                        selectedStackIds - stack.id
                                    } else {
                                        selectedStackIds + stack.id
                                    }
                                } else {
                                    selectedStackForDetail = stack
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    selectedStackIds = setOf(stack.id)
                                } else {
                                    selectedStackIds = if (isSelected) {
                                        selectedStackIds - stack.id
                                    } else {
                                        selectedStackIds + stack.id
                                    }
                                }
                            },
                            onDelete = {
                                inboxRepo.deleteStack(stack.id)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Stack removed from Inbox")
                                }
                            }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 80.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        SkeletonWireframeGrid()
                    }
                }
            }

            // Top Bar: Selection Toolbar when selecting, otherwise Find in Inbox Search Bar
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (isSelectionMode) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("inbox_selection_toolbar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 6.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Close/Cancel button
                            IconButton(
                                onClick = { selectedStackIds = emptySet() },
                                modifier = Modifier
                                    .size(42.dp)
                                    .testTag("selection_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Exit selection",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(Modifier.width(4.dp))

                            // Selected count text
                            Text(
                                text = "${selectedStackIds.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("selection_count_text")
                            )

                            // Select all toggle
                            val allSelected = filteredStacks.isNotEmpty() && filteredStacks.all { it.id in selectedStackIds }
                            IconButton(
                                onClick = {
                                    selectedStackIds = if (allSelected) {
                                        emptySet()
                                    } else {
                                        filteredStacks.map { it.id }.toSet()
                                    }
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .testTag("selection_select_all_button")
                            ) {
                                Icon(
                                    imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                    contentDescription = if (allSelected) "Deselect all" else "Select all",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Delete button (in select tool bar keep delete button)
                            IconButton(
                                onClick = { showDeleteConfirmationDialog = true },
                                modifier = Modifier
                                    .size(42.dp)
                                    .testTag("selection_delete_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete selected stacks",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 4.dp,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("inbox_search_pill")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 6.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Back navigation button
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Search icon
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.width(10.dp))

                            // Text input field with "Find in Inbox" placeholder
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Find in Inbox",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("find_in_inbox_input")
                                )
                            }

                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Tune / Filter icon on the right (as seen in Screenshot 1)
                            IconButton(
                                onClick = { showFilterSheet = true },
                                modifier = Modifier.size(42.dp).testTag("inbox_tune_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Sort and filter inbox",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog for selected stacks
    if (showDeleteConfirmationDialog) {
        val count = selectedStackIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = {
                Text(
                    text = if (count == 1) "Delete media stack?" else "Delete $count media stacks?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove ${if (count == 1) "this stack" else "these $count stacks"} from your Inbox?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idsToDelete = selectedStackIds.toList()
                        showDeleteConfirmationDialog = false
                        selectedStackIds = emptySet()
                        idsToDelete.forEach { id ->
                            inboxRepo.deleteStack(id)
                        }
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (idsToDelete.size == 1) "1 stack deleted" else "${idsToDelete.size} stacks deleted"
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmationDialog = false },
                    modifier = Modifier.testTag("cancel_delete_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal Sheet for Sorting / Filtering Inbox
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Inbox Options",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text("Sort Order", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Newest", "Oldest").forEach { order ->
                        FilterChip(
                            selected = sortOrder == order,
                            onClick = { sortOrder = order },
                            label = { Text(order) }
                        )
                    }
                }

                HorizontalDivider()

                if (stacks.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            inboxRepo.clearAll()
                            showFilterSheet = false
                            scope.launch { snackbarHostState.showSnackbar("All inbox stacks cleared") }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Clear All Inbox Stacks")
                    }
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showFilterSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }

    // Stack Detail Tab with stacked card carousel and bottom drawer (replacing modal)
    AnimatedVisibility(
        visible = selectedStackForDetail != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.fillMaxSize().zIndex(20f)
    ) {
        selectedStackForDetail?.let { stack ->
            MediaStackDetailTab(
                stack = stack,
                onClose = { selectedStackForDetail = null },
                onOpenMedia = { uri ->
                    val allUris = stack.items.map { it.localUri.ifBlank { it.sourceUrl } }
                    if (onOpenMediaWithList != null) {
                        onOpenMediaWithList(uri, allUris)
                    } else {
                        onOpenMediaItem?.invoke(uri)
                    }
                },
                onOpenBrowser = { url ->
                    browserInitialUrl = url
                    showFullScreenBrowser = true
                },
                onDeleteStack = {
                    inboxRepo.deleteStack(stack.id)
                    selectedStackForDetail = null
                    scope.launch {
                        snackbarHostState.showSnackbar("Stack removed from Inbox")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Media Scraper Drawer opening from below up to 80% of screen height with 20% scrim at top
    AnimatedVisibility(
        visible = showScraperDrawer,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // 20% Top Scrim area (covers entire background behind sheet, dismisses drawer on click)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showScraperDrawer = false
                    }
                    .testTag("scraper_scrim")
            )

            // Scraper Drawer with top floating bar and bottom 80% sheet
            MediaScraperDrawer(
                onClose = {
                    showScraperDrawer = false
                },
                onDownloadComplete = {
                    showScraperDrawer = false
                    scope.launch {
                        snackbarHostState.showSnackbar("Media stack added to Inbox!")
                    }
                },
                onOpenBrowser = { url ->
                    browserInitialUrl = url
                    showFullScreenBrowser = true
                    showScraperDrawer = false
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Full Screen In-App Browser (opens when clicking browser button above plus FAB)
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
                scope.launch {
                    snackbarHostState.showSnackbar("Downloaded $count media items to Inbox!")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
    }
}

/**
 * True Mosaic Thumbnail component for media stacks.
 * Supports:
 * - 0 items: Empty placeholder
 * - 1 item: Full image
 * - 2 items: 2 equal vertical columns (50% / 50%) side-by-side
 * - 3 items: 1 tall on left (58%), 2 stacked on right (42%)
 * - 4+ items: 2x2 quadrant grid with +N counter on the 4th quadrant
 */
@Composable
fun TrueMediaMosaicThumbnail(
    items: List<MediaInboxItem>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        when {
            items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            items.size == 1 -> {
                AsyncImage(
                    model = items[0].localUri,
                    contentDescription = items[0].title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            items.size == 2 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AsyncImage(
                        model = items[0].localUri,
                        contentDescription = items[0].title,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentScale = ContentScale.Crop
                    )
                    AsyncImage(
                        model = items[1].localUri,
                        contentDescription = items[1].title,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            items.size == 3 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AsyncImage(
                        model = items[0].localUri,
                        contentDescription = items[0].title,
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight(),
                        contentScale = ContentScale.Crop
                    )
                    Column(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        AsyncImage(
                            model = items[1].localUri,
                            contentDescription = items[1].title,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentScale = ContentScale.Crop
                        )
                        AsyncImage(
                            model = items[2].localUri,
                            contentDescription = items[2].title,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        AsyncImage(
                            model = items[0].localUri,
                            contentDescription = items[0].title,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                        AsyncImage(
                            model = items[1].localUri,
                            contentDescription = items[1].title,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        AsyncImage(
                            model = items[2].localUri,
                            contentDescription = items[2].title,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            AsyncImage(
                                model = items[3].localUri,
                                contentDescription = items[3].title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (items.size > 4) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.58f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+${items.size - 3}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Image Stack Card displaying real downloaded media in True Mosaic format
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageStackCard(
    stack: MediaInboxStack,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = remember(stack.timestampMillis) {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(stack.timestampMillis))
    }
    val hasVideo = remember(stack.items) { stack.items.any { it.isVideo || it.isStream } }

    val cardThumbnailHeight = remember(stack.items) {
        when {
            stack.items.size == 1 && (stack.items[0].isVideo || stack.items[0].isStream) -> 145.dp
            stack.items.size == 1 -> 175.dp
            stack.items.size == 2 -> 160.dp
            stack.items.size == 3 -> 180.dp
            else -> 190.dp
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("inbox_image_stack_${stack.id}")
    ) {
        // Stack visual effect: Subtle layered card underneath to look like an image stack
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp, start = 4.dp, end = 4.dp)
                .height(cardThumbnailHeight + 60.dp)
        ) {}

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                    } else Modifier
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = if (isSelected) 6.dp else 3.dp
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Media thumbnail container with True Mosaic
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardThumbnailHeight)
                ) {
                    TrueMediaMosaicThumbnail(
                        items = stack.items,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Video Play Icon overlay if stack has video
                    if (hasVideo) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Stack Count Badge in top right corner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (hasVideo) Icons.Default.VideoLibrary else Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${stack.items.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Selection Indicator in top left corner
                    if (isSelectionMode) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f),
                            border = BorderStroke(
                                2.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                            ),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .size(28.dp)
                                .testTag("stack_selection_check_${stack.id}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Source domain tag in bottom left
                    if (stack.sourceDomain.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = stack.sourceDomain,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Info text lines
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = stack.title.ifBlank { stack.sourceDomain.ifBlank { "Media Stack" } },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$dateStr • ${stack.items.size} ${if (stack.items.size == 1) "item" else "items"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Detail Tab with stacked card carousel and lower drawer (matches screenshots)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaStackDetailTab(
    stack: MediaInboxStack,
    onClose: () -> Unit,
    onOpenMedia: (String) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onDeleteStack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDrawerExpanded by remember { mutableStateOf(true) }

    val items = stack.items
    var currentIndex by remember(stack.id) { mutableIntStateOf(0) }
    val activeIndex = currentIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val activeItem = items.getOrNull(activeIndex) ?: items.firstOrNull()

    val dragOffsetX = remember { Animatable(0f) }
    val dragOffsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density

    val formattedDateTime = remember(stack.timestampMillis) {
        SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(stack.timestampMillis))
    }

    val totalBytes = remember(items) { items.sumOf { it.sizeBytes } }
    val formattedTotalSize = remember(totalBytes) {
        when {
            totalBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", totalBytes / (1024f * 1024f))
            totalBytes >= 1024 -> String.format(Locale.US, "%.1f KB", totalBytes / 1024f)
            totalBytes > 0 -> "$totalBytes B"
            else -> "Ready"
        }
    }

    val domain = remember(stack.sourceDomain, stack.sourceUrl) {
        if (stack.sourceDomain.isNotBlank()) {
            stack.sourceDomain
        } else {
            runCatching { URL(stack.sourceUrl).host }.getOrDefault("Web")
        }
    }

    val faviconUrl = remember(domain) {
        "https://www.google.com/s2/favicons?domain=$domain&sz=64"
    }

    val extractedTitle = remember(stack.title, activeItem) {
        if (activeItem != null && activeItem.title.isNotBlank()) {
            activeItem.title
        } else if (stack.title.isNotBlank() && stack.title != "Untitled Stack" && stack.title != "Discovered Media") {
            stack.title
        } else {
            domain
        }
    }

    val extractedDescription = remember(stack.description, activeItem, items, domain, formattedTotalSize) {
        if (activeItem != null) {
            val formatStr = if (activeItem.format.isNotBlank()) "${activeItem.format} format" else "media file"
            val typeStr = if (activeItem.isVideo) "Video" else if (activeItem.isStream) "Live Stream" else "Image"
            val itemSizeStr = if (activeItem.sizeBytes > 0) {
                when {
                    activeItem.sizeBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", activeItem.sizeBytes / (1024f * 1024f))
                    activeItem.sizeBytes >= 1024 -> String.format(Locale.US, "%.1f KB", activeItem.sizeBytes / 1024f)
                    else -> "${activeItem.sizeBytes} B"
                }
            } else formattedTotalSize
            "File ${activeIndex + 1} of ${items.size}: $typeStr in $formatStr ($itemSizeStr) extracted from $domain."
        } else if (stack.description.isNotBlank()) {
            stack.description
        } else {
            "Contains ${items.size} media files extracted from $domain. Total size: $formattedTotalSize."
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("media_stack_detail_tab")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 1. Top Bar with Circular Back Button and Circular More Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Back Button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp,
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("stack_detail_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Circular More Button
                Box {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 3.dp,
                        tonalElevation = 2.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("stack_detail_more_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open in Browser") },
                            leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                            onClick = {
                                menuExpanded = false
                                onOpenBrowser(stack.sourceUrl.ifBlank { "https://$domain" })
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("View in Gallery") },
                            leadingIcon = { Icon(Icons.Default.Visibility, null) },
                            onClick = {
                                menuExpanded = false
                                activeItem?.let { onOpenMedia(it.localUri) }
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete Stack", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                showDeleteConfirmDialog = true
                            }
                        )
                    }
                }
            }

            // 2. Functional Stacked Photo Deck Area (Real images physically coming forward from below stack)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("functional_stacked_photo_deck"),
                    contentAlignment = Alignment.Center
                ) {
                    if (items.isEmpty()) {
                        RealDeckMediaCard(
                            item = null,
                            elevation = 8.dp,
                            modifier = Modifier,
                            onClick = {}
                        )
                    } else {
                        val maxDepth = 3
                        val maxVisibleIndex = (currentIndex + maxDepth).coerceAtMost(items.size - 1)
                        val dragDistance = sqrt(dragOffsetX.value * dragOffsetX.value + dragOffsetY.value * dragOffsetY.value)
                        val swipeProgress = (dragDistance / 350f).coerceIn(0f, 1f)

                        // Render cards in reverse order so the deepest card is at the back, top card on top
                        for (i in maxVisibleIndex downTo currentIndex) {
                            val depth = i - currentIndex
                            val item = items[i]

                            // Dynamic interpolation: as top card is swiped away, card directly below comes forward!
                            val cardScale = when (depth) {
                                0 -> 1.0f
                                1 -> 0.93f + (1.0f - 0.93f) * swipeProgress
                                2 -> 0.86f + (0.93f - 0.86f) * swipeProgress
                                else -> 0.80f + (0.86f - 0.80f) * swipeProgress
                            }

                            val cardRotZ = when (depth) {
                                0 -> dragOffsetX.value * 0.04f
                                1 -> 3.5f * (1f - swipeProgress)
                                2 -> -4.5f + (3.5f - -4.5f) * swipeProgress
                                else -> 5.5f + (-4.5f - 5.5f) * swipeProgress
                            }

                            val cardTransX = when (depth) {
                                0 -> dragOffsetX.value
                                1 -> (14f * density) * (1f - swipeProgress)
                                2 -> (-16f * density) + ((14f * density) - (-16f * density)) * swipeProgress
                                else -> (18f * density) + ((-16f * density) - (18f * density)) * swipeProgress
                            }

                            val cardTransY = when (depth) {
                                0 -> dragOffsetY.value
                                1 -> (8f * density) * (1f - swipeProgress)
                                2 -> (16f * density) + ((8f * density) - (16f * density)) * swipeProgress
                                else -> (24f * density) + ((16f * density) - (24f * density)) * swipeProgress
                            }

                            val cardAlpha = when (depth) {
                                0 -> (1f - swipeProgress * 0.25f).coerceIn(0.75f, 1f)
                                else -> (0.88f + 0.12f * swipeProgress).coerceIn(0.88f, 1f)
                            }

                            val cardElevation = when (depth) {
                                0 -> 16.dp
                                1 -> 10.dp
                                2 -> 6.dp
                                else -> 3.dp
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = cardScale
                                        scaleY = cardScale
                                        rotationZ = cardRotZ
                                        translationX = cardTransX
                                        translationY = cardTransY
                                        alpha = cardAlpha
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                RealDeckMediaCard(
                                    item = item,
                                    elevation = cardElevation,
                                    modifier = if (depth == 0) {
                                        Modifier.pointerInput(currentIndex, items.size) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                var hasMoved = false
                                                var dragDeltaX = 0f
                                                var dragDeltaY = 0f
                                                val touchSlop = viewConfiguration.touchSlop

                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                                    if (change.changedToUp()) {
                                                        change.consume()
                                                        break
                                                    }

                                                    val posDelta = change.positionChange()
                                                    if (posDelta != Offset.Zero) {
                                                        dragDeltaX += posDelta.x
                                                        dragDeltaY += posDelta.y
                                                        val dist = sqrt(dragDeltaX * dragDeltaX + dragDeltaY * dragDeltaY)
                                                        if (dist > touchSlop) {
                                                            hasMoved = true
                                                        }
                                                        if (hasMoved) {
                                                            change.consume()
                                                            scope.launch {
                                                                dragOffsetX.snapTo(dragOffsetX.value + posDelta.x)
                                                                dragOffsetY.snapTo(dragOffsetY.value + posDelta.y)
                                                            }
                                                        }
                                                    }
                                                }

                                                val offsetX = dragOffsetX.value
                                                val offsetY = dragOffsetY.value
                                                val totalDrag = kotlin.math.sqrt(offsetX * offsetX + offsetY * offsetY)

                                                if (!hasMoved || totalDrag < 45f) {
                                                    // Direct tap without swipe threshold: directly land in viewer!
                                                    val targetUri = item.localUri.ifBlank { item.sourceUrl }
                                                    onOpenMedia(targetUri)
                                                } else if ((offsetX < -140f || offsetY < -150f) && currentIndex < items.size - 1) {
                                                    // Swiped left or up: top card exits and card below comes forward to the top!
                                                    scope.launch {
                                                        val exitX = if (abs(offsetX) > 20f) (if (offsetX < 0) -1300f else 1300f) else 0f
                                                        val exitY = if (abs(offsetY) > 20f) (if (offsetY < 0) -1300f else 1300f) else 0f
                                                        launch { dragOffsetX.animateTo(exitX, tween(160)) }
                                                        launch { dragOffsetY.animateTo(exitY, tween(160)) }
                                                        delay(160)
                                                        currentIndex = (currentIndex + 1).coerceAtMost(items.size - 1)
                                                        dragOffsetX.snapTo(0f)
                                                        dragOffsetY.snapTo(0f)
                                                    }
                                                } else if (offsetX > 140f && currentIndex > 0) {
                                                    // Swiped right: bring previous card back
                                                    scope.launch {
                                                        launch { dragOffsetX.animateTo(1300f, tween(160)) }
                                                        delay(160)
                                                        currentIndex = (currentIndex - 1).coerceAtLeast(0)
                                                        dragOffsetX.snapTo(0f)
                                                        dragOffsetY.snapTo(0f)
                                                    }
                                                } else {
                                                    // Snap back to resting position with bouncy spring
                                                    scope.launch {
                                                        launch { dragOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                                                        launch { dragOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // Tapping the card peeking below immediately promotes it to the top!
                                        Modifier.clickable {
                                            scope.launch {
                                                dragOffsetX.snapTo(0f)
                                                dragOffsetY.snapTo(0f)
                                                currentIndex = i
                                            }
                                        }
                                    },
                                    onClick = {
                                        val targetUri = item.localUri.ifBlank { item.sourceUrl }
                                        onOpenMedia(targetUri)
                                    }
                                )
                            }
                        }
                    }
                }

                // Numeric Page Indicator (replaces overflowing dot pagination)
                if (items.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .padding(top = 8.dp, bottom = 10.dp)
                            .testTag("numeric_pagination_indicator")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${activeIndex + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = " / ${items.size}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // 3. Lower Drawer Sheet (occupying lower portion of the screen)
            Surface(
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 20.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stack_detail_lower_drawer")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                        .animateContentSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Drag handle pill at the top (tapping toggles drawer expand/collapse)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 6.dp)
                            .clickable { isDrawerExpanded = !isDrawerExpanded },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        )
                    }

                    // Row 1: Date & Time
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Date and Time",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = formattedDateTime,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Row 2: URL with Favicon and Redirect Button
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Favicon
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(28.dp)
                            ) {
                                AsyncImage(
                                    model = faviconUrl,
                                    contentDescription = "Favicon",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stack.sourceUrl.ifBlank { "https://$domain" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onOpenBrowser(stack.sourceUrl.ifBlank { "https://$domain" })
                                    }
                            )
                            IconButton(
                                onClick = {
                                    onOpenBrowser(stack.sourceUrl.ifBlank { "https://$domain" })
                                },
                                modifier = Modifier.size(32.dp).testTag("stack_detail_redirect_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = "Open in browser",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Rows 3, 4, 5 (visible when expanded)
                    if (isDrawerExpanded) {
                        // Row 3: Extracted Title
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "Title",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = extractedTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Row 4: Extracted Description
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notes,
                                    contentDescription = "Description",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = extractedDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        // Row 5: Tags and Format Chips with + action button matching image_4867641c.png
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalOffer,
                                    contentDescription = "Tags",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    item {
                                        DetailTagChip(label = activeItem?.format?.ifBlank { "MEDIA" } ?: "MEDIA")
                                    }
                                    item {
                                        DetailTagChip(label = if (activeItem?.isVideo == true) "Video" else "Image")
                                    }
                                    item {
                                        DetailTagChip(label = formattedTotalSize)
                                    }
                                    item {
                                        DetailTagChip(label = domain)
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                // Quick Action (+) button matching image_4867641c.png
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clickable {
                                            activeItem?.let { onOpenMedia(it.localUri) }
                                        }
                                        .testTag("detail_tags_add_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Actions",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Stack?") },
            text = { Text("This will remove this stack and its downloaded files from your device.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteStack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Real Media Card for the functional stacked photo deck (renders actual downloaded media items)
 */
@Composable
fun RealDeckMediaCard(
    item: MediaInboxItem?,
    elevation: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 8.dp,
        shadowElevation = elevation,
        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.95f)),
        modifier = modifier
            .fillMaxWidth(0.82f)
            .fillMaxHeight(0.94f)
            .clip(RoundedCornerShape(32.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (item != null) {
                val context = LocalContext.current
                val localFile = remember(item.localUri) {
                    if (item.localUri.isNotBlank()) File(item.localUri.removePrefix("file://")) else null
                }
                val fileExists = localFile?.exists() == true && (localFile.length() > 0L)
                val imageSource: Any = if (fileExists) {
                    localFile!!
                } else if (item.sourceUrl.isNotBlank()) {
                    item.sourceUrl
                } else {
                    item.localUri
                }

                val imageRequest = remember(imageSource) {
                    val rawStr = imageSource.toString()
                    val resolvedUrl = if (rawStr.startsWith("//")) "https:$rawStr" else rawStr
                    val refererHeader = item.sourceUrl.takeIf { it.isNotBlank() }
                        ?: runCatching {
                            val u = java.net.URI(resolvedUrl)
                            "${u.scheme ?: "https"}://${u.host}/"
                        }.getOrNull()
                    val builder = ImageRequest.Builder(context)
                        .data(if (imageSource is File) imageSource else resolvedUrl)
                        .crossfade(true)
                        .setHeader("User-Agent", OkHttpDownloader.DEFAULT_USER_AGENT)
                        .setHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                        .setHeader("Sec-Fetch-Dest", "image")
                        .setHeader("Sec-Fetch-Mode", "no-cors")

                    if (!refererHeader.isNullOrBlank()) {
                        builder.setHeader("Referer", refererHeader)
                    }

                    val cookies = OkHttpDownloader.sharedCookieJar.getCookieString(resolvedUrl)
                        .ifBlank { refererHeader?.let { OkHttpDownloader.sharedCookieJar.getCookieString(it) } }
                    if (!cookies.isNullOrBlank()) {
                        builder.setHeader("Cookie", cookies)
                    }

                    builder.build()
                }

                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Fit
                )

                if (item.isVideo || item.isStream) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = if (item.isStream) "STREAM" else item.format.ifBlank { "VIDEO" },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Landscape,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(96.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DetailTagChip(label: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}

/**
 * Skeleton wireframe grid matching Screenshot 1 layout precisely
 */
@Composable
fun SkeletonWireframeGrid() {
    // Skeletons matching the varied heights from Screenshot 1:
    // Left column: 130dp, 190dp, 130dp, 160dp
    // Right column: 190dp, 120dp, 170dp, 120dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left Column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SkeletonCard(heightDp = 130)
            SkeletonCard(heightDp = 190)
            SkeletonCard(heightDp = 130)
            SkeletonCard(heightDp = 160)
        }

        // Right Column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SkeletonCard(heightDp = 190)
            SkeletonCard(heightDp = 120)
            SkeletonCard(heightDp = 170)
            SkeletonCard(heightDp = 120)
        }
    }
}

/**
 * Single skeleton card matching Screenshot 1 wireframe
 */
@Composable
fun SkeletonCard(heightDp: Int) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Upper thumbnail skeleton block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heightDp.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )

            // Lower info skeleton bars
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Top longer horizontal bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
                // Bottom shorter horizontal bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                )
            }
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
    val scraper = remember { MediaScraper() }
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
                    val key = MediaClassifier.canonicalKey(item.url)
                    val existingIndex = discoveredItems.indexOfFirst { MediaClassifier.canonicalKey(it.url) == key }
                    if (existingIndex == -1) {
                        discoveredItems = discoveredItems + item
                    } else {
                        val existing = discoveredItems[existingIndex]
                        if (MediaClassifier.compareMediaQuality(item, existing) > 0) {
                            val updated = discoveredItems.toMutableList()
                            updated[existingIndex] = item.copy(
                                id = existing.id,
                                thumbnailUrl = item.thumbnailUrl ?: existing.thumbnailUrl
                            )
                            discoveredItems = updated
                        }
                    }
                },
                onBatchExtracted = { batch, title, domain ->
                    title?.let { if (it.isNotBlank()) pageTitle = it }
                    discoveredItems = MediaClassifier.deduplicateScrapedItems(discoveredItems + batch)
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
