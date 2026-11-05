package com.amresalehin.emregallery.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URL
import java.util.Locale

/**
 * Recognized media platforms with custom brand color and iconography.
 */
enum class DetectedPlatform(
    val title: String,
    val icon: ImageVector,
    val badgeColor: Color,
    val onBadgeColor: Color,
    val description: String,
    val defaultUrl: String
) {
    YOUTUBE("YouTube", Icons.Default.PlayCircle, Color(0xFFE53935), Color.White, "Videos, Shorts & Audio", "https://youtube.com"),
    INSTAGRAM("Instagram", Icons.Default.CameraAlt, Color(0xFFE1306C), Color.White, "Reels, Posts & Stories", "https://instagram.com"),
    TIKTOK("TikTok", Icons.Default.Audiotrack, Color(0xFF010101), Color.White, "No-watermark Clips", "https://tiktok.com"),
    TWITTER("X / Twitter", Icons.Default.AlternateEmail, Color(0xFF1DA1F2), Color.White, "Videos, GIFs & Media", "https://x.com"),
    REDDIT("Reddit", Icons.Default.Forum, Color(0xFFFF4500), Color.White, "Posts & Video Clips", "https://reddit.com"),
    PINTEREST("Pinterest", Icons.Default.PushPin, Color(0xFFBD081C), Color.White, "Full-res Pins & Boards", "https://pinterest.com"),
    FACEBOOK("Facebook", Icons.Default.VideoLibrary, Color(0xFF1877F2), Color.White, "Public Reels & Videos", "https://facebook.com"),
    DIRECT_MEDIA("Direct Media", Icons.Default.Download, Color(0xFF2E7D32), Color.White, "Raw MP4, WEBM or JPG", ""),
    GENERIC_WEB("Any Website", Icons.Default.Language, Color(0xFF455A64), Color.White, "Deep Scrape All Media", "")
}

fun detectPlatform(url: String): DetectedPlatform {
    val lower = url.trim().lowercase(Locale.ROOT)
    return when {
        lower.contains("youtube.com") || lower.contains("youtu.be") -> DetectedPlatform.YOUTUBE
        lower.contains("instagram.com") -> DetectedPlatform.INSTAGRAM
        lower.contains("tiktok.com") -> DetectedPlatform.TIKTOK
        lower.contains("twitter.com") || lower.contains("x.com") -> DetectedPlatform.TWITTER
        lower.contains("reddit.com") || lower.contains("redd.it") -> DetectedPlatform.REDDIT
        lower.contains("pinterest.com") || lower.contains("pin.it") -> DetectedPlatform.PINTEREST
        lower.contains("facebook.com") || lower.contains("fb.watch") -> DetectedPlatform.FACEBOOK
        lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m3u8") ||
                lower.endsWith(".mpd") || lower.endsWith(".jpg") || lower.endsWith(".png") ||
                lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".mp3") -> DetectedPlatform.DIRECT_MEDIA
        else -> DetectedPlatform.GENERIC_WEB
    }
}

/**
 * Minimal Emre-style Search & URL Fetch Page for the Media Downloader.
 * Clean monochromatic layout with dynamic capsule search input and compact
 * site favicon pills based on user history.
 */
data class DownloaderPill(
    val domain: String,
    val name: String,
    val url: String,
    val explicitFaviconUrl: String? = null
) {
    val faviconUrl: String
        get() = explicitFaviconUrl ?: "https://www.google.com/s2/favicons?domain=${domain.removePrefix("www.")}&sz=64"

    val initial: String
        get() = name.firstOrNull()?.uppercaseChar()?.toString() ?: "W"
}

