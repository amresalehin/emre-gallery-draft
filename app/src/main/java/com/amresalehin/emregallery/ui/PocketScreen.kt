package com.amresalehin.emregallery.ui
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.MediaClassifier
import com.amresalehin.emregallery.data.MediaInboxStack
import com.amresalehin.emregallery.data.PocketRepository
import java.util.*
@Composable
fun PocketScreen(
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 0.dp,
    onMenuClick: () -> Unit = {},
    onDetailViewerStateChange: ((Boolean) -> Unit)? = null,
    onOpenDiscoveredMedia: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val pocketRepo = remember { PocketRepository.getInstance(context) }
    val stacks by pocketRepo.bookmarks.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedStackIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedStackIds.isNotEmpty()
    var sortOrder by remember { mutableStateOf("Newest") } // Newest, Oldest, Title, Domain
    var filterType by remember { mutableStateOf("All") } // All, Web Links, Notes
    var showFilterSheet by remember { mutableStateOf(false) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var editingStack by remember { mutableStateOf<MediaInboxStack?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedStackForDetail by remember { mutableStateOf<MediaInboxStack?>(null) }
    var showFullScreenBrowser by remember { mutableStateOf(false) }
    var browserUrl by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    var clipboardUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isSearchFocused) {
        if (isSearchFocused) {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val clipText = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                if (MediaClassifier.isValidWebUrl(clipText) || clipText.startsWith("http://") || clipText.startsWith("https://")) {
                    clipboardUrl = clipText
                } else {
                    clipboardUrl = null
                }
            }
        }
    }
    val filteredStacks = remember(stacks, searchQuery, sortOrder, filterType) {
        var result = stacks.filter { stack ->
            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                stack.title.contains(searchQuery, ignoreCase = true) ||
                        stack.sourceDomain.contains(searchQuery, ignoreCase = true) ||
                        stack.sourceUrl.contains(searchQuery, ignoreCase = true) ||
                        stack.description.contains(searchQuery, ignoreCase = true)
            }
            val matchesFilter = when (filterType) {
                "Web Links" -> stack.sourceUrl.isNotBlank()
                "Notes" -> stack.description.isNotBlank()
                else -> true
            }
            matchesQuery && matchesFilter
        }
        result = when (sortOrder) {
            "Oldest" -> result.sortedBy { it.timestampMillis }
            "Title" -> result.sortedBy { it.title.lowercase() }
            "Domain" -> result.sortedBy { it.sourceDomain.lowercase() }
            else -> result.sortedByDescending { it.timestampMillis } // Newest
        }
        result
    }
    LaunchedEffect(selectedStackForDetail) {
        onDetailViewerStateChange?.invoke(selectedStackForDetail != null)
    }
    DisposableEffect(Unit) {
        onDispose {
            onDetailViewerStateChange?.invoke(false)
        }
    }
    BackHandler(enabled = !showFullScreenBrowser && (selectedStackForDetail != null || isSelectionMode || isSearchFocused || searchQuery.isNotEmpty() || showFilterSheet)) {
        when {
            showFullScreenBrowser -> showFullScreenBrowser = false
            selectedStackForDetail != null -> {
                selectedStackForDetail = null
                onDetailViewerStateChange?.invoke(false)
            }
            isSelectionMode -> selectedStackIds = emptySet()
            isSearchFocused || searchQuery.isNotEmpty() -> {
                searchQuery = ""
                isSearchFocused = false
                focusManager.clearFocus()
                keyboardController?.hide()
            }
            showFilterSheet -> showFilterSheet = false
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PocketThemeColors.background(isDark))
            .testTag("pocket_screen")
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = PocketThemeColors.background(isDark),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                PocketTopBarSurface(
                    isDark = isDark,
                    isSelectionMode = isSelectionMode,
                    selectedCount = selectedStackIds.size,
                    totalCount = filteredStacks.size,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onSearchFocusChange = { isSearchFocused = it },
                    clipboardUrl = clipboardUrl,
                    onClearClipboardUrl = { clipboardUrl = null },
                    pocketRepo = pocketRepo,
                    snackbarHostState = snackbarHostState,
                    onMenuClick = onMenuClick,
                    onOpenFilterSheet = { showFilterSheet = true },
                    onOpenAddBookmark = { showAddBookmarkDialog = true },
                    onSelectAll = { selectedStackIds = filteredStacks.map { it.id }.toSet() },
                    onClearSelection = { selectedStackIds = emptySet() },
                    onRequestDeleteSelected = { showDeleteConfirmDialog = true }
                )
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
                            .padding(horizontal = 12.dp)
                            .testTag("pocket_bookmarks_grid"),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalItemSpacing = 14.dp
                    ) {
                        itemsIndexed(filteredStacks, key = { _, stack -> stack.id }) { index, stack ->
                            val isSelected = stack.id in selectedStackIds
                            PocketBookmarkStaggeredCard(
                                stack = stack,
                                index = index,
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
                                    selectedStackIds = if (isSelectionMode) {
                                        if (isSelected) selectedStackIds - stack.id else selectedStackIds + stack.id
                                    } else {
                                        setOf(stack.id)
                                    }
                                },
                                isDark = isDark,
                                modifier = Modifier.testTag("pocket_bookmark_card_${stack.id}")
                            )
                        }
                    }
                } else {
                    PocketEmptyState(
                        searchQuery = searchQuery,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        PocketOverlaysAndDialogs(
            isDark = isDark,
            pocketRepo = pocketRepo,
            snackbarHostState = snackbarHostState,
            selectedStackForDetail = selectedStackForDetail,
            onSelectedStackForDetailChange = { selectedStackForDetail = it },
            onDetailViewerStateChange = onDetailViewerStateChange,
            showFullScreenBrowser = showFullScreenBrowser,
            onShowFullScreenBrowserChange = { showFullScreenBrowser = it },
            browserUrl = browserUrl,
            onBrowserUrlChange = { browserUrl = it },
            onMenuClick = onMenuClick,
            onOpenDiscoveredMedia = onOpenDiscoveredMedia,
            showAddBookmarkDialog = showAddBookmarkDialog,
            onShowAddBookmarkDialogChange = { showAddBookmarkDialog = it },
            editingStack = editingStack,
            onEditingStackChange = { editingStack = it },
            showDeleteConfirmDialog = showDeleteConfirmDialog,
            onShowDeleteConfirmDialogChange = { showDeleteConfirmDialog = it },
            selectedStackIds = selectedStackIds,
            onSelectedStackIdsChange = { selectedStackIds = it },
            showFilterSheet = showFilterSheet,
            onShowFilterSheetChange = { showFilterSheet = it },
            sortOrder = sortOrder,
            onSortOrderChange = { sortOrder = it },
            filterType = filterType,
            onFilterTypeChange = { filterType = it }
        )
    }
}
