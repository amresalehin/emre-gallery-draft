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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
    onOpenActivity: () -> Unit = {},
    onBack: () -> Unit = {},
    savedStacks: List<MediaInboxStack> = emptyList(),
    onSelectStack: (MediaInboxStack) -> Unit = {},
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
    val inboxRepo = remember { MediaInboxRepository.getInstance(context) }
    val repoStacks by inboxRepo.stacks.collectAsState()
    val effectiveStacks = remember(savedStacks, repoStacks) {
        if (savedStacks.isNotEmpty()) savedStacks else repoStacks
    }

    val activities by DownloadActivityManager.activities.collectAsState()
    LaunchedEffect(Unit) {
        DownloadActivityManager.initialize(context)
        DownloadActivityManager.syncWithInboxRepository()
        com.amresalehin.emregallery.GalleryApplication.warmupChromium(context)
    }

    var urlInput by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

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
            historyManager.recordSearchQuery(trimmed)
            historyManager.recordVisit(normalized)
        }
        onFetchUrl(normalized)
    }

    var showSupportedSitesSheet by remember { mutableStateOf(false) }

    fun openInBrowser(queryOrUrl: String) {
        val trimmed = queryOrUrl.trim()
        keyboardController?.hide()
        focusManager.clearFocus()
        if (trimmed.isBlank()) {
            onOpenBrowser("")
            return
        }
        val destinationUrl = if (trimmed.contains(".") && !trimmed.contains(" ") &&
            (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) ||
                    trimmed.matches(Regex("^[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}(/.*)?$")))) {
            if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) trimmed else "https://$trimmed"
        } else {
            "https://www.google.com/search?q=" + java.net.URLEncoder.encode(trimmed, "UTF-8")
        }
        scope.launch {
            historyManager.recordSearchQuery(trimmed)
            historyManager.recordVisit(destinationUrl)
        }
        onOpenBrowser(destinationUrl)
    }

    val recentSearches by historyManager.recentSearchQueries.collectAsState(initial = emptyList())

    // 5 most searched/visited site favicons
    val topFiveSites = remember(allHistorySites) {
        val seenDomains = mutableSetOf<String>()
        val result = mutableListOf<DownloaderPill>()
        val sorted = allHistorySites.sortedWith(
            compareByDescending<BrowserSite> { it.visitCount }
                .thenByDescending { it.lastVisitedMillis }
        )
        for (site in sorted) {
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
        result.take(5)
    }

    // Recent 3 searches
    val recentThreeSearches = remember(recentSearches, allHistorySites) {
        val list = mutableListOf<String>()
        for (q in recentSearches) {
            val trimmed = q.trim()
            if (trimmed.isNotBlank() && list.none { it.equals(trimmed, ignoreCase = true) }) {
                list.add(trimmed)
            }
        }
        // If recentSearches has fewer than 3, backfill from visited site titles/urls
        if (list.size < 3) {
            for (site in allHistorySites) {
                val candidate = site.title.takeIf { it.isNotBlank() && it != "about:blank" && !it.startsWith("http") }
                    ?: site.domain.removePrefix("www.")
                if (candidate.isNotBlank() && list.none { it.equals(candidate, ignoreCase = true) }) {
                    list.add(candidate)
                    if (list.size == 3) break
                }
            }
        }
        list.take(3)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("downloader_search_main_page"),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. One Unified Search Bar on Main Page (Back button & Switcher pills inside search bar, link icon removed)
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("downloader_main_search_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button inside search bar (exits search edit if focused/query present, or navigates back)
                IconButton(
                    onClick = {
                        if (isSearchFocused || urlInput.isNotEmpty()) {
                            urlInput = ""
                            isSearchFocused = false
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("downloader_main_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Switcher Pill & Divider: hidden when search field is focused
                if (!isSearchFocused) {
                    Spacer(Modifier.width(2.dp))

                    // Switcher Pill: download icon only and folder icon only
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Fetch Pill (Active on main search page)
                            Surface(
                                onClick = { /* already on search page */ },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .size(width = 34.dp, height = 30.dp)
                                    .testTag("tab_search_fetch")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Fetch",
                                        tint = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Saved Pill (Inactive on main search page, switches to Saved Library)
                            Surface(
                                onClick = onViewSavedLibrary,
                                shape = RoundedCornerShape(14.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .size(width = 34.dp, height = 30.dp)
                                    .testTag("tab_saved_library")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Saved",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Divider between switcher and search field
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Search / URL input field (Link icon removed!)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (urlInput.isEmpty()) {
                        Text(
                            text = "Paste link or enter URL...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                isSearchFocused = focusState.isFocused
                            }
                            .testTag("downloader_main_url_input")
                    )
                }

                Spacer(Modifier.width(6.dp))

                // Action Controls: Always show Browser button (whether empty or query present, and whether focused or not).
                // Paste button removed.
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        // Always show browser button even when search bar is empty and not focused
                        IconButton(
                            onClick = { openInBrowser("") },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("downloader_action_browse_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.TravelExplore,
                                contentDescription = "Open browser homepage",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Recent 3 searches: one in a row. If empty show divider lines.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 0 until 3) {
                val query = recentThreeSearches.getOrNull(i)
                if (query != null) {
                    Surface(
                        onClick = {
                            urlInput = query
                            submitSearch(query)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("recent_search_row_$i")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = query,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.NorthWest,
                                contentDescription = "Select query",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    // Empty state: clean subtle divider lines
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("recent_search_empty_divider_$i"),
                        contentAlignment = Alignment.Center
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            thickness = 1.dp
                        )
                    }
                }
            }
        }

        // 3. One row with 5 most searched site favicons
        if (topFiveSites.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("top_favicons_row"),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                topFiveSites.forEach { site ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clickable {
                                urlInput = site.url
                                submitSearch(site.url)
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .testTag("top_favicon_${site.name}")
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                var iconFailed by remember(site.faviconUrl) { mutableStateOf(false) }
                                if (!iconFailed) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(site.faviconUrl)
                                            .crossfade(true)
                                            .listener(
                                                onError = { _, _ -> iconFailed = true }
                                            )
                                            .build(),
                                        contentDescription = site.name,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = site.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            // Subtle horizontal placeholder divider when no sites have been searched yet
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    thickness = 1.dp
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        // 3. Activity Card matching image_4fd87912.png
        ActivityPreviewCard(
            activities = activities,
            onOpenActivity = onOpenActivity,
            modifier = Modifier.fillMaxWidth()
        )

        // 4. Saved Preview Section matching image_1d9653bc.png layout with real data
        SavedPreviewSection(
            stacks = effectiveStacks,
            onViewSavedLibrary = onViewSavedLibrary,
            onSelectStack = onSelectStack,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showSupportedSitesSheet) {
        SupportedSitesBottomSheet(
            onDismiss = { showSupportedSitesSheet = false },
            onSelectSite = { site ->
                showSupportedSitesSheet = false
                urlInput = site.exampleUrl
                submitSearch(site.exampleUrl)
            },
            onBrowseSite = { site ->
                showSupportedSitesSheet = false
                openInBrowser(site.exampleUrl)
            }
        )
    }
}

/**
 * Saved Preview Section matching the layout of image_1d9653bc.png:
 * - Rounded card container with cloud download icon, "Saved" bold title, and "See All >" button
 * - Horizontal scroll of real stacked media cards with colored layer effect
 * - Item count badge on top-right of image, bold title, formatted timestamp
 * - Centered indicator dots below cards
 */
@Composable
fun SavedPreviewSection(
    stacks: List<MediaInboxStack>,
    onViewSavedLibrary: () -> Unit,
    onSelectStack: (MediaInboxStack) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val listState = rememberLazyListState()
    val containerShape = RoundedCornerShape(24.dp)

    Surface(
        shape = containerShape,
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.65f else 0.75f)
        ),
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = containerShape,
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.12f),
                spotColor = if (isDark) Color.Black.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.18f)
            )
            .testTag("downloader_saved_preview_section")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp, bottom = 20.dp, start = 18.dp, end = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: [Cloud Icon] Saved       [See All >]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Saved",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Saved",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    onClick = onViewSavedLibrary,
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.testTag("downloader_saved_see_all_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "See All",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            val displayStacks = remember(stacks) { stacks.take(5) }
            if (displayStacks.isNotEmpty()) {
                // Horizontal list of stacked preview cards (bounded to max 5 items to avoid unlimited horizontal scrolling)
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("saved_preview_cards_row"),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
                ) {
                    itemsIndexed(displayStacks, key = { _, item -> item.id }) { index, stack ->
                        SavedStackPreviewCard(
                            stack = stack,
                            accentIndex = index,
                            onClick = { onSelectStack(stack) }
                        )
                    }
                }
            } else {
                // Clean empty state with no text or explanation matching image_6118052c.png
                val placeholderColor = if (isDark) {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                } else {
                    Color(0xFFE8E8EE)
                }
                val placeholderSubColor = if (isDark) {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f)
                } else {
                    Color(0xFFEEEEF4)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp)
                        .clickable(onClick = onViewSavedLibrary)
                        .testTag("saved_empty_state_container"),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    repeat(2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Rounded thumbnail placeholder
                            Box(
                                modifier = Modifier
                                    .size(width = 86.dp, height = 52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(placeholderColor)
                            )

                            // Title & subtitle line placeholders
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.72f)
                                        .height(11.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(placeholderColor)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.44f)
                                        .height(9.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(placeholderSubColor)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual Saved Stack Preview Card:
 * - Stacked layered cards effect behind the main card with pastel accents
 * - Rounded cover image with item count badge
 * - Stack title and formatted timestamp
 */
@Composable
fun SavedStackPreviewCard(
    stack: MediaInboxStack,
    accentIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val layerColors = remember(accentIndex, isDark) {
        val palettes = listOf(
            // Lavender / Indigo (as in Taj Mahal card)
            if (isDark) listOf(Color(0xFF312E81).copy(alpha = 0.6f), Color(0xFF3730A3).copy(alpha = 0.45f))
            else listOf(Color(0xFFEDE9FE), Color(0xFFDDD6FE)),
            // Warm Amber / Peach (as in Ancient India card)
            if (isDark) listOf(Color(0xFF78350F).copy(alpha = 0.6f), Color(0xFF92400E).copy(alpha = 0.45f))
            else listOf(Color(0xFFFEF3C7), Color(0xFFFDE68A)),
            // Rose / Pink (as in Indian Architecture card)
            if (isDark) listOf(Color(0xFF831843).copy(alpha = 0.6f), Color(0xFF9D174D).copy(alpha = 0.45f))
            else listOf(Color(0xFFFCE7F3), Color(0xFFFBCFE8)),
            // Soft Teal / Cyan
            if (isDark) listOf(Color(0xFF164E63).copy(alpha = 0.6f), Color(0xFF155E75).copy(alpha = 0.45f))
            else listOf(Color(0xFFCFFAFE), Color(0xFFA5F3FC)),
            // Soft Emerald
            if (isDark) listOf(Color(0xFF064E3B).copy(alpha = 0.6f), Color(0xFF065F46).copy(alpha = 0.45f))
            else listOf(Color(0xFFD1FAE5), Color(0xFFA7F3D0))
        )
        palettes[accentIndex % palettes.size]
    }

    val cardWidth = 176.dp
    val cardHeight = 210.dp
    val cardShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .width(cardWidth + 12.dp)
            .height(cardHeight + 12.dp)
            .clickable(onClick = onClick)
            .testTag("saved_preview_stack_${stack.id}")
    ) {
        // Layer 2 (Backmost stacked card)
        Box(
            modifier = Modifier
                .offset(x = 10.dp, y = 10.dp)
                .width(cardWidth)
                .height(cardHeight)
                .background(layerColors[1], shape = cardShape)
                .border(1.dp, layerColors[1].copy(alpha = 0.85f), shape = cardShape)
        )

        // Layer 1 (Middle stacked card)
        Box(
            modifier = Modifier
                .offset(x = 5.dp, y = 5.dp)
                .width(cardWidth)
                .height(cardHeight)
                .background(layerColors[0], shape = cardShape)
                .border(1.dp, layerColors[0].copy(alpha = 0.9f), shape = cardShape)
        )

        // Front Main Card
        Surface(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight),
            shape = cardShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = if (isDark) 1.dp else 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                // Thumbnail Box with item count badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(116.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    val coverItem = stack.items.firstOrNull { !it.isVideo && it.localUri.isNotBlank() }
                        ?: stack.items.firstOrNull { it.localUri.isNotBlank() }
                        ?: stack.items.firstOrNull()

                    val imageModel = coverItem?.localUri?.takeIf { it.isNotBlank() }
                        ?: coverItem?.sourceUrl?.takeIf { it.isNotBlank() }

                    if (imageModel != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageModel)
                                .crossfade(true)
                                .build(),
                            contentDescription = stack.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Count Badge in top-right (e.g. 16, 24, 8)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xCC1A1A1A),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .testTag("saved_preview_count_badge")
                    ) {
                        Text(
                            text = "${stack.items.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Title
                Text(
                    text = stack.title.ifBlank { "Saved Collection" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Spacer(Modifier.height(2.dp))

                // Formatted Date & Time (e.g. Nov 04, 2026 • 9:02 AM)
                val dateFormatted = remember(stack.timestampMillis) {
                    try {
                        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy • h:mm a", java.util.Locale.getDefault())
                        sdf.format(java.util.Date(stack.timestampMillis))
                    } catch (e: Exception) {
                        ""
                    }
                }
                Text(
                    text = dateFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
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

    // Active URL & Editing State
    var activeUrl by remember(targetUrl) { mutableStateOf(targetUrl) }
    var isEditingUrl by remember { mutableStateOf(false) }
    var hasAutoOpenedBrowser by remember(activeUrl) { mutableStateOf(false) }
    val cleanDisplayUrl = remember(activeUrl) {
        activeUrl.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
    }
    var urlTextFieldValue by remember(cleanDisplayUrl) {
        mutableStateOf(
            TextFieldValue(
                text = cleanDisplayUrl,
                selection = TextRange(cleanDisplayUrl.length)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Download & Checkbox dynamic states
    var isDownloaded by remember { mutableStateOf(false) }
    var downloadedItemIds by remember { mutableStateOf(setOf<String>()) }

    val activities by DownloadActivityManager.activities.collectAsState()
    val finishedMediaUrls = remember(activities) {
        activities.filter { it.status == DownloadActivityStatus.FINISHED }.map { it.mediaUrl }.toSet()
    }
    val finishedIds = remember(activities) {
        activities.filter { it.status == DownloadActivityStatus.FINISHED }.map { it.id }.toSet()
    }

    LaunchedEffect(isEditingUrl) {
        if (isEditingUrl) {
            urlTextFieldValue = TextFieldValue(
                text = cleanDisplayUrl,
                selection = TextRange(cleanDisplayUrl.length)
            )
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Dimension & SVG Filters
    var showDimensionFilter by remember { mutableStateOf(false) }
    var minWidthFilter by remember { mutableFloatStateOf(0f) }
    var minHeightFilter by remember { mutableFloatStateOf(0f) }
    var showSvg by remember { mutableStateOf(false) }
    var minKbInput by remember { mutableStateOf("1") }
    var showSupportedSitesSheet by remember { mutableStateOf(false) }

    val dynamicDimensions = remember { mutableStateMapOf<String, Pair<Int, Int>>() }

    // Trigger extraction on mount or URL change
    LaunchedEffect(activeUrl) {
        if (activeUrl.isNotBlank()) {
            hasAutoOpenedBrowser = false
            selectedItemIds = emptySet()
            downloadedItemIds = emptySet()
            isDownloaded = false
            dynamicDimensions.clear()
            DownloaderSession.startSession(activeUrl)
            dualExtractor.extract(activeUrl, scope)
        }
    }

    fun performFetch(target: String) {
        val trimmed = target.trim()
        if (trimmed.isNotBlank()) {
            val sanitized = MediaClassifier.sanitizeUrl(trimmed)
            hasAutoOpenedBrowser = false
            if (sanitized != activeUrl) {
                activeUrl = sanitized
            } else {
                selectedItemIds = emptySet()
                downloadedItemIds = emptySet()
                isDownloaded = false
                dynamicDimensions.clear()
                DownloaderSession.startSession(sanitized)
                dualExtractor.extract(sanitized, scope)
            }
        }
        isEditingUrl = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    fun performOpenInBrowser(target: String) {
        val trimmed = target.trim()
        if (trimmed.isNotBlank()) {
            val destinationUrl = MediaClassifier.formatQueryOrUrl(trimmed)
            isEditingUrl = false
            focusManager.clearFocus()
            keyboardController?.hide()
            if (onOpenBrowser != null) {
                onOpenBrowser(destinationUrl)
            } else {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(destinationUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
            }
        }
    }

    val sessionItems by DownloaderSession.discoveredItems.collectAsState()
    val allScrapedItems = remember(extractorState.items, sessionItems) {
        MediaNormalizer.deduplicate(extractorState.items + sessionItems)
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
                val probed = ImageDimensionProber.probe(item.url, referer = activeUrl)
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
        if (isEditingUrl) {
            isEditingUrl = false
            urlTextFieldValue = TextFieldValue(cleanDisplayUrl, selection = TextRange(cleanDisplayUrl.length))
            focusManager.clearFocus()
            keyboardController?.hide()
        } else if (showDimensionFilter) {
            showDimensionFilter = false
        } else {
            onBack()
        }
    }

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
            // 1. Top Browser Bar: Single rounded horizontal bar with navigation and address
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
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
                            onClick = {
                                if (isEditingUrl) {
                                    isEditingUrl = false
                                    urlTextFieldValue = TextFieldValue(cleanDisplayUrl, selection = TextRange(cleanDisplayUrl.length))
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                } else {
                                    onBack()
                                }
                            },
                            modifier = Modifier.size(40.dp).testTag("downloader_results_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Web Address Area: Tappable to edit, sets cursor to the end of typed text
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (!isEditingUrl) {
                                        urlTextFieldValue = TextFieldValue(
                                            text = cleanDisplayUrl,
                                            selection = TextRange(cleanDisplayUrl.length)
                                        )
                                        isEditingUrl = true
                                    }
                                }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (!isEditingUrl) {
                                Text(
                                    text = cleanDisplayUrl.ifBlank { "Paste link or enter URL..." },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth().testTag("downloader_results_address_display"),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                BasicTextField(
                                    value = urlTextFieldValue,
                                    onValueChange = { urlTextFieldValue = it },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Go
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            performFetch(urlTextFieldValue.text)
                                        }
                                    ),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester)
                                        .testTag("downloader_results_url_input")
                                )
                            }
                        }

                        // Trailing Controls:
                        // When editing/focused: show Clear, Open in Live Browser, AND Fetch button so user can fetch new sites or launch browser
                        if (isEditingUrl) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // 1. Clear / Cancel Button
                                IconButton(
                                    onClick = {
                                        if (urlTextFieldValue.text.isNotEmpty()) {
                                            urlTextFieldValue = TextFieldValue("", selection = TextRange(0))
                                        } else {
                                            isEditingUrl = false
                                            urlTextFieldValue = TextFieldValue(cleanDisplayUrl, selection = TextRange(cleanDisplayUrl.length))
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                        }
                                    },
                                    modifier = Modifier.size(34.dp).testTag("downloader_clear_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = if (urlTextFieldValue.text.isNotEmpty()) "Clear text" else "Cancel editing",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // 2. Launch in Live Browser Button
                                IconButton(
                                    onClick = {
                                        val target = urlTextFieldValue.text.ifBlank { activeUrl }
                                        performOpenInBrowser(target)
                                    },
                                    modifier = Modifier.size(34.dp).testTag("downloader_action_browse_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Open in browser",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                // 3. Fetch / Scrape new site directly on this page
                                Surface(
                                    onClick = {
                                        val target = urlTextFieldValue.text.ifBlank { activeUrl }
                                        performFetch(target)
                                    },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp).testTag("downloader_action_fetch_btn")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Fetch media",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // When not editing: Progress indicator morphs into Reload button once scraping finishes
                                if (extractorState.isRunning) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("downloader_progress_indicator"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            selectedItemIds = emptySet()
                                            dynamicDimensions.clear()
                                            dualExtractor.extract(activeUrl, scope)
                                        },
                                        modifier = Modifier.size(36.dp).testTag("downloader_refresh_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Reload",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // External Browser Redirect Button
                                IconButton(
                                    onClick = {
                                        performOpenInBrowser(activeUrl)
                                    },
                                    modifier = Modifier.size(36.dp).testTag("downloader_browser_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Open in browser",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
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
                                isDownloaded = false
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

            // Sign-in Required Card (Prompted on page when login is required by yt-dlp or gallery-dl)
            if (extractorState.requiresSignIn) {
                SignInRequiredCard(
                    loginUrl = extractorState.signInUrl ?: activeUrl,
                    domain = extractorState.domain,
                    onOpenBrowser = { urlToOpen ->
                        performOpenInBrowser(urlToOpen)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // Background Cookies Progress Banner
            if (extractorState.isObtainingCookiesInBackground) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bypassing bot protection in background...",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Obtaining session cookies to retry yt-dlp / gallery-dl extraction",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
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
                                isDownloaded = item.id in finishedIds || item.url in finishedMediaUrls,
                                dynamicDimensions = dynamicDimensions,
                                onToggle = {
                                    isDownloaded = false
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(44.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = extractorState.statusMessage.ifBlank { "Discovering media..." },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
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
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.widthIn(max = 420.dp)
                        ) {
                            val failureReason = extractorState.errorMessage?.ifBlank { null }
                                ?: extractorState.statusMessage.takeIf {
                                    it.contains("protect", true) || it.contains("challenge", true) ||
                                    it.contains("waf", true) || it.contains("restricted", true) ||
                                    it.contains("timeout", true) || it.contains("failed", true)
                                }
                            val isChallenge = failureReason?.let {
                                it.contains("protect", true) || it.contains("challenge", true) ||
                                it.contains("waf", true) || it.contains("bot", true) ||
                                it.contains("turnstile", true) || it.contains("captcha", true)
                            } == true

                            Icon(
                                imageVector = if (isChallenge) Icons.Default.Shield else Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = if (isChallenge) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (isChallenge) "Verification Required" else "No media discovered",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (failureReason != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isChallenge) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = failureReason,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isChallenge) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                        if (isChallenge) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "Open Live Browser once to resolve the challenge. Media will automatically appear here.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "The webpage might be dynamically protected, require login, or filter settings are excluding items.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (onOpenBrowser != null) {
                                            onOpenBrowser(activeUrl)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.TravelExplore, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isChallenge) "Verify in Live Browser" else "Open in Live Browser")
                                }

                                OutlinedButton(
                                    onClick = { showSupportedSitesSheet = true }
                                ) {
                                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Supported Sites")
                                }
                            }
                        }
                    }
                }

                // 6. Centered Floating Download Action Pill (Turns ticked on download, app default black and white)
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
                                val deduplicated = MediaNormalizer.deduplicate(selectedItems)
                                val stackId = "stack_${System.currentTimeMillis()}"
                                val stackTitle = extractorState.pageTitle.ifBlank { extractorState.domain.ifBlank { "Media Download" } }

                                DownloadQueueManager.enqueueBatch(
                                    context = context,
                                    items = deduplicated,
                                    referer = activeUrl,
                                    stackId = stackId,
                                    stackTitle = stackTitle,
                                    sourceDomain = extractorState.domain,
                                    sourceUrl = activeUrl
                                )

                                downloadProgress = 1.0f
                                downloadStatusText = "Queued ${deduplicated.size} item(s)"
                                delay(300)
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
                        } else if (isDownloaded) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Downloaded",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
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

    if (showSupportedSitesSheet) {
        SupportedSitesBottomSheet(
            onDismiss = { showSupportedSitesSheet = false },
            onSelectSite = { site ->
                showSupportedSitesSheet = false
                performFetch(site.exampleUrl)
            },
            onBrowseSite = { site ->
                showSupportedSitesSheet = false
                performOpenInBrowser(site.exampleUrl)
            }
        )
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
    modifier: Modifier = Modifier,
    isDownloaded: Boolean = false
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
    val formatText = if (!item.isVideo && item.format.contains("4K", ignoreCase = true)) {
        "JPG"
    } else {
        val raw = item.format.uppercase().trim()
        when {
            raw == "JPEG" -> "JPG"
            raw.isNotBlank() -> raw
            item.isVideo -> "MP4"
            item.isAudio -> "MP3"
            item.isStream -> "M3U8"
            else -> "JPG"
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

                // Top-Right Checkbox: Turns ticked on download with default black and white
                val isTicked = isSelected || isDownloaded
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp, top = 6.dp)
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isTicked) Color(0xFF212529) else Color.Black.copy(alpha = 0.25f))
                        .border(
                            width = 1.5.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isTicked) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = if (isDownloaded) "Downloaded" else "Selected",
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

/**
 * Clean on-page card displayed when yt-dlp or gallery-dl requires user authentication.
 * Conforms to the Emre design language with action buttons to sign in via the in-app browser.
 */
@Composable
fun SignInRequiredCard(
    loginUrl: String,
    domain: String,
    onOpenBrowser: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.testTag("downloader_sign_in_required_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sign-in required for ${domain.ifBlank { "this site" }}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "The content is protected behind an account login or age checkpoint.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Sign in using our in-app browser once. Your secure session cookies will be saved so yt-dlp and gallery-dl can download automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onOpenBrowser(loginUrl) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("downloader_sign_in_browser_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Login,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sign In in Browser", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Interactive bottom sheet displaying the official 2,000+ yt-dlp and gallery-dl supported sites catalog.
 * Supports instant search, category filtering (All, Video, Social, Images, Music, Art, Manga),
 * primary engine indicator badges, and direct test/browse actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportedSitesBottomSheet(
    onDismiss: () -> Unit,
    onSelectSite: (com.amresalehin.emregallery.data.SupportedSiteItem) -> Unit,
    onBrowseSite: (com.amresalehin.emregallery.data.SupportedSiteItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = remember {
        listOf("All", "Video", "Social", "Images", "Music", "Art", "Manga")
    }

    val sites = remember(searchQuery, selectedCategory) {
        com.amresalehin.emregallery.data.SupportedSitesRepository.querySupportedSites(
            query = searchQuery,
            category = selectedCategory
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("downloader_supported_sites_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Official Supported Sites",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "yt-dlp (Primary) & gallery-dl (Secondary) engines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search site, domain or engine (e.g. reddit, x, video)...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("downloader_supported_sites_search_input")
            )

            Spacer(Modifier.height(10.dp))

            // Category Filter Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Sites Count Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${sites.size} platforms available",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tap to fetch • Globe to browse",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.height(6.dp))

            // Sites List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("downloader_supported_sites_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(sites, key = { it.domain + it.name }) { site ->
                    Surface(
                        onClick = { onSelectSite(site) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = when (site.category) {
                                            "Video" -> Icons.Default.PlayArrow
                                            "Music" -> Icons.Default.MusicNote
                                            "Images", "Art" -> Icons.Default.Image
                                            "Social" -> Icons.Default.Share
                                            else -> Icons.Default.Language
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = site.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (site.engine.startsWith("yt-dlp")) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        }
                                    ) {
                                        Text(
                                            text = site.engine,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (site.engine.startsWith("yt-dlp")) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = site.domain,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = site.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { onBrowseSite(site) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TravelExplore,
                                    contentDescription = "Browse ${site.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

