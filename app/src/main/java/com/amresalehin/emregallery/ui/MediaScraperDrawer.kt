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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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

    var urlInput by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<String?>(null) }
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

    val filterOptions = listOf("Images", "Videos", "Audio", "Streams")

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

    val clipboardManager = LocalClipboardManager.current
    val isValidUrl = remember(urlInput) { MediaClassifier.isValidWebUrl(urlInput) }
    val isAllSelected = filteredScrapedItems.isNotEmpty() &&
            selectedItemIds.containsAll(filteredScrapedItems.map { it.id })

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("media_scraper_drawer_content")
    ) {
        // Drawer Sheet anchored to the bottom with embedded search bar, pinned filters, grid, and sticky download button
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
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
                // 1. Drag handle pill matching Material 3 bottom sheet styling
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

                // 2. Search / URL input bar inside the drawer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp,
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
                                tint = if (urlInput.isNotBlank() && !isValidUrl) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Go
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            if (isValidUrl) {
                                                selectedItemIds = emptySet()
                                                dualExtractor.extract(urlInput, scope)
                                            }
                                        }
                                    ),
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

                            // Quick paste from clipboard button
                            if (urlInput.isEmpty()) {
                                IconButton(
                                    onClick = {
                                        val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                                        if (clip.isNotEmpty()) {
                                            urlInput = clip
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste from clipboard",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
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

                            // Fetch button pill - strictly enabled ONLY when URL is valid and extractor is not running
                            Surface(
                                onClick = {
                                    if (isValidUrl) {
                                        selectedItemIds = emptySet()
                                        dualExtractor.extract(urlInput, scope)
                                    }
                                },
                                enabled = !extractorState.isRunning && isValidUrl,
                                shape = RoundedCornerShape(16.dp),
                                color = when {
                                    extractorState.isRunning -> Color(0xFF383C4A)
                                    isValidUrl -> Color(0xFF535868)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
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
                                            color = if (isValidUrl) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // If pasted text is NOT a valid URL, display a clear warning banner
                    AnimatedVisibility(
                        visible = urlInput.isNotBlank() && !isValidUrl,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                            tonalElevation = 4.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Invalid URL. Please enter or paste a valid web link (e.g. https://example.com)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // 3. Pinned Filter Row: Category Chips (scrollable) + Pinned Select All + Pinned Sort
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("media_filters_and_actions_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category filter chips (scrollable)
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(filterOptions) { filter ->
                            val isSelected = selectedFilter == filter
                            val count = when (filter) {
                                "Images" -> imageCount
                                "Videos" -> videoCount
                                "Audio" -> audioCount
                                "Streams" -> streamCount
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
                                    .testTag("scraper_filter_chip_$filter")
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

                    // Pinned Select All Pill
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

                    Spacer(Modifier.width(6.dp))

                    // Pinned Sort Dropdown Pill
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
                                modifier = Modifier.padding(horizontal = 10.dp),
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

                // 4. Media Grid Area + Sticky Bottom Download Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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

                    // 5. Sticky Floating Bottom Download Button (always visible, greyed out in empty/unselected state)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 18.dp)
                    ) {
                        val isDownloadEnabled = selectedItemIds.isNotEmpty() && !isDownloading
                        Surface(
                            onClick = {
                                if (!isDownloadEnabled) return@Surface
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
                            enabled = isDownloadEnabled,
                            shape = RoundedCornerShape(24.dp),
                            color = when {
                                isDownloading -> Color(0xFF383C4A)
                                isDownloadEnabled -> Color(0xFF434854)
                                else -> Color(0xFF262932)
                            },
                            shadowElevation = if (isDownloadEnabled) 6.dp else 0.dp,
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
                                    val contentColor = if (isDownloadEnabled) Color.White else Color(0xFF727888)
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = "Download",
                                        tint = contentColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Download (${selectedItemIds.size})",
                                        color = contentColor,
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
 * 3-Column Skeleton Grid:
 * Rendered in a dull, muted, greyed-out visual style.
 */
@Composable
fun ThreeColumnSkeletonGrid() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(0.40f),
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
 * Single 3-column skeleton card:
 * Low-contrast dull grey container, soft placeholder icon, and muted placeholder text bars.
 */
@Composable
fun ThreeColumnSkeletonCard(heightDp: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heightDp.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f),
                    modifier = Modifier.size(26.dp)
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
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
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                )
            }
        }
    }
}
