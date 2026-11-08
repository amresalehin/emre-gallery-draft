package com.amresalehin.emregallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    query: String,
    stage: SearchStage,
    allMedia: List<MediaItem>,
    recentSearches: List<String>,
    customAlbums: List<CustomAlbum>,
    customTagsMap: Map<String, Set<String>>,
    customPeopleMap: Map<String, Set<String>>,
    favourites: Set<String>,
    themePref: String = "system",
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onClearRecent: () -> Unit,
    onBack: () -> Unit,
    onOpenMedia: (MediaItem, List<MediaItem>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val isDark = when (themePref) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val searchEngine = remember { SearchEngine(context) }

    // Search & Filter State
    var filterState by remember { mutableStateOf(SearchFilterState()) }
    var sortMode by remember { mutableStateOf(SearchSortMode.RELEVANCE) }
    var viewMode by remember { mutableStateOf(SearchViewMode.MASONRY) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    // Collapsed Date Groups State for State C
    val collapsedGroups = remember { mutableStateMapOf<Long, Boolean>() }

    // Discovery Data for State A
    var discoveryData by remember { mutableStateOf<DiscoveryData?>(null) }

    // Live Suggestions for State B
    var liveSuggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }
    var suggestionsJob by remember { mutableStateOf<Job?>(null) }

    // Search Results for State C
    var searchResultsData by remember {
        mutableStateOf(SearchResultData(query = "", items = emptyList(), dynamicChips = emptyList(), totalPhotosCount = 0, totalVideosCount = 0))
    }

    // Load Discovery Data when entering Search / changing media
    LaunchedEffect(allMedia, customAlbums, customTagsMap, customPeopleMap, favourites) {
        discoveryData = searchEngine.getDiscoveryContent(
            media = allMedia,
            customAlbums = customAlbums,
            customTagsMap = customTagsMap,
            customPeopleMap = customPeopleMap,
            favourites = favourites
        )
    }

    // Auto-focus input when in Discovery or Typing mode
    LaunchedEffect(stage) {
        if (stage == SearchStage.DISCOVERY || stage == SearchStage.TYPING) {
            runCatching {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        } else {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    // Debounced Live Suggestions computation for State B
    LaunchedEffect(query, stage, allMedia, customAlbums, customTagsMap, customPeopleMap, recentSearches) {
        if (stage == SearchStage.TYPING && query.isNotBlank()) {
            suggestionsJob?.cancel()
            suggestionsJob = scope.launch {
                delay(80) // Debounce typing
                val suggestions = searchEngine.getLiveSuggestions(
                    query = query,
                    media = allMedia,
                    recentSearches = recentSearches,
                    customAlbums = customAlbums,
                    customTagsMap = customTagsMap,
                    customPeopleMap = customPeopleMap
                )
                liveSuggestions = suggestions
            }
        } else {
            liveSuggestions = emptyList()
        }
    }

    // Compute Search Results for State C or whenever query/filters/sort change
    LaunchedEffect(query, stage, filterState, sortMode, allMedia, customAlbums, customTagsMap, customPeopleMap, favourites) {
        if (stage == SearchStage.RESULTS || !filterState.isDefault) {
            val results = searchEngine.searchMedia(
                query = query,
                media = allMedia,
                filter = filterState,
                sortMode = sortMode,
                customAlbums = customAlbums,
                customTagsMap = customTagsMap,
                customPeopleMap = customPeopleMap,
                favourites = favourites
            )
            searchResultsData = results
        }
    }

    // Handle Back Press navigation cleanly
    BackHandler {
        when {
            !filterState.isDefault -> {
                filterState = SearchFilterState()
            }
            stage == SearchStage.RESULTS -> {
                // If in results, go back to typing/discovery
                if (query.isNotBlank()) {
                    onQueryChange(query)
                } else {
                    onBack()
                }
            }
            stage == SearchStage.TYPING -> {
                if (query.isNotEmpty()) {
                    onQueryChange("")
                } else {
                    onBack()
                }
            }
            stage == SearchStage.DISCOVERY -> {
                onBack()
            }
        }
    }

    val bgColor = searchBgColor(isDark)
    val textColor = searchPrimaryTextColor(isDark)
    val secondaryText = searchSecondaryTextColor(isDark)
    val cardSurface = searchCardSurfaceColor(isDark)
    val dividerColor = searchDividerColor(isDark)
    val selectedSurface = searchSelectedSurfaceColor(isDark)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(10.dp))

            // TOP SEARCH BAR CAPSULE (Present in All 3 States)
            SearchBarCapsule(
                query = query,
                onQueryChange = { newQuery ->
                    onQueryChange(newQuery)
                },
                onSearchSubmit = { submittedQuery ->
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onSubmitSearch(submittedQuery)
                },
                onBack = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    if (!filterState.isDefault) {
                        filterState = SearchFilterState()
                    } else if (stage == SearchStage.RESULTS) {
                        onQueryChange("")
                    } else {
                        onBack()
                    }
                },
                onOpenFilterSheet = { showFilterSheet = true },
                hasActiveFilters = !filterState.isDefault,
                focusRequester = focusRequester,
                isDark = isDark
            )

            SearchAnimatedFilterStrip(
                stage = stage,
                filterState = filterState,
                onFilterStateChange = { filterState = it },
                searchResultsData = searchResultsData,
                onRequestCustomDatePicker = { showDatePickerDialog = true },
                isDark = isDark
            )
            SearchStageBodyBox(
                stage = stage,
                query = query,
                filterState = filterState,
                onFilterStateChange = { filterState = it },
                searchResultsData = searchResultsData,
                sortMode = sortMode,
                onSortModeChange = { sortMode = it },
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                collapsedGroups = collapsedGroups,
                liveSuggestions = liveSuggestions,
                discoveryData = discoveryData,
                recentSearches = recentSearches,
                onClearRecent = onClearRecent,
                onQueryChange = onQueryChange,
                onSubmitSearch = onSubmitSearch,
                onOpenMedia = onOpenMedia,
                focusManager = focusManager,
                keyboardController = keyboardController,
                isDark = isDark
            )
        }
    }

    // Modal Advanced Filter Sheet
    if (showFilterSheet) {
        SearchAdvancedFilterSheet(
            filterState = filterState,
            onApplyFilter = { newFilter -> filterState = newFilter },
            onDismiss = { showFilterSheet = false },
            onRequestCustomDatePicker = {
                showFilterSheet = false
                showDatePickerDialog = true
            },
            isDark = isDark
        )
    }

    // Custom Date Range Dialog
    if (showDatePickerDialog) {
        CustomDateRangeDialog(
            onDismiss = { showDatePickerDialog = false },
            onDateRangeSelected = { startMillis, endMillis ->
                filterState = filterState.copy(
                    timeRange = TimeRangeFilter.Custom(startMillis, endMillis)
                )
                showDatePickerDialog = false
            },
            isDark = isDark
        )
    }
}

/**
 * Discovery Content for State A.
 * Strictly uses real app data. Omits empty sections!
 */