@Composable
fun DownloaderSearchMainPage(
    onFetchUrl: (String) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onViewSavedLibrary: () -> Unit,
    activeDownloadsCount: Int = 0,
    savedStacksCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val historyManager = remember { BrowserHistoryManager(context) }
    val allHistorySites by historyManager.allSites.collectAsState(initial = emptyList())

    var urlInput by remember { mutableStateOf("") }

    fun submitSearch(target: String) {
        val trimmed = target.trim()
        if (trimmed.isBlank()) return
        keyboardController?.hide()
        focusManager.clearFocus()
        val normalized = if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
            "https://$trimmed"
        } else {
            trimmed
        }
        scope.launch {
            historyManager.recordVisit(normalized)
        }
        onFetchUrl(normalized)
    }

    fun openInBrowser(queryOrUrl: String) {
        val trimmed = queryOrUrl.trim()
        if (trimmed.isBlank()) return
        keyboardController?.hide()
        focusManager.clearFocus()
        val destinationUrl = if (trimmed.contains(".") && !trimmed.contains(" ") &&
            (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) ||
                    trimmed.matches(Regex("^[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}(/.*)?$")))) {
            if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) trimmed else "https://$trimmed"
        } else {
            "https://www.google.com/search?q=" + java.net.URLEncoder.encode(trimmed, "UTF-8")
        }
        scope.launch {
            historyManager.recordVisit(destinationUrl)
        }
        onOpenBrowser(destinationUrl)
    }

    // Dynamic pills derived solely from user visit/saved history - no hardcoded sites
    val pills = remember(allHistorySites) {
        val seenDomains = mutableSetOf<String>()
        val result = mutableListOf<DownloaderPill>()
        for (site in allHistorySites) {
            val domain = site.domain.removePrefix("www.").lowercase().trim()
            if (domain.isNotBlank() && seenDomains.add(domain)) {
                val displayName = site.title.takeIf { it.isNotBlank() && it != "about:blank" && !it.startsWith("http") }
                    ?: domain.substringBefore('.').replaceFirstChar { it.uppercase() }
                result.add(
                    DownloaderPill(
                        domain = domain,
                        name = displayName,
                        url = site.url
                    )
                )
            }
        }
        result.take(12)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("downloader_search_main_page"),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Dynamic URL search & action field
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("downloader_main_search_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = "Link",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(Modifier.width(10.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (urlInput.isEmpty()) {
                        Text(
                            text = "Paste link or enter URL...",
                            style = MaterialTheme.typography.bodyMedium,
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
                                if (urlInput.isNotBlank()) {
                                    submitSearch(urlInput)
                                }
                            }
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        ),
                        cursorBrush = SolidColor(Color(0xFF111827)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("downloader_main_url_input")
                    )
                }

                Spacer(Modifier.width(6.dp))

                // Dynamic Action Controls: Paste icon when empty; Download + Browse + Clear when query present
                AnimatedContent(
                    targetState = urlInput.isNotBlank(),
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label = "search_bar_actions"
                ) { hasQuery ->
                    if (hasQuery) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Clear input button
                            IconButton(
                                onClick = { urlInput = "" },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("downloader_clear_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF6B7280),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Browse button (opens in-app browser with query / url)
                            IconButton(
                                onClick = { openInBrowser(urlInput) },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("downloader_action_browse_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TravelExplore,
                                    contentDescription = "Browse web with search query",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Download button (opens downloader page) - clean and transparent
                            IconButton(
                                onClick = { submitSearch(urlInput) },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("downloader_action_download_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Download media from URL",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        // Paste button when empty
                        IconButton(
                            onClick = {
                                val clipText = clipboardManager.getText()?.text?.trim().orEmpty()
                                if (clipText.isNotBlank()) {
                                    urlInput = clipText
                                    submitSearch(clipText)
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("downloader_paste_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste from clipboard and fetch",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Recent links horizontal pill row with site favicons
        if (pills.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("downloader_recent_pills_row"),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(pills, key = { it.domain + it.url }) { pill ->
                    Surface(
                        onClick = {
                            urlInput = pill.url
                            submitSearch(pill.url)
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("recent_pill_${pill.name}")
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Circular site favicon avatar
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    var iconFailed by remember(pill.faviconUrl) { mutableStateOf(false) }
                                    if (!iconFailed) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(pill.faviconUrl)
                                                .crossfade(true)
                                                .listener(
                                                    onError = { _, _ -> iconFailed = true }
                                                )
                                                .build(),
                                            contentDescription = pill.name,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }

                            // Website name
                            Text(
                                text = pill.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Downloader Page that opens when the user enters or pastes a URL and chooses "Fetch".
 * Executes deep multi-provider extraction, displays real-time progress, filter chips,
 * staggered result cards, and sticky bottom download control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderResultsPage(
    targetUrl: String,
    onBack: () -> Unit,
    onOpenBrowser: ((String) -> Unit)? = null,
    onDownloadComplete: (Int) -> Unit,
    onViewSavedLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dualExtractor = remember { DualMediaExtractor(context) }

    DisposableEffect(Unit) {
        onDispose {
            dualExtractor.cleanup()
        }
    }

    val extractorState by dualExtractor.state.collectAsState()

    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var selectedItemIds by remember { mutableStateOf(setOf<String>()) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadStatusText by remember { mutableStateOf("") }
    var sortDropdownExpanded by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf("Default") }

    // Dimension & SVG Filters
    var showDimensionFilter by remember { mutableStateOf(false) }
    var minWidthFilter by remember { mutableFloatStateOf(0f) }
    var minHeightFilter by remember { mutableFloatStateOf(0f) }
    var showSvg by remember { mutableStateOf(false) }
    var minKbInput by remember { mutableStateOf("1") }

    val dynamicDimensions = remember { mutableStateMapOf<String, Pair<Int, Int>>() }

    // Trigger extraction on mount or URL change
    LaunchedEffect(targetUrl) {
        if (targetUrl.isNotBlank()) {
            selectedItemIds = emptySet()
            dynamicDimensions.clear()
            dualExtractor.extract(targetUrl, scope)
        }
    }

    val allScrapedItems = remember(extractorState.items) {
        MediaClassifier.deduplicateScrapedItems(extractorState.items)
    }

    // Auto-select newly discovered items
    LaunchedEffect(allScrapedItems.size) {
        if (allScrapedItems.isNotEmpty() && selectedItemIds.isEmpty() && !extractorState.isRunning) {
            selectedItemIds = allScrapedItems.map { it.id }.toSet()
        }
    }

    // Dimension prober in background
    LaunchedEffect(allScrapedItems) {
        val itemsToProbe = allScrapedItems.filter { item ->
            item.category == MediaCategory.IMAGES &&
                    dynamicDimensions[item.id] == null &&
                    parseScrapedMediaDimensions(item).let { (w, h) -> w == null && h == null }
        }
        for (item in itemsToProbe.take(50)) {
            launch {
                val probed = ImageDimensionProber.probe(item.url, referer = targetUrl)
                if (probed != null) {
                    dynamicDimensions[item.id] = probed
                }
            }
        }
    }

    val minKbVal = minKbInput.toFloatOrNull() ?: 0f
    val minBytesThreshold = (minKbVal * 1024L).toLong()

    val dimensionFiltered = remember(
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
            if (!showSvg && MediaClassifier.isSvg(item.url, item.mimeType, item.format)) {
                return@filter false
            }
            if (minBytesThreshold > 0L) {
                val knownSize = item.sizeBytes ?: ImageDimensionProber.getCachedSize(item.url)
                if (knownSize != null && knownSize < minBytesThreshold) {
                    return@filter false
                }
            }
            if (targetMinWidth > 0 || targetMinHeight > 0) {
                if (!passesDimensionFilter(item, targetMinWidth, targetMinHeight, dynamicDimensions)) {
                    return@filter false
                }
            }
            true
        }
    }

    val imageCount = remember(dimensionFiltered) { dimensionFiltered.count { it.category == MediaCategory.IMAGES } }
    val videoCount = remember(dimensionFiltered) { dimensionFiltered.count { it.category == MediaCategory.VIDEOS } }
    val audioCount = remember(dimensionFiltered) { dimensionFiltered.count { it.category == MediaCategory.AUDIO } }
    val streamCount = remember(dimensionFiltered) { dimensionFiltered.count { it.category == MediaCategory.STREAMS } }

    val filteredItems = remember(selectedFilter, dimensionFiltered, sortOption) {
        val categoryFiltered = when (selectedFilter) {
            "Images" -> dimensionFiltered.filter { it.category == MediaCategory.IMAGES }
            "Videos" -> dimensionFiltered.filter { it.category == MediaCategory.VIDEOS }
            "Audio" -> dimensionFiltered.filter { it.category == MediaCategory.AUDIO }
            "Streams" -> dimensionFiltered.filter { it.category == MediaCategory.STREAMS }
            else -> dimensionFiltered
        }
        when (sortOption) {
            "Title (A-Z)" -> categoryFiltered.sortedBy { it.title.lowercase() }
            "Videos First" -> categoryFiltered.sortedByDescending { it.isVideo }
            "Images First" -> categoryFiltered.sortedBy { it.isVideo || it.isAudio || it.isStream }
            "Streams First" -> categoryFiltered.sortedByDescending { it.isStream }
            else -> categoryFiltered
        }
    }

    val isAllSelected = filteredItems.isNotEmpty() && selectedItemIds.containsAll(filteredItems.map { it.id })
    val filterOptions = listOf("Images", "Videos", "Audio", "Streams")

    BackHandler {
        if (showDimensionFilter) {
            showDimensionFilter = false
        } else {
            onBack()
        }
    }

    val siteDomain = extractorState.domain.ifBlank {
        runCatching { Uri.parse(targetUrl).host?.removePrefix("www.") }.getOrNull()
    }?.ifBlank { "pexels.com" } ?: "pexels.com"

    val categories = listOf(
        Triple("Images", Icons.Default.Image, imageCount),
        Triple("Videos", Icons.Default.Videocam, videoCount),
        Triple("Audio", Icons.Default.MusicNote, audioCount),
        Triple("Streams", Icons.Default.SmartDisplay, streamCount)
    )

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize().testTag("downloader_results_page")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Browser Bar: Single rounded horizontal bar with navigation and domain
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color(0xFFF1F3F5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("downloader_top_browser_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Back Button
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(40.dp).testTag("downloader_results_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF212529)
                            )
                        }

                        // Website Domain in center
                        Text(
                            text = siteDomain,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Normal,
                            fontSize = 16.sp,
                            color = Color(0xFF212529),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 6.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Dynamic Status Indicator (Circle spinner while loading, subtle hollow circle when done)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .padding(end = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (extractorState.isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF6C757D)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .border(1.8.dp, Color(0xFFADB5BD), CircleShape)
                                )
                            }
                        }

                        // Refresh Button
                        IconButton(
                            onClick = {
                                selectedItemIds = emptySet()
                                dynamicDimensions.clear()
                                dualExtractor.extract(targetUrl, scope)
                            },
                            modifier = Modifier.size(38.dp).testTag("downloader_refresh_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color(0xFF212529),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // External Browser Button
                        IconButton(
                            onClick = {
                                if (onOpenBrowser != null) {
                                    onOpenBrowser(targetUrl)
                                } else {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    runCatching { context.startActivity(intent) }
                                }
                            },
                            modifier = Modifier.size(38.dp).testTag("downloader_browser_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open in browser",
                                tint = Color(0xFF212529),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 2. Media Category Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Pills (Images, Videos, Audio, Streams)
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(categories) { (categoryName, icon, count) ->
                        val isSelected = selectedFilter == categoryName
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color(0xFF212529) else Color(0xFFE9ECEF),
                            modifier = Modifier
                                .clickable {
                                    selectedFilter = if (isSelected) null else categoryName
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = categoryName,
                                    tint = if (isSelected) Color.White else Color(0xFF212529),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "$count",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = if (isSelected) Color.White else Color(0xFF212529)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Vertical Divider between categories and utilities
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(Color(0xFFDEE2E6))
                )

                Spacer(Modifier.width(8.dp))

                // Grouped Circular Controls: Sort, Filter, Select All
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sort Control
                    Box {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE9ECEF),
                            modifier = Modifier
                                .size(34.dp)
                                .clickable { sortDropdownExpanded = true }
                                .testTag("downloader_sort_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = "Sort",
                                    tint = if (sortOption != "Default") Color(0xFF212529) else Color(0xFF495057),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = sortDropdownExpanded,
                            onDismissRequest = { sortDropdownExpanded = false }
                        ) {
                            listOf("Default", "Title (A-Z)", "Videos First", "Images First", "Streams First").forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt, fontWeight = if (sortOption == opt) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        sortOption = opt
                                        sortDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Dimension / Filter Control
                    Surface(
                        shape = CircleShape,
                        color = if (minWidthFilter > 0 || minHeightFilter > 0 || showSvg) Color(0xFF212529) else Color(0xFFE9ECEF),
                        modifier = Modifier
                            .size(34.dp)
                            .clickable { showDimensionFilter = !showDimensionFilter }
                            .testTag("downloader_filter_tune_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Filter dimensions & size",
                                tint = if (minWidthFilter > 0 || minHeightFilter > 0 || showSvg) Color.White else Color(0xFF495057),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Selection Checkbox Control
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFE9ECEF),
                        modifier = Modifier
                            .size(34.dp)
                            .clickable {
                                val ids = filteredItems.map { it.id }.toSet()
                                selectedItemIds = if (isAllSelected) selectedItemIds - ids else selectedItemIds + ids
                            }
                            .testTag("downloader_select_all_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isAllSelected) Icons.Default.Check else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = "Select All",
                                tint = Color(0xFF495057),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Dimension Filter Drawer (Animated)
            AnimatedVisibility(visible = showDimensionFilter) {
                Surface(
                    color = Color(0xFFF8F9FA),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE9ECEF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Dimension & Size Filter",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212529)
                            )
                            TextButton(onClick = {
                                minWidthFilter = 0f
                                minHeightFilter = 0f
                                showSvg = false
                                minKbInput = "1"
                            }) {
                                Text("Reset", color = Color(0xFF212529))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Min Width: ${if (minWidthFilter <= 0f) "Any" else "${minWidthFilter.toInt()}px"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF495057)
                            )
                        }
                        Slider(
                            value = minWidthFilter,
                            onValueChange = { minWidthFilter = it },
                            valueRange = 0f..3840f,
                            steps = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Min Height: ${if (minHeightFilter <= 0f) "Any" else "${minHeightFilter.toInt()}px"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF495057)
                            )
                        }
                        Slider(
                            value = minHeightFilter,
                            onValueChange = { minHeightFilter = it },
                            valueRange = 0f..2160f,
                            steps = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Show SVG images", style = MaterialTheme.typography.bodySmall, color = Color(0xFF495057))
                            Switch(checked = showSvg, onCheckedChange = { showSvg = it })
                        }
                    }
                }
            }

            // 3. Media Grid Area with Floating Centered Download Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (filteredItems.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp)
                            .testTag("downloader_results_grid"),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            DownloaderMediaCard(
                                item = item,
                                isSelected = item.id in selectedItemIds,
                                dynamicDimensions = dynamicDimensions,
                                onToggle = {
                                    selectedItemIds = if (item.id in selectedItemIds) {
                                        selectedItemIds - item.id
                                    } else {
                                        selectedItemIds + item.id
                                    }
                                }
                            )
                        }
                    }
                } else if (extractorState.isRunning) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp)
                    ) {
                        item {
                            ThreeColumnSkeletonGrid()
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
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = Color(0xFF6C757D),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No media discovered",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212529)
                            )
                            Text(
                                text = "The webpage might be dynamically protected, require login, or filter settings are excluding items.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6C757D),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    if (onOpenBrowser != null) {
                                        onOpenBrowser(targetUrl)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF212529))
                            ) {
                                Icon(Icons.Default.TravelExplore, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Open in Live Browser")
                            }
                        }
                    }
                }

                // 6. Centered Floating Download Action Pill
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF212529),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                        .clickable(enabled = !isDownloading) {
                            if (selectedItemIds.isEmpty() || isDownloading) return@clickable
                            isDownloading = true
                            downloadProgress = 0.2f
                            downloadStatusText = "Preparing download batch..."
                            scope.launch {
                                val selectedItems = filteredItems.filter { it.id in selectedItemIds }
                                val deduplicated = MediaClassifier.deduplicateScrapedItems(selectedItems)
                                val stackId = "stack_${System.currentTimeMillis()}"
                                val stackTitle = extractorState.pageTitle.ifBlank { extractorState.domain.ifBlank { "Media Download" } }

                                DownloadQueueManager.enqueueBatch(
                                    context = context,
                                    items = deduplicated,
                                    referer = targetUrl,
                                    stackId = stackId,
                                    stackTitle = stackTitle,
                                    sourceDomain = extractorState.domain,
                                    sourceUrl = targetUrl
                                )

                                downloadProgress = 1.0f
                                downloadStatusText = "Queued ${deduplicated.size} item(s)"
                                delay(400)
                                isDownloading = false
                                onDownloadComplete(deduplicated.size)
                            }
                        }
                        .testTag("downloader_download_selected_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Download selected",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "(${selectedItemIds.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean, prominent media card conforming to the minimal Emre design language.
 * Format label top-left, selection checkbox top-right, filename below thumbnail,
 * dimensions bottom-left, media category bottom-right.
 */
@Composable
fun DownloaderMediaCard(
    item: ScrapedMediaItem,
    isSelected: Boolean,
    dynamicDimensions: Map<String, Pair<Int, Int>>,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val parsedDim = dynamicDimensions[item.id] ?: parseScrapedMediaDimensions(item).let { (w, h) ->
        if (w != null && h != null) (w to h) else null
    }
    val dimText = if (parsedDim != null && parsedDim.first > 0 && parsedDim.second > 0) {
        "${parsedDim.first} × ${parsedDim.second}"
    } else {
        item.resolutionHint ?: "40 × 40"
    }
    val categoryText = when {
        item.isVideo -> "Videos"
        item.isAudio -> "Audio"
        item.isStream -> "Streams"
        else -> "Images"
    }
    val formatText = item.format.uppercase().ifBlank {
        when {
            item.isVideo -> "MP4"
            item.isAudio -> "MP3"
            item.isStream -> "M3U8"
            else -> "JPEG"
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF1F3F5),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag("scraped_media_card_${item.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Thumbnail container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color(0xFFE9ECEF))
            ) {
                val previewUrl = item.thumbnailUrl ?: item.url
                AsyncImage(
                    model = previewUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Top-Left Format Pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White.copy(alpha = 0.92f),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .padding(start = 6.dp, top = 6.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = formatText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = Color(0xFF212529),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Top-Right Checkbox
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp, top = 6.dp)
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) Color(0xFF212529) else Color.Black.copy(alpha = 0.25f))
                        .border(
                            width = 1.5.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            // Info below thumbnail
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 7.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title.ifBlank { "media_${item.id.takeLast(6)}" },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = Color(0xFF212529),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dimText,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = Color(0xFF6C757D),
                        maxLines = 1
                    )
                    Text(
                        text = categoryText,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = Color(0xFF6C757D),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
