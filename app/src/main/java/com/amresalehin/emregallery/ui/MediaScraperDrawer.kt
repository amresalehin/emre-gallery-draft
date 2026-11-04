package com.amresalehin.emregallery.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@Composable
fun MediaScraperDrawer(
    onClose: () -> Unit,
    onDownloadComplete: () -> Unit,
    onOpenBrowser: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val historyManager = remember { BrowserHistoryManager(context) }
    val recentSites by historyManager.allSites.collectAsState(initial = emptyList())

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

    // Dimension & Size filter states - initial default set to 0 (all dimensions shown), 1 KB minimum
    var showDimensionFilter by remember { mutableStateOf(false) }
    var minWidthFilter by remember { mutableFloatStateOf(0f) }
    var minHeightFilter by remember { mutableFloatStateOf(0f) }
    var showSvg by remember { mutableStateOf(false) } // By default keep SVG hidden
    var minKbInput by remember { mutableStateOf("1") } // By default hide media less than 1 KB

    var hasSubmittedQuery by remember { mutableStateOf(false) }
    var lastSubmittedUrl by remember { mutableStateOf("") }

    val allScrapedItems = remember(extractorState.items) {
        MediaClassifier.deduplicateScrapedItems(extractorState.items)
    }
    val svgCount = remember(allScrapedItems) {
        allScrapedItems.count { MediaClassifier.isSvg(it.url, it.mimeType, it.format) }
    }

    // Auto-select newly discovered items if user hasn't explicitly deselected all
    LaunchedEffect(allScrapedItems.size) {
        if (allScrapedItems.isNotEmpty() && selectedItemIds.isEmpty() && !extractorState.isRunning) {
            selectedItemIds = allScrapedItems.map { it.id }.toSet()
        }
    }

    val filterOptions = listOf("Images", "Videos", "Audio", "Streams")
    val dynamicDimensions = remember { mutableStateMapOf<String, Pair<Int, Int>>() }

    // Background probing of items with undetermined dimensions
    LaunchedEffect(allScrapedItems) {
        val itemsToProbe = allScrapedItems.filter { item ->
            item.category == MediaCategory.IMAGES &&
                    dynamicDimensions[item.id] == null &&
                    parseScrapedMediaDimensions(item).let { (w, h) -> w == null && h == null }
        }
        for (item in itemsToProbe.take(60)) {
            launch {
                val probed = ImageDimensionProber.probe(item.url, referer = urlInput)
                if (probed != null) {
                    dynamicDimensions[item.id] = probed
                }
            }
        }
    }

    val minKbVal = minKbInput.toFloatOrNull() ?: 0f
    val minBytesThreshold = (minKbVal * 1024L).toLong()

    // Auto-apply dimension, size (< 1KB), and SVG filters across all scraped items
    val dimensionFilteredAll = remember(
        allScrapedItems,
        minWidthFilter,
        minHeightFilter,
        showSvg,
        minBytesThreshold,
        dynamicDimensions.size
    ) {
        val targetMinWidth = minWidthFilter.toInt()
        val targetMinHeight = minHeightFilter.toInt()

        allScrapedItems.filter { item ->
            // 1. SVG Filter
            if (!showSvg && MediaClassifier.isSvg(item.url, item.mimeType, item.format)) {
                return@filter false
            }

            // 2. Hide media less than __ KB (by default 1 KB)
            if (minBytesThreshold > 0L) {
                val knownSize = item.sizeBytes
                    ?: if (item.url.startsWith("data:", ignoreCase = true)) {
                        MediaClassifier.estimateDataUriSizeBytes(item.url)
                    } else {
                        ImageDimensionProber.getCachedSize(item.url)
                    }
                if (knownSize != null && knownSize < minBytesThreshold) {
                    return@filter false
                }
            }

            // 3. Dimension Filter (Width & Height)
            if (targetMinWidth > 0 || targetMinHeight > 0) {
                if (!passesDimensionFilter(item, targetMinWidth, targetMinHeight, dynamicDimensions)) {
                    return@filter false
                }
            }

            true
        }
    }

    // Dynamic counts (reflecting active dimension and size filter)
    val imageCount = remember(dimensionFilteredAll) { dimensionFilteredAll.count { it.category == MediaCategory.IMAGES } }
    val videoCount = remember(dimensionFilteredAll) { dimensionFilteredAll.count { it.category == MediaCategory.VIDEOS } }
    val audioCount = remember(dimensionFilteredAll) { dimensionFilteredAll.count { it.category == MediaCategory.AUDIO } }
    val streamCount = remember(dimensionFilteredAll) { dimensionFilteredAll.count { it.category == MediaCategory.STREAMS } }

    // Filter by Category & Sort
    val filteredScrapedItems = remember(selectedFilter, dimensionFilteredAll, sortOption) {
        val categoryFiltered = when (selectedFilter) {
            "Images" -> dimensionFilteredAll.filter { it.category == MediaCategory.IMAGES }
            "Videos" -> dimensionFilteredAll.filter { it.category == MediaCategory.VIDEOS }
            "Audio" -> dimensionFilteredAll.filter { it.category == MediaCategory.AUDIO }
            "Streams" -> dimensionFilteredAll.filter { it.category == MediaCategory.STREAMS }
            else -> dimensionFilteredAll
        }

        when (sortOption) {
            "Title (A-Z)" -> categoryFiltered.sortedBy { it.title.lowercase() }
            "Streams First" -> categoryFiltered.sortedByDescending { it.isStream }
            "Videos First" -> categoryFiltered.sortedByDescending { it.isVideo && !it.isStream }
            "Images First" -> categoryFiltered.sortedBy { it.isVideo || it.isAudio || it.isStream }
            else -> categoryFiltered
        }
    }

    val clipboardManager = LocalClipboardManager.current
    val isValidUrl = remember(urlInput) { MediaClassifier.isValidWebUrl(urlInput) }
    val isAllSelected = filteredScrapedItems.isNotEmpty() &&
            selectedItemIds.containsAll(filteredScrapedItems.map { it.id })

    fun submitSearch(targetUrl: String) {
        val trimmed = targetUrl.trim()
        if (trimmed.isBlank()) return
        keyboardController?.hide()
        focusManager.clearFocus()
        val normalized = if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
            "https://$trimmed"
        } else {
            trimmed
        }
        if (MediaClassifier.isValidWebUrl(normalized)) {
            hasSubmittedQuery = true
            lastSubmittedUrl = normalized
            urlInput = normalized
            selectedItemIds = emptySet()
            dynamicDimensions.clear()
            scope.launch {
                historyManager.recordVisit(normalized)
            }
            dualExtractor.extract(normalized, scope)
        }
    }

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
                            // Reset Scraper Button (Clears fetched thumbnails, inputs, and filters)
                            IconButton(
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    urlInput = ""
                                    hasSubmittedQuery = false
                                    lastSubmittedUrl = ""
                                    selectedItemIds = emptySet()
                                    dynamicDimensions.clear()
                                    dualExtractor.reset()
                                    selectedFilter = null
                                    sortOption = "Default"
                                    minWidthFilter = 0f
                                    minHeightFilter = 0f
                                    showSvg = false
                                    minKbInput = "1"
                                    showDimensionFilter = false
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("scraper_reset_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Reset Scraper",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (urlInput.isEmpty()) {
                                    Text(
                                        text = "Search or enter URL",
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
                                            submitSearch(urlInput)
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

                            if (urlInput.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        urlInput = ""
                                        hasSubmittedQuery = false
                                        lastSubmittedUrl = ""
                                        selectedItemIds = emptySet()
                                        dynamicDimensions.clear()
                                        dualExtractor.reset()
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

                            // Fetch button pill - removed after submitting query until user edits or resets input
                            val showFetchButton = !hasSubmittedQuery || (urlInput.isNotBlank() && urlInput.trim() != lastSubmittedUrl)
                            if (showFetchButton) {
                                Surface(
                                    onClick = {
                                        submitSearch(urlInput)
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
                                Spacer(Modifier.width(6.dp))
                            }

                            // Browser redirect button beside Fetch button
                            IconButton(
                                onClick = {
                                    val targetUrl = when {
                                        isValidUrl -> urlInput.trim()
                                        urlInput.isNotBlank() && (urlInput.contains(".") || urlInput.startsWith("http")) -> {
                                            if (!urlInput.startsWith("http://") && !urlInput.startsWith("https://")) "https://${urlInput.trim()}" else urlInput.trim()
                                        }
                                        else -> "https://google.com"
                                    }
                                    if (onOpenBrowser != null) {
                                        onOpenBrowser(targetUrl)
                                    } else {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        runCatching { context.startActivity(intent) }
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("scraper_browser_redirect_button")
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (urlInput.isNotBlank()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isValidUrl) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInNew,
                                            contentDescription = "Open in browser",
                                            tint = if (urlInput.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(18.dp)
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

                    Spacer(Modifier.width(6.dp))

                    // Pinned Sort Button (Icon only to save space)
                    Box {
                        Surface(
                            onClick = { sortDropdownExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            color = if (sortOption != "Default") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                1.dp,
                                if (sortOption != "Default") MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("scraper_sort_button")
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = "Sort ($sortOption)",
                                    tint = if (sortOption != "Default") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = sortDropdownExpanded,
                            onDismissRequest = { sortDropdownExpanded = false }
                        ) {
                            listOf("Default", "Title (A-Z)", "Streams First", "Videos First", "Images First").forEach { opt ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (sortOption == opt) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.width(8.dp))
                                            }
                                            Text(
                                                text = opt,
                                                fontWeight = if (sortOption == opt) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    },
                                    onClick = {
                                        sortOption = opt
                                        sortDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    // Pinned Dimension & SVG Filter Button
                    val isDimensionFilterActive = minWidthFilter > 0f || minHeightFilter > 0f || showSvg || (minKbInput.isNotBlank() && minKbInput != "1")
                    Surface(
                        onClick = { showDimensionFilter = !showDimensionFilter },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDimensionFilterActive || showDimensionFilter) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            if (isDimensionFilterActive || showDimensionFilter) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("scraper_dimension_filter_button")
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Filter media by dimensions and SVG",
                                tint = if (isDimensionFilterActive || showDimensionFilter) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    // Pinned Select All Checkbox (Right-most position, no text, checkbox only)
                    Surface(
                        onClick = {
                            val currentFilteredIds = filteredScrapedItems.map { it.id }.toSet()
                            selectedItemIds = if (isAllSelected) {
                                selectedItemIds - currentFilteredIds
                            } else {
                                selectedItemIds + currentFilteredIds
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isAllSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            if (isAllSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("scraper_select_all_checkbox")
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = if (isAllSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                border = BorderStroke(
                                    1.4.dp,
                                    if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.size(16.dp)
                            ) {
                                if (isAllSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Select All",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Expandable Dimension & Size Filter Section matching clean compact mockup
                AnimatedVisibility(
                    visible = showDimensionFilter,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("dimension_filter_panel")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Top action buttons: Clear and Close
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = {
                                        minWidthFilter = 0f
                                        minHeightFilter = 0f
                                        showSvg = false
                                        minKbInput = "1"
                                    },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .height(32.dp)
                                        .testTag("dimension_filter_reset_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Clear",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Clear",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Spacer(Modifier.width(8.dp))

                                Surface(
                                    onClick = { showDimensionFilter = false },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("dimension_filter_close_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close filter panel",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            // Slider 1: Width
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Width",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (minWidthFilter <= 0f) "∞" else "${minWidthFilter.toInt()}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Slider(
                                    value = minWidthFilter,
                                    onValueChange = { minWidthFilter = it },
                                    valueRange = 0f..3840f,
                                    steps = 3,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.surface,
                                        activeTrackColor = MaterialTheme.colorScheme.outlineVariant,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("dimension_filter_width_slider")
                                )
                            }

                            // Slider 2: Height
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Height",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (minHeightFilter <= 0f) "∞" else "${minHeightFilter.toInt()}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Slider(
                                    value = minHeightFilter,
                                    onValueChange = { minHeightFilter = it },
                                    valueRange = 0f..2160f,
                                    steps = 3,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.surface,
                                        activeTrackColor = MaterialTheme.colorScheme.outlineVariant,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("dimension_filter_height_slider")
                                )
                            }

                            // Presets Row: 360p, 480p, 720p, 1080p, 4K
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    "360p" to Pair(360f, 360f),
                                    "480p" to Pair(640f, 480f),
                                    "720p" to Pair(1280f, 720f),
                                    "1080p" to Pair(1920f, 1080f),
                                    "4K" to Pair(3840f, 2160f)
                                ).forEach { (label, dims) ->
                                    val isCurrentPreset = minWidthFilter == dims.first && minHeightFilter == dims.second
                                    Surface(
                                        onClick = {
                                            if (isCurrentPreset) {
                                                minWidthFilter = 0f
                                                minHeightFilter = 0f
                                            } else {
                                                minWidthFilter = dims.first
                                                minHeightFilter = dims.second
                                            }
                                        },
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (isCurrentPreset) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isCurrentPreset) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .testTag("scraper_preset_$label")
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (isCurrentPreset) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isCurrentPreset) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            // Bottom Capsule: SVG [Switch] | Hide < [ 1 ] KB
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("scraper_bottom_capsule")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left side: SVG and Switch
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "SVG",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Switch(
                                            checked = showSvg,
                                            onCheckedChange = { showSvg = it },
                                            modifier = Modifier.testTag("scraper_filter_svg_switch")
                                        )
                                    }

                                    Spacer(Modifier.width(14.dp))

                                    // Vertical divider
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(26.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                    )

                                    Spacer(Modifier.width(14.dp))

                                    // Right side: Hide < [ 1 ] KB
                                    Row(
                                        modifier = Modifier.weight(1.3f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "Hide <",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier
                                                .width(68.dp)
                                                .height(34.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (minKbInput.isEmpty()) {
                                                    Text(
                                                        text = "1",
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            textAlign = TextAlign.Center,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                        )
                                                    )
                                                }
                                                BasicTextField(
                                                    value = minKbInput,
                                                    onValueChange = { input ->
                                                        if (input.all { it.isDigit() } && input.length <= 6) {
                                                            minKbInput = input
                                                        }
                                                    },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                        textAlign = TextAlign.Center,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    ),
                                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("scraper_filter_min_kb_input")
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "KB",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
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
                    if (filteredScrapedItems.isNotEmpty()) {
                        val columnCount = when {
                            filteredScrapedItems.size == 1 -> 1
                            filteredScrapedItems.size == 2 -> 2
                            else -> 3
                        }
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(columnCount),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalItemSpacing = 8.dp
                        ) {
                            items(filteredScrapedItems, key = { it.id }) { itm ->
                                RealScrapedCard(
                                    item = itm,
                                    isSelected = itm.id in selectedItemIds,
                                    dynamicDimensions = dynamicDimensions,
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
                    } else if (allScrapedItems.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "No media matches the filters",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Try reducing min width/height or clearing category filter",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        minWidthFilter = 0f
                                        minHeightFilter = 0f
                                        showSvg = false
                                        selectedFilter = null
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Reset Filters")
                                }
                            }
                        }
                    } else if (extractorState.isRunning) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                ThreeColumnSkeletonGrid()
                            }
                        }
                    } else {
                        // Empty State: Show Previous 5 Searches
                        val displaySites = if (recentSites.isNotEmpty()) {
                            recentSites.take(5)
                        } else {
                            historyManager.defaultTopSites.take(5)
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .testTag("scraper_empty_state_recent_searches"),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = if (recentSites.isNotEmpty()) "Previous Searches" else "Suggested Searches",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            items(displaySites, key = { it.url }) { site ->
                                Surface(
                                    onClick = { submitSearch(site.url) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("recent_search_item_${site.domain}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.History,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = site.title.ifBlank { site.domain },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = site.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(Modifier.width(8.dp))

                                        Icon(
                                            imageVector = Icons.Default.NorthWest,
                                            contentDescription = "Search this URL",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
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
                                downloadProgress = 0.2f
                                downloadStatusText = "Queueing persistent download..."
                                scope.launch {
                                    val selectedRawItems = filteredScrapedItems.filter { it.id in selectedItemIds }
                                    // Automatic cross-scraper deduplication before downloading
                                    val itemsToDownload = MediaClassifier.deduplicateScrapedItems(selectedRawItems)
                                    val duplicateCount = selectedRawItems.size - itemsToDownload.size
                                    if (duplicateCount > 0) {
                                        downloadStatusText = "Deduplicated $duplicateCount duplicate(s) across scrapers..."
                                    }
                                    val stackId = "stack_${System.currentTimeMillis()}"
                                    val stackTitle = extractorState.pageTitle.ifBlank { extractorState.domain.ifBlank { "Discovered Media" } }

                                    // Persistently download using WorkManager
                                    DownloadQueueManager.enqueueBatch(
                                        context = context,
                                        items = itemsToDownload,
                                        referer = urlInput,
                                        stackId = stackId,
                                        stackTitle = stackTitle,
                                        sourceDomain = extractorState.domain,
                                        sourceUrl = urlInput
                                    )

                                    downloadProgress = 1.0f
                                    val notice = if (duplicateCount > 0) {
                                        "Queued ${itemsToDownload.size} items ($duplicateCount duplicate(s) merged)"
                                    } else {
                                        "Queued ${itemsToDownload.size} items for background download"
                                    }
                                    downloadStatusText = notice
                                    kotlinx.coroutines.delay(400)
                                    isDownloading = false
                                    onDownloadComplete()
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
    modifier: Modifier = Modifier,
    dynamicDimensions: Map<String, Pair<Int, Int>> = emptyMap()
) {
    val mosaicHeight = remember(item, dynamicDimensions[item.id]) {
        val (width, height) = dynamicDimensions[item.id] ?: parseScrapedMediaDimensions(item)
        if (width != null && height != null && width > 0) {
            val ratio = height.toFloat() / width.toFloat()
            when {
                ratio > 1.25f -> 160.dp // portrait
                ratio < 0.75f -> 100.dp // landscape
                else -> 125.dp
            }
        } else if (item.isStream || item.isVideo) {
            130.dp
        } else if (item.isAudio) {
            95.dp
        } else {
            // True staggered mosaic variation
            val mod = Math.abs(item.url.hashCode()) % 4
            when (mod) {
                0 -> 155.dp
                1 -> 115.dp
                2 -> 140.dp
                else -> 120.dp
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToggle() }
            .testTag("scraped_media_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mosaicHeight)
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
                    val resolvedUrl = remember(previewUrl) {
                        if (previewUrl.startsWith("//")) "https:$previewUrl" else previewUrl
                    }
                    val context = LocalContext.current
                    val imageRequest = remember(resolvedUrl) {
                        ImageRequest.Builder(context)
                            .data(resolvedUrl)
                            .crossfade(true)
                            .setHeader("User-Agent", OkHttpDownloader.DEFAULT_USER_AGENT)
                            .setHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                            .apply {
                                if (resolvedUrl.contains("wikimedia.org") || resolvedUrl.contains("wikipedia.org")) {
                                    setHeader("Referer", "https://en.wikipedia.org/")
                                }
                            }
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
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
                    val (w, h) = dynamicDimensions[item.id] ?: parseScrapedMediaDimensions(item)
                    val dimensionDisplay = if (w != null && h != null) {
                        "${w}x${h}"
                    } else if (w != null) {
                        "${w}px"
                    } else {
                        item.resolutionHint ?: item.source
                    }
                    Text(
                        text = dimensionDisplay,
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

/**
 * Comprehensive parser for media dimensions from resolution hints, URL patterns, and titles.
 */
fun parseScrapedMediaDimensions(item: ScrapedMediaItem): Pair<Int?, Int?> {
    val hint = item.resolutionHint?.trim()
    if (!hint.isNullOrBlank()) {
        val match = Regex("""(\d{2,5})\s*[xX×]\s*(\d{2,5})""").find(hint)
        if (match != null) {
            val w = match.groupValues[1].toIntOrNull()
            val h = match.groupValues[2].toIntOrNull()
            if (w != null && h != null && w > 0 && h > 0) return Pair(w, h)
        }
        val pMatch = Regex("""(\d{3,4})p""", RegexOption.IGNORE_CASE).find(hint)
        if (pMatch != null) {
            val h = pMatch.groupValues[1].toIntOrNull()
            if (h != null && h > 0) {
                return Pair((h * 16) / 9, h)
            }
        }
        val wMatch = Regex("""(\d{2,5})w""", RegexOption.IGNORE_CASE).find(hint)
        if (wMatch != null) {
            val w = wMatch.groupValues[1].toIntOrNull()
            if (w != null && w > 0) return Pair(w, null)
        }
        val pxMatch = Regex("""(\d{2,5})px""", RegexOption.IGNORE_CASE).find(hint)
        if (pxMatch != null) {
            val w = pxMatch.groupValues[1].toIntOrNull()
            if (w != null && w > 0) return Pair(w, null)
        }
    }

    // 1. WxH in URL or Title (e.g., 800x600, 1920x1080)
    val urlMatch = Regex("""(?:^|[/_.-])(\d{2,5})\s*[xX×]\s*(\d{2,5})(?:[/_.-]|$)""").find(item.url)
        ?: Regex("""(?:^|[/_.-])(\d{2,5})\s*[xX×]\s*(\d{2,5})(?:[/_.-]|$)""").find(item.title)
    if (urlMatch != null) {
        val w = urlMatch.groupValues[1].toIntOrNull()
        val h = urlMatch.groupValues[2].toIntOrNull()
        if (w != null && h != null && w >= 10 && h >= 10) {
            return Pair(w, h)
        }
    }

    // 2. Wikimedia / standard thumbnail pattern: /(\d+)px- or title starting with (\d+)px
    val pxMatch = Regex("""(?:^|[/_-])(\d{2,5})px(?:[-_./]|$)""", RegexOption.IGNORE_CASE).find(item.url)
        ?: Regex("""(?:^|[/_-])(\d{2,5})px(?:[-_./]|$)""", RegexOption.IGNORE_CASE).find(item.title)
    if (pxMatch != null) {
        val w = pxMatch.groupValues[1].toIntOrNull()
        if (w != null && w >= 10) {
            return Pair(w, null)
        }
    }

    // 3. Query params: ?width=800&height=600 or ?w=800&h=600
    val wQuery = Regex("""[?&](?:width|w)=(\d{2,5})""", RegexOption.IGNORE_CASE).find(item.url)?.groupValues?.get(1)?.toIntOrNull()
    val hQuery = Regex("""[?&](?:height|h)=(\d{2,5})""", RegexOption.IGNORE_CASE).find(item.url)?.groupValues?.get(1)?.toIntOrNull()
    if (wQuery != null || hQuery != null) {
        return Pair(wQuery, hQuery)
    }

    // 4. Folder dimension descriptor e.g. /736x/, /474x/, /236x/
    val folderDim = Regex("""/(\d{2,5})x/""").find(item.url)?.groupValues?.get(1)?.toIntOrNull()
    if (folderDim != null && folderDim >= 10) {
        return Pair(folderDim, null)
    }

    return Pair(null, null)
}

/**
 * Evaluates whether a media item satisfies the active dimension filter.
 */
fun passesDimensionFilter(
    item: ScrapedMediaItem,
    targetMinWidth: Int,
    targetMinHeight: Int,
    dynamicDimensions: Map<String, Pair<Int, Int>>
): Boolean {
    // 1. Any Dimensions setting (default 0, 0) matches everything
    if (targetMinWidth <= 0 && targetMinHeight <= 0) return true

    // 2. Audio, Video, and Stream files have no image-based spatial filtering requirements; exclude them from dimension filtering
    if (item.category == MediaCategory.AUDIO || item.category == MediaCategory.VIDEOS ||
        item.category == MediaCategory.STREAMS || item.isVideo || item.isAudio || item.isStream
    ) {
        return true
    }

    // 3. Reject decorative small UI elements, buttons, icons, or donate badges
    val lowerUrl = item.url.lowercase(Locale.ROOT)
    val lowerTitle = item.title.lowercase(Locale.ROOT)
    val isTinyUiMedia = lowerUrl.contains("donate") || lowerUrl.contains("/icon") ||
            lowerUrl.contains("-icon.") || lowerUrl.contains("_icon.") ||
            lowerUrl.contains("favicon") || lowerUrl.contains("badge") ||
            lowerTitle.contains("donate") || lowerTitle.contains("favicon") ||
            lowerTitle.contains("icon")
    if (isTinyUiMedia) {
        return false
    }

    val (w, h) = dynamicDimensions[item.id] ?: parseScrapedMediaDimensions(item)

    // Treat unknown dimensions as pending or excluded in strict mode
    if (targetMinWidth > 0 && w == null) return false
    if (targetMinHeight > 0 && h == null) return false

    // When strict filtering is active, require every enabled dimension constraint to be satisfied
    if (targetMinWidth > 0 && (w == null || w < targetMinWidth)) return false
    if (targetMinHeight > 0 && (h == null || h < targetMinHeight)) return false

    return true
}

/**
 * Lightweight background prober for media items with undetermined dimensions.
 * Uses OkHttp with HTTP Range 0-8191 and enforces a hard limit of 8192 bytes read from stream.
 */
object ImageDimensionProber {
    private val dimCache = ConcurrentHashMap<String, Pair<Int, Int>>()
    private val sizeCache = ConcurrentHashMap<String, Long>()

    suspend fun probe(url: String, referer: String? = null): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val cleanUrl = if (url.startsWith("//")) "https:$url" else url
        dimCache[cleanUrl]?.let { return@withContext it }

        try {
            val reqBuilder = okhttp3.Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", OkHttpDownloader.DEFAULT_USER_AGENT)
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .header("Range", "bytes=0-8191")

            if (!referer.isNullOrBlank()) {
                reqBuilder.header("Referer", referer)
            } else if (cleanUrl.contains("wikimedia.org") || cleanUrl.contains("wikipedia.org")) {
                reqBuilder.header("Referer", "https://en.wikipedia.org/")
            }

            OkHttpDownloader.client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val contentRange = resp.header("Content-Range")
                    val totalSize = if (!contentRange.isNullOrBlank() && contentRange.contains("/")) {
                        contentRange.substringAfterLast("/").trim().toLongOrNull()
                    } else if (resp.code == 200) {
                        resp.header("Content-Length")?.toLongOrNull()
                    } else null

                    if (totalSize != null && totalSize > 0L) {
                        sizeCache[cleanUrl] = totalSize
                    }

                    val stream = resp.body?.byteStream() ?: return@withContext null
                    val maxProbeBytes = 8192
                    val buffer = ByteArray(maxProbeBytes)
                    var totalRead = 0
                    while (totalRead < maxProbeBytes) {
                        val read = stream.read(buffer, totalRead, maxProbeBytes - totalRead)
                        if (read == -1) break
                        totalRead += read
                    }
                    if (totalRead > 0) {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(buffer, 0, totalRead, opts)
                        if (opts.outWidth > 0 && opts.outHeight > 0) {
                            val dims = Pair(opts.outWidth, opts.outHeight)
                            dimCache[cleanUrl] = dims
                            return@withContext dims
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        null
    }

    fun getCached(url: String): Pair<Int, Int>? {
        val cleanUrl = if (url.startsWith("//")) "https:$url" else url
        return dimCache[cleanUrl]
    }

    fun getCachedSize(url: String): Long? {
        val cleanUrl = if (url.startsWith("//")) "https:$url" else url
        return sizeCache[cleanUrl]
    }
}
