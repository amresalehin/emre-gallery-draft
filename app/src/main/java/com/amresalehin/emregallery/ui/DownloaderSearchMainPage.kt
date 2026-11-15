package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.amresalehin.emregallery.data.BrowserHistoryManager
import com.amresalehin.emregallery.data.DownloadActivityManager
import com.amresalehin.emregallery.data.MediaInboxRepository
import com.amresalehin.emregallery.data.MediaInboxStack
import kotlinx.coroutines.launch
import java.net.URLEncoder

/**
 * Minimal Search & URL Fetch Page for the Media Downloader.
 * Combines the unified capsule search input, dynamic site history pills,
 * Activity preview card, and Saved preview section.
 */
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
    }

    var urlInput by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    fun submitSearch(target: String) {
        val normalized = normalizeDownloadUrl(target) ?: return
        keyboardController?.hide()
        focusManager.clearFocus()
        scope.launch {
            historyManager.recordVisit(normalized)
        }
        onFetchUrl(normalized)
    }

    fun openInBrowser(queryOrUrl: String) {
        val destinationUrl = resolveBrowserDestinationUrl(queryOrUrl) ?: return
        keyboardController?.hide()
        focusManager.clearFocus()
        scope.launch {
            historyManager.recordVisit(destinationUrl)
        }
        onOpenBrowser(destinationUrl)
    }

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
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("downloader_search_main_page"),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DownloaderSearchBar(
            urlInput = urlInput,
            isSearchFocused = isSearchFocused,
            onUrlInputChange = { urlInput = it },
            onSearchFocusChange = { isSearchFocused = it },
            onBackClick = onBack,
            onClearEditAndFocus = {
                urlInput = ""
                isSearchFocused = false
                focusManager.clearFocus()
                keyboardController?.hide()
            },
            onViewSavedLibrary = onViewSavedLibrary,
            onSubmitSearch = ::submitSearch,
            onOpenInBrowser = ::openInBrowser,
            onPasteAndFetch = {
                val clipText = clipboardManager.getText()?.text?.trim().orEmpty()
                if (clipText.isNotBlank()) {
                    urlInput = clipText
                    submitSearch(clipText)
                }
            }
        )

        if (pills.isNotEmpty()) {
            DownloaderRecentPillsRow(
                pills = pills,
                onPillClick = { pill ->
                    urlInput = pill.url
                    submitSearch(pill.url)
                }
            )
        }

        ActivityPreviewCard(
            activities = activities,
            onOpenActivity = onOpenActivity,
            modifier = Modifier.fillMaxWidth()
        )

        SavedPreviewSection(
            stacks = effectiveStacks,
            onViewSavedLibrary = onViewSavedLibrary,
            onSelectStack = onSelectStack,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DownloaderRecentPillsRow(
    pills: List<DownloaderPill>,
    onPillClick: (DownloaderPill) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("downloader_recent_pills_row"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(pills, key = { it.domain + it.url }) { pill ->
            Surface(
                onClick = { onPillClick(pill) },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .height(38.dp)
                    .testTag("recent_pill_${pill.name}")
            ) {
                Row(
                    modifier = Modifier.padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                                        .listener(onError = { _, _ -> iconFailed = true })
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

private fun normalizeDownloadUrl(target: String): String? {
    val trimmed = target.trim()
    if (trimmed.isBlank()) return null
    return if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
        "https://$trimmed"
    } else {
        trimmed
    }
}

private fun resolveBrowserDestinationUrl(queryOrUrl: String): String? {
    val trimmed = queryOrUrl.trim()
    if (trimmed.isBlank()) return null
    return if (trimmed.contains(".") && !trimmed.contains(" ") &&
        (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) ||
            trimmed.matches(Regex("^[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}(/.*)?$")))
    ) {
        if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
    } else {
        "https://www.google.com/search?q=" + URLEncoder.encode(trimmed, "UTF-8")
    }
}
