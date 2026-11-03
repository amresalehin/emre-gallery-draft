package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.amresalehin.emregallery.data.*
import kotlinx.coroutines.launch

@Composable
fun MediaScraperDrawer(
    onClose: () -> Unit,
    onDownloadComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val inboxRepo = remember { MediaInboxRepository.getInstance(context) }
    val scraper = remember { MediaScraper() }
    val dualExtractor = remember { DualMediaExtractor(context) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            dualExtractor.cleanup()
        }
    }

    val extractorState by dualExtractor.state.collectAsState()

    var selectedDrawerTab by remember { mutableIntStateOf(0) }
    var urlInput by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedItemIds by remember { mutableStateOf(setOf<String>()) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadStatusText by remember { mutableStateOf("") }

    var sortDropdownExpanded by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf("Default") }

    val allScrapedItems = extractorState.items

    // Auto-select newly discovered items if user hasn't explicitly deselected all
    LaunchedEffect(allScrapedItems.size) {
        if (allScrapedItems.isNotEmpty() && selectedItemIds.isEmpty() && !extractorState.isRunning) {
            selectedItemIds = allScrapedItems.map { it.id }.toSet()
        }
    }

    val filterOptions = listOf("All", "Images", "Videos", "Audio", "Streams")

    // Dynamic counts
    val imageCount = remember(allScrapedItems) { allScrapedItems.count { it.category == MediaCategory.IMAGES } }
    val videoCount = remember(allScrapedItems) { allScrapedItems.count { it.category == MediaCategory.VIDEOS } }
    val audioCount = remember(allScrapedItems) { allScrapedItems.count { it.category == MediaCategory.AUDIO } }
    val streamCount = remember(allScrapedItems) { allScrapedItems.count { it.category == MediaCategory.STREAMS } }

    // Filter & Sort
    val filteredScrapedItems = remember(selectedFilter, allScrapedItems, sortOption) {
        val filtered = when (selectedFilter) {
            "Images" -> allScrapedItems.filter { it.category == MediaCategory.IMAGES }
            "Videos" -> allScrapedItems.filter { it.category == MediaCategory.VIDEOS }
            "Audio" -> allScrapedItems.filter { it.category == MediaCategory.AUDIO }
            "Streams" -> allScrapedItems.filter { it.category == MediaCategory.STREAMS }
            else -> allScrapedItems
        }
        when (sortOption) {
            "Title (A-Z)" -> filtered.sortedBy { it.title.lowercase() }
            "Streams First" -> filtered.sortedByDescending { it.isStream }
            "Videos First" -> filtered.sortedByDescending { it.isVideo && !it.isStream }
            "Images First" -> filtered.sortedBy { it.isVideo || it.isAudio || it.isStream }
            else -> filtered
        }
    }

    val isAllSelected = filteredScrapedItems.isNotEmpty() &&
            selectedItemIds.containsAll(filteredScrapedItems.map { it.id })

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("media_scraper_drawer_content")
    ) {
        // 1. Floating Bar hovering at the top of the drawer (in the 20% area above the 80% sheet)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
                .zIndex(10f)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
                shadowElevation = 14.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("floating_paste_link_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Link",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(Modifier.width(12.dp))

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (urlInput.isEmpty()) {
                            Text(
                                text = "Paste a link (URL)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp
                            )
                        }
                        BasicTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scraper_url_input")
                        )
                    }

                    if (urlInput.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                urlInput = ""
                                selectedItemIds = emptySet()
                                dualExtractor.cancel()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }

                    // Fetch button pill matching screenshot
                    Surface(
                        onClick = {
                            if (urlInput.isNotBlank()) {
                                selectedItemIds = emptySet()
                                dualExtractor.extract(urlInput, scope)
                            }
                        },
                        enabled = !extractorState.isRunning && urlInput.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (extractorState.isRunning) Color(0xFF383C4A) else Color(0xFF535868),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("scraper_fetch_button")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (extractorState.isRunning) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "Scan",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Text(
                                    text = "Fetch",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. 80% Drawer Sheet anchored to the bottom
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.80f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* Prevent click through to scrim */ }
                .testTag("media_scraper_bottom_drawer"),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 24.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drag handle pill matching Material 3 bottom sheet styling
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }

                // Two Tab Navigation Bar: Media vs Direct Browser
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .height(42.dp)
                        .testTag("scraper_drawer_tab_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Media Tab Button
                        Surface(
                            onClick = { selectedDrawerTab = 0 },
                            shape = RoundedCornerShape(11.dp),
                            color = if (selectedDrawerTab == 0) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("tab_media_button")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PermMedia,
                                    contentDescription = null,
                                    tint = if (selectedDrawerTab == 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (allScrapedItems.isNotEmpty()) "Media (${allScrapedItems.size})" else "Media",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedDrawerTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedDrawerTab == 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Browser Tab Button
                        Surface(
                            onClick = { selectedDrawerTab = 1 },
                            shape = RoundedCornerShape(11.dp),
                            color = if (selectedDrawerTab == 1) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("tab_browser_button")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = if (selectedDrawerTab == 1) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Browser",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedDrawerTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedDrawerTab == 1) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (selectedDrawerTab == 0) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 110.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 1. Filter Pills + Select All + Sort button ALL IN THE SAME ROW
                                item {
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("media_filters_and_actions_row"),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        items(filterOptions) { filter ->
                                            val isSelected = selectedFilter == filter
                                            val countLabel = when (filter) {
                                                "Images" -> if (imageCount > 0) " ($imageCount)" else ""
                                                "Videos" -> if (videoCount > 0) " ($videoCount)" else ""
                                                "Audio" -> if (audioCount > 0) " ($audioCount)" else ""
                                                "Streams" -> if (streamCount > 0) " ($streamCount)" else ""
                                                else -> if (allScrapedItems.isNotEmpty()) " (${allScrapedItems.size})" else ""
                                            }

                                            Surface(
                                                onClick = { selectedFilter = filter },
                                                shape = CircleShape,
                                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(horizontal = 14.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "$filter$countLabel",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }

                                        item {
                                            // Select All Pill
                                            Surface(
                                                onClick = {
                                                    val currentFilteredIds = filteredScrapedItems.map { it.id }.toSet()
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
                                                    .testTag("scraper_select_all_button")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp),
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
                                                        text = if (isAllSelected) "Deselect All" else "Select All",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }

                                        item {
                                            // Sort Dropdown Pill
                                            Box {
                                                Surface(
                                                    onClick = { sortDropdownExpanded = true },
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                    modifier = Modifier
                                                        .height(34.dp)
                                                        .testTag("scraper_sort_button")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.SwapVert,
                                                            contentDescription = "Sort",
                                                            tint = MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(
                                                            text = if (sortOption != "Default") sortOption else "Sort",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontSize = 12.sp
                                                        )
                                                        Spacer(Modifier.width(2.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.ArrowDropDown,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }

                                                DropdownMenu(
                                                    expanded = sortDropdownExpanded,
                                                    onDismissRequest = { sortDropdownExpanded = false }
                                                ) {
                                                    listOf("Default", "Title (A-Z)", "Streams First", "Videos First", "Images First").forEach { opt ->
                                                        DropdownMenuItem(
                                                            text = { Text(opt) },
                                                            onClick = {
                                                                sortOption = opt
                                                                sortDropdownExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2. 3-Column Grid matching screenshot:
                                item {
                if (filteredScrapedItems.isNotEmpty()) {
                    val col0 = filteredScrapedItems.filterIndexed { idx, _ -> idx % 3 == 0 }
                    val col1 = filteredScrapedItems.filterIndexed { idx, _ -> idx % 3 == 1 }
                    val col2 = filteredScrapedItems.filterIndexed { idx, _ -> idx % 3 == 2 }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            col0.forEach { itm ->
                                RealScrapedCard(
                                    item = itm,
                                    isSelected = itm.id in selectedItemIds,
                                    onToggle = {
                                        selectedItemIds = if (itm.id in selectedItemIds) {
                                            selectedItemIds - itm.id
                                        } else {
                                            selectedItemIds + itm.id
                                        }
                                    }
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            col1.forEach { itm ->
                                RealScrapedCard(
                                    item = itm,
                                    isSelected = itm.id in selectedItemIds,
                                    onToggle = {
                                        selectedItemIds = if (itm.id in selectedItemIds) {
                                            selectedItemIds - itm.id
                                        } else {
                                            selectedItemIds + itm.id
                                        }
                                    }
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            col2.forEach { itm ->
                                RealScrapedCard(
                                    item = itm,
                                    isSelected = itm.id in selectedItemIds,
                                    onToggle = {
                                        selectedItemIds = if (itm.id in selectedItemIds) {
                                            selectedItemIds - itm.id
                                        } else {
                                            selectedItemIds + itm.id
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    ThreeColumnSkeletonGrid()
                }
            }
        }

        // 5. Sticky Floating Bottom Download Button matching screenshot
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Surface(
                onClick = {
                    if (selectedItemIds.isEmpty() || isDownloading) return@Surface
                    isDownloading = true
                    downloadProgress = 0f
                    downloadStatusText = "Preparing download..."
                    scope.launch {
                        val itemsToDownload = filteredScrapedItems.filter { it.id in selectedItemIds }
                        val downloadedInboxItems = mutableListOf<MediaInboxItem>()

                        itemsToDownload.forEachIndexed { index, scrapedItem ->
                            downloadStatusText = "Downloading (${index + 1}/${itemsToDownload.size}): ${scrapedItem.title}"
                            downloadProgress = (index + 0.3f) / itemsToDownload.size.toFloat()
                            val file = scraper.downloadMediaFile(context, scrapedItem.url, scrapedItem.title)
                            if (file != null) {
                                downloadedInboxItems.add(
                                    MediaInboxItem(
                                        id = "item_${System.currentTimeMillis()}_$index",
                                        title = scrapedItem.title,
                                        localUri = file.absolutePath,
                                        sourceUrl = scrapedItem.url,
                                        isVideo = scrapedItem.isVideo,
                                        isAudio = scrapedItem.isAudio,
                                        isStream = scrapedItem.isStream,
                                        format = scrapedItem.format,
                                        sizeBytes = file.length()
                                    )
                                )
                            }
                            downloadProgress = (index + 1f) / itemsToDownload.size.toFloat()
                        }

                        isDownloading = false
                        if (downloadedInboxItems.isNotEmpty()) {
                            val newStack = MediaInboxStack(
                                id = "stack_${System.currentTimeMillis()}",
                                title = extractorState.pageTitle.ifBlank { extractorState.domain.ifBlank { "Discovered Media" } },
                                sourceDomain = extractorState.domain,
                                sourceUrl = urlInput,
                                items = downloadedInboxItems
                            )
                            inboxRepo.addStack(newStack)
                            onDownloadComplete()
                        }
                    }
                },
                enabled = selectedItemIds.isNotEmpty() && !isDownloading,
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF434854),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("scraper_bottom_download_button")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = downloadStatusText.ifBlank { "Downloading (${(downloadProgress * 100).toInt()}%)..." },
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Download",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Download (${selectedItemIds.size})",
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
} else {
        // Tab 1: Direct Browser View
        DirectBrowserView(
            initialUrl = urlInput.ifBlank { "https://duckduckgo.com" },
            discoveredCount = allScrapedItems.size,
            onMediaExtracted = { item ->
                dualExtractor.addDiscoveredItem(item)
            },
            onBatchExtracted = { batch, title, domain ->
                dualExtractor.addDiscoveredItems(batch, title, domain)
            },
            onSwitchToMediaTab = {
                selectedDrawerTab = 0
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
}
}
}
}

/**
 * Real Scraped Media Card displayed in the 3-column grid
 */
@Composable
fun RealScrapedCard(
    item: ScrapedMediaItem,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToggle() }
            .testTag("scraped_media_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                val previewUrl = item.thumbnailUrl ?: item.url
                if (item.category == MediaCategory.AUDIO && item.thumbnailUrl == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF263238)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = Color(0xFF80DEEA),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else {
                    AsyncImage(
                        model = previewUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Center Icon Overlay for Video / Audio / Stream
                if (item.isStream) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.LiveTv,
                                contentDescription = null,
                                tint = Color(0xFFFFB74D),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else if (item.isVideo) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else if (item.isAudio && item.thumbnailUrl != null) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Format & Source Badge Pill in top-left
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (item.category) {
                        MediaCategory.STREAMS -> Color(0xFFE65100).copy(alpha = 0.85f)
                        MediaCategory.VIDEOS -> Color(0xFF4A148C).copy(alpha = 0.85f)
                        MediaCategory.AUDIO -> Color(0xFF006064).copy(alpha = 0.85f)
                        else -> Color(0xFF1A237E).copy(alpha = 0.85f)
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                ) {
                    Text(
                        text = item.format,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // Checkbox Square outline in top-right corner matching screenshot
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f),
                    border = BorderStroke(1.2.dp, Color.White),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.resolutionHint ?: item.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = item.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when (item.category) {
                            MediaCategory.STREAMS -> Color(0xFFFF9800)
                            MediaCategory.VIDEOS -> Color(0xFFBA68C8)
                            MediaCategory.AUDIO -> Color(0xFF4DD0E1)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        fontSize = 8.sp
                    )
                }
            }
        }
    }
}

/**
 * 3-Column Skeleton Grid matching Screenshot 3 wireframe precisely
 */
@Composable
fun ThreeColumnSkeletonGrid() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThreeColumnSkeletonCard(heightDp = 125)
            ThreeColumnSkeletonCard(heightDp = 100)
            ThreeColumnSkeletonCard(heightDp = 115)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThreeColumnSkeletonCard(heightDp = 95)
            ThreeColumnSkeletonCard(heightDp = 135)
            ThreeColumnSkeletonCard(heightDp = 100)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThreeColumnSkeletonCard(heightDp = 115)
            ThreeColumnSkeletonCard(heightDp = 95)
            ThreeColumnSkeletonCard(heightDp = 115)
        }
    }
}

/**
 * Single 3-column skeleton card matching screenshot wireframe:
 * - Rounded rectangle thumbnail
 * - Centered placeholder image icon
 * - Rounded checkbox outline in top-right corner
 * - 2 skeleton horizontal text bars underneath
 */
@Composable
fun ThreeColumnSkeletonCard(heightDp: Int) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFFDFE2EC)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heightDp.dp)
                    .background(Color(0xFFDFE2EC)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = Color(0xFFA5ABC0),
                    modifier = Modifier.size(28.dp)
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.2.dp, Color(0xFF5A6072)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(16.dp)
                ) {}
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.80f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFA5ABC0).copy(alpha = 0.6f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFA5ABC0).copy(alpha = 0.4f))
                )
            }
        }
    }
}
