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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val scope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf("") }
    var isScraping by remember { mutableStateOf(false) }
    var scrapeError by remember { mutableStateOf<String?>(null) }
    var scrapedResult by remember { mutableStateOf<ScrapeResult?>(null) }

    var selectedFilter by remember { mutableStateOf("All") }
    var selectedItemIds by remember { mutableStateOf(setOf<String>()) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    var sortDropdownExpanded by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf("Default") }

    val allScrapedItems = scrapedResult?.items ?: emptyList()
    val filterOptions = listOf("All", "Images", "Videos", "Audio")

    // Filter & Sort
    val filteredScrapedItems = remember(selectedFilter, allScrapedItems, sortOption) {
        val filtered = when (selectedFilter) {
            "Images" -> allScrapedItems.filter { !it.isVideo && !it.isAudio }
            "Videos" -> allScrapedItems.filter { it.isVideo }
            "Audio" -> allScrapedItems.filter { it.isAudio }
            else -> allScrapedItems
        }
        when (sortOption) {
            "Title" -> filtered.sortedBy { it.title.lowercase() }
            "Images First" -> filtered.sortedBy { it.isVideo || it.isAudio }
            "Videos First" -> filtered.sortedByDescending { it.isVideo }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Drag handle pill matching Material 3 bottom sheet styling
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp),
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
            }

            // 1. URL Input Bar matching screenshot: [ 🔗 | Paste a link (URL) | Fetch ]
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
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
                                onValueChange = {
                                    urlInput = it
                                    scrapeError = null
                                },
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
                                    scrapedResult = null
                                    selectedItemIds = emptySet()
                                    scrapeError = null
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
                                if (urlInput.isBlank()) {
                                    scrapeError = "Please enter a URL to fetch"
                                    return@Surface
                                }
                                isScraping = true
                                scrapeError = null
                                scrapedResult = null
                                selectedItemIds = emptySet()
                                scope.launch {
                                    val res = scraper.scrape(urlInput)
                                    isScraping = false
                                    if (res.errorMessage != null) {
                                        scrapeError = res.errorMessage
                                    } else {
                                        scrapedResult = res
                                        // Select all by default
                                        selectedItemIds = res.items.map { it.id }.toSet()
                                    }
                                }
                            },
                            enabled = !isScraping,
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF535868),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("scraper_fetch_button")
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isScraping) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
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

            // Error Notice Banner if any
            scrapeError?.let { err ->
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 2. Controls Row: [Select All] & [Sort ▾] matching screenshot
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Select All Pill Button with Checkbox
                    Surface(
                        onClick = {
                            if (filteredScrapedItems.isNotEmpty()) {
                                val currentIds = filteredScrapedItems.map { it.id }.toSet()
                                selectedItemIds = if (isAllSelected) {
                                    selectedItemIds - currentIds
                                } else {
                                    selectedItemIds + currentIds
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("scraper_select_all_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Rounded Checkbox Square Icon
                            Icon(
                                imageVector = if (isAllSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Select All",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Sort Pill Button with dropdown
                    Box {
                        Surface(
                            onClick = { sortDropdownExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("scraper_sort_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = "Sort",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (sortOption == "Default") "Sort" else sortOption,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = sortDropdownExpanded,
                            onDismissRequest = { sortDropdownExpanded = false }
                        ) {
                            listOf("Default", "Title", "Images First", "Videos First").forEach { opt ->
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

            // 3. Subtitle Bar Skeleton & 4 Filter Chips matching screenshot
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Rounded horizontal skeleton bar matching screenshot
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    )

                    // 4 Horizontal Pill Chips: All, Images, Videos, Audio
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filterOptions) { filter ->
                            val isSelected = selectedFilter == filter
                            Surface(
                                onClick = { selectedFilter = filter },
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = filter,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. 3-Column Grid matching screenshot:
            // If real media items exist, render 3 columns of real media cards.
            // If empty (no media scraped yet), render the 3-column skeleton grid!
            item {
                if (filteredScrapedItems.isNotEmpty()) {
                    // 3 columns of real media
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
                    // Wireframe 3-Column Skeleton Grid matching Screenshot 3 perfectly!
                    ThreeColumnSkeletonGrid()
                }
            }
        }

        // 5. Bottom Download Pill Button matching screenshot: [ 📥 Download (N) ]
        // Notice: "Remove the plus button from bellow, rather keep a download buttom."
        // We render a wide rounded download button docked at the bottom with a gradient fade backdrop!
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Surface(
                onClick = {
                    if (selectedItemIds.isEmpty() || isDownloading) return@Surface
                    isDownloading = true
                    downloadProgress = 0f
                    scope.launch {
                        val itemsToDownload = filteredScrapedItems.filter { it.id in selectedItemIds }
                        val downloadedInboxItems = mutableListOf<MediaInboxItem>()

                        itemsToDownload.forEachIndexed { index, scrapedItem ->
                            downloadProgress = (index + 0.5f) / itemsToDownload.size.toFloat()
                            val file = scraper.downloadMediaFile(context, scrapedItem.url, scrapedItem.title)
                            if (file != null) {
                                downloadedInboxItems.add(
                                    MediaInboxItem(
                                        id = "item_${System.currentTimeMillis()}_$index",
                                        title = scrapedItem.title,
                                        localUri = file.absolutePath,
                                        sourceUrl = scrapedItem.url,
                                        isVideo = scrapedItem.isVideo,
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
                                title = scrapedResult?.pageTitle?.ifBlank { scrapedResult?.domain } ?: "Web Media",
                                sourceDomain = scrapedResult?.domain ?: "",
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
                            strokeWidth = 2.5.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Downloading ${(downloadProgress * 100).toInt()}%...",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
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
                AsyncImage(
                    model = item.url,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (item.isVideo) {
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
                } else if (item.isAudio) {
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
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp
                )
                Text(
                    text = when {
                        item.isVideo -> "Video"
                        item.isAudio -> "Audio"
                        else -> "Image"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
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
        // Column 1
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThreeColumnSkeletonCard(heightDp = 125)
            ThreeColumnSkeletonCard(heightDp = 100)
            ThreeColumnSkeletonCard(heightDp = 115)
        }

        // Column 2
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThreeColumnSkeletonCard(heightDp = 95)
            ThreeColumnSkeletonCard(heightDp = 135)
            ThreeColumnSkeletonCard(heightDp = 100)
        }

        // Column 3
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
                // Centered image placeholder icon
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = Color(0xFFA5ABC0),
                    modifier = Modifier.size(28.dp)
                )

                // Checkbox outline in top-right corner matching screenshot
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

            // 2 skeleton text lines below thumbnail
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
