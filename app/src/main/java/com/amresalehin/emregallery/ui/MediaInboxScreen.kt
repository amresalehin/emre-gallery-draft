package com.amresalehin.emregallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.amresalehin.emregallery.data.MediaCategory
import com.amresalehin.emregallery.data.MediaClassifier
import com.amresalehin.emregallery.data.MediaInboxItem
import com.amresalehin.emregallery.data.MediaInboxRepository
import com.amresalehin.emregallery.data.MediaInboxStack
import com.amresalehin.emregallery.data.MediaScraper
import com.amresalehin.emregallery.data.ScrapedMediaItem
import kotlinx.coroutines.launch
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInboxScreen(
    onBack: () -> Unit,
    onOpenMediaItem: ((String) -> Unit)? = null,
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
    var sortOrder by remember { mutableStateOf("Newest") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = !showFullScreenBrowser) {
        if (showScraperDrawer) {
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
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 80.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // If there are real image stacks, render the real masonry grid
                if (filteredStacks.isNotEmpty()) {
                    item {
                        val leftStacks = filteredStacks.filterIndexed { idx, _ -> idx % 2 == 0 }
                        val rightStacks = filteredStacks.filterIndexed { idx, _ -> idx % 2 == 1 }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                leftStacks.forEach { stack ->
                                    ImageStackCard(
                                        stack = stack,
                                        onClick = { selectedStackForDetail = stack },
                                        onDelete = {
                                            inboxRepo.deleteStack(stack.id)
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Stack removed from Inbox")
                                            }
                                        }
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rightStacks.forEach { stack ->
                                    ImageStackCard(
                                        stack = stack,
                                        onClick = { selectedStackForDetail = stack },
                                        onDelete = {
                                            inboxRepo.deleteStack(stack.id)
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Stack removed from Inbox")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Skeleton wireframe layout matching Screenshot 1 empty state
                    item {
                        SkeletonWireframeGrid()
                    }
                }
            }

            // Top Search Bar matching Screenshot 1 ("Find in Inbox")
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
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

    // Detail Dialog for an Image Stack
    selectedStackForDetail?.let { stack ->
        AlertDialog(
            onDismissRequest = { selectedStackForDetail = null },
            title = {
                Text(
                    text = stack.title.ifBlank { stack.sourceDomain.ifBlank { "Media Stack" } },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Contains ${stack.items.size} ${if (stack.items.size == 1) "media file" else "media files"} from ${stack.sourceDomain.ifBlank { "web" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // First item preview
                    val firstItem = stack.items.firstOrNull()
                    if (firstItem != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        ) {
                            AsyncImage(
                                model = firstItem.localUri,
                                contentDescription = firstItem.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // List item names
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        stack.items.take(4).forEach { itm ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (itm.isVideo) Icons.Default.PlayArrow else Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = itm.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val firstItem = stack.items.firstOrNull()
                        selectedStackForDetail = null
                        if (firstItem != null && onOpenMediaItem != null) {
                            onOpenMediaItem(firstItem.localUri)
                        }
                    }
                ) {
                    Text("View Media")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        inboxRepo.deleteStack(stack.id)
                        selectedStackForDetail = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Stack")
                }
            }
        )
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
            onClose = { showFullScreenBrowser = false },
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
 * Image Stack Card displaying real downloaded media in stacked format
 */
@Composable
fun ImageStackCard(
    stack: MediaInboxStack,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val firstItem = stack.items.firstOrNull()
    val dateStr = remember(stack.timestampMillis) {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(stack.timestampMillis))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("inbox_image_stack_${stack.id}")
    ) {
        // Stack visual effect: Subtle layered card underneath to look like an image stack
        if (stack.items.size > 1) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 4.dp, end = 4.dp)
                    .height(180.dp)
            ) {}
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Media thumbnail container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    if (firstItem != null) {
                        AsyncImage(
                            model = firstItem.localUri,
                            contentDescription = stack.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Stack Count Badge in top right corner (indicates image stack)
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Collections,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${stack.items.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
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
    onClose: () -> Unit,
    onDownloadComplete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val inboxRepo = remember { MediaInboxRepository.getInstance(context) }
    val scraper = remember { MediaScraper() }
    val scope = rememberCoroutineScope()

    var currentUrl by remember { mutableStateOf("") }
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
                discoveredCount = discoveredItems.size,
                onMediaExtracted = { item ->
                    val key = MediaClassifier.canonicalKey(item.url)
                    if (discoveredItems.none { MediaClassifier.canonicalKey(it.url) == key }) {
                        discoveredItems = discoveredItems + item
                    }
                },
                onBatchExtracted = { batch, title, domain ->
                    title?.let { if (it.isNotBlank()) pageTitle = it }
                    val existingKeys = discoveredItems.map { MediaClassifier.canonicalKey(it.url) }.toSet()
                    val newItems = batch.filter { MediaClassifier.canonicalKey(it.url) !in existingKeys }
                    if (newItems.isNotEmpty()) {
                        discoveredItems = discoveredItems + newItems
                    }
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
                            downloadProgress = 0f
                            scope.launch {
                                val toDownload = filteredItems.filter { it.id in selectedItemIds }
                                val downloadedInboxItems = mutableListOf<MediaInboxItem>()
                                toDownload.forEachIndexed { idx, itm ->
                                    downloadProgress = (idx + 0.3f) / toDownload.size.toFloat()
                                    val file = scraper.downloadMediaFile(context, itm.url, itm.title)
                                    if (file != null) {
                                        downloadedInboxItems.add(
                                            MediaInboxItem(
                                                id = "item_${System.currentTimeMillis()}_$idx",
                                                title = itm.title,
                                                localUri = file.absolutePath,
                                                sourceUrl = itm.url,
                                                isVideo = itm.isVideo,
                                                isAudio = itm.isAudio,
                                                isStream = itm.isStream,
                                                format = itm.format,
                                                sizeBytes = file.length()
                                            )
                                        )
                                    }
                                    downloadProgress = (idx + 1f) / toDownload.size.toFloat()
                                }
                                isDownloading = false
                                if (downloadedInboxItems.isNotEmpty()) {
                                    val domain = runCatching { URL(currentUrl).host }.getOrDefault("Web")
                                    val newStack = MediaInboxStack(
                                        id = "stack_${System.currentTimeMillis()}",
                                        title = pageTitle.ifBlank { domain.ifBlank { "Browser Download" } },
                                        sourceDomain = domain,
                                        sourceUrl = currentUrl,
                                        items = downloadedInboxItems
                                    )
                                    inboxRepo.addStack(newStack)
                                    showDiscoveredMediaSheet = false
                                    onDownloadComplete(downloadedInboxItems.size)
                                }
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
