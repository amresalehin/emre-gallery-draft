package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amresalehin.emregallery.data.*
import java.util.*

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.SearchAnimatedFilterStrip(
    stage: SearchStage,
    filterState: SearchFilterState,
    onFilterStateChange: (SearchFilterState) -> Unit,
    searchResultsData: SearchResultData,
    onRequestCustomDatePicker: () -> Unit,
    isDark: Boolean
) {
            // SINGLE SCROLLABLE ROW OF FILTER PILLS (State A, State B, and State C)
            // Hides as keyboard hides and appears as keyboard appears
            AnimatedVisibility(
                visible = WindowInsets.isImeVisible,
                enter = expandVertically(tween(200)) + fadeIn(tween(150)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    SearchFilterChipsRow(
                        filterState = filterState,
                        onFilterChange = onFilterStateChange,
                        dynamicChips = if (stage == SearchStage.RESULTS) searchResultsData.dynamicChips else emptyList(),
                        onToggleDynamicChip = { chip ->
                            // Toggle active dynamic entity chip
                            val nextFilter = when (chip.kind) {
                                ChipKind.ALBUM -> {
                                    if (filterState.selectedAlbumId == chip.value) {
                                        filterState.copy(selectedAlbumId = null)
                                    } else {
                                        filterState.copy(selectedAlbumId = chip.value)
                                    }
                                }
                                ChipKind.PERSON -> {
                                    if (filterState.selectedPersonId == chip.value) {
                                        filterState.copy(selectedPersonId = null)
                                    } else {
                                        filterState.copy(selectedPersonId = chip.value)
                                    }
                                }
                                ChipKind.PLACE -> {
                                    if (filterState.selectedPlace == chip.value) {
                                        filterState.copy(selectedPlace = null)
                                    } else {
                                        filterState.copy(selectedPlace = chip.value)
                                    }
                                }
                                ChipKind.TAG -> {
                                    if (filterState.selectedTag == chip.value) {
                                        filterState.copy(selectedTag = null)
                                    } else {
                                        filterState.copy(selectedTag = chip.value)
                                    }
                                }
                            }
                            onFilterStateChange(nextFilter)
                        },
                        onRequestCustomDatePicker = { onRequestCustomDatePicker() },
                        isDark = isDark
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // SCREEN BODY ACCORDING TO CURRENT STAGE
}

@Composable
internal fun ColumnScope.SearchStageBodyBox(
    stage: SearchStage,
    query: String,
    filterState: SearchFilterState,
    onFilterStateChange: (SearchFilterState) -> Unit,
    searchResultsData: SearchResultData,
    sortMode: SearchSortMode,
    onSortModeChange: (SearchSortMode) -> Unit,
    viewMode: SearchViewMode,
    onViewModeChange: (SearchViewMode) -> Unit,
    collapsedGroups: MutableMap<String, Boolean>,
    liveSuggestions: List<SearchSuggestion>,
    discoveryData: DiscoveryData?,
    recentSearches: List<String>,
    onClearRecent: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onOpenMedia: (MediaItem, List<MediaItem>) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
    isDark: Boolean
) {
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                when {
                    stage == SearchStage.RESULTS || (!filterState.isDefault && stage == SearchStage.DISCOVERY) -> {
                        // Display matching search results if in results stage or if any filter pills are active
                        SubmittedResultsContent(
                            query = query,
                            resultsData = searchResultsData,
                            sortMode = sortMode,
                            onSortChange = onSortModeChange,
                            viewMode = viewMode,
                            onViewModeChange = onViewModeChange,
                            hasActiveFilters = !filterState.isDefault,
                            onResetFilters = { onFilterStateChange(SearchFilterState()) },
                            collapsedGroups = collapsedGroups,
                            onToggleGroupCollapse = { key ->
                                collapsedGroups[key] = !(collapsedGroups[key] ?: false)
                            },
                            onOpenMedia = { item ->
                                onOpenMedia(item, searchResultsData.items)
                            },
                            isDark = isDark
                        )
                    }

                    stage == SearchStage.TYPING -> {
                        // STATE B: Typing / live suggestions
                        TypingLiveSuggestionsContent(
                            query = query,
                            suggestions = liveSuggestions,
                            onSelectSuggestion = { suggestion ->
                                if (suggestion.targetFilter != null) {
                                    onFilterStateChange(suggestion.targetFilter)
                                }
                                val q = suggestion.targetQuery ?: suggestion.title
                                onQueryChange(q)
                                onSubmitSearch(q)
                            },
                            onSubmitQuery = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onSubmitSearch(query.trim())
                            },
                            isDark = isDark
                        )
                    }

                    else -> {
                        // STATE A: Discovery content (Search opened, no query entered, no filter pills active)
                        DiscoveryContent(
                            discoveryData = discoveryData,
                            recentSearches = recentSearches,
                            onSelectRecent = { selectedQuery ->
                                onQueryChange(selectedQuery)
                                onSubmitSearch(selectedQuery)
                            },
                            onClearRecent = onClearRecent,
                            onSelectCategory = { category ->
                                if (category.filterAction != null) {
                                    onFilterStateChange(category.filterAction)
                                }
                                val q = category.queryAction ?: category.title
                                onQueryChange(q)
                                onSubmitSearch(q)
                            },
                            onSelectEntity = { entity ->
                                if (entity.filterAction != null) {
                                    onFilterStateChange(entity.filterAction)
                                }
                                val q = entity.queryAction ?: entity.title
                                onQueryChange(q)
                                onSubmitSearch(q)
                            },
                            isDark = isDark
                        )
                    }
                }
            }
}
