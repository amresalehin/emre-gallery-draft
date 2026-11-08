package com.amresalehin.emregallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amresalehin.emregallery.data.*
import java.util.*


@Composable
internal fun TypingLiveSuggestionsContent(
    query: String,
    suggestions: List<SearchSuggestion>,
    onSelectSuggestion: (SearchSuggestion) -> Unit,
    onSubmitQuery: () -> Unit,
    isDark: Boolean = false
) {
    val textColor = searchPrimaryTextColor(isDark)
    val secondaryText = searchSecondaryTextColor(isDark)
    val cardSurface = searchCardSurfaceColor(isDark)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Direct Query Action Row
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSubmitQuery)
                    .testTag("submit_query_row"),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = textColor
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = query,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Search photos, locations, and text",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryText
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.NorthWest,
                        contentDescription = "Submit search",
                        tint = secondaryText.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Live Suggestion Rows (Grouped or ordered cleanly, max 1-2 per category)
        items(suggestions, key = { it.id }) { suggestion ->
            SearchSuggestionRow(
                suggestion = suggestion,
                onClick = { onSelectSuggestion(suggestion) },
                isDark = isDark
            )
        }
    }
}

/**
 * State C: Submitted Search Results.
 * Date-grouped masonry photo grid, Sort/View controls beside results header.
 */
@Composable
internal fun SubmittedResultsContent(
    query: String,
    resultsData: SearchResultData,
    sortMode: SearchSortMode,
    onSortChange: (SearchSortMode) -> Unit,
    viewMode: SearchViewMode,
    onViewModeChange: (SearchViewMode) -> Unit,
    hasActiveFilters: Boolean,
    onResetFilters: () -> Unit,
    collapsedGroups: Map<Long, Boolean>,
    onToggleGroupCollapse: (Long) -> Unit,
    onOpenMedia: (MediaItem) -> Unit,
    isDark: Boolean = false
) {
    val results = resultsData.items
    val totalCount = results.size

    if (totalCount == 0) {
        SearchEmptyState(
            query = query,
            hasActiveFilters = hasActiveFilters,
            onResetFilters = onResetFilters,
            isDark = isDark
        )
        return
    }

    // Date grouping for results
    val dayGroups = remember(results) { groupMediaByDay(results) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Results Header: Query title, count, content summary, Sort & View controls
        item {
            SearchResultsHeader(
                queryTitle = query.ifBlank { "All results" },
                totalCount = totalCount,
                photosCount = resultsData.totalPhotosCount,
                videosCount = resultsData.totalVideosCount,
                sortMode = sortMode,
                onSortChange = onSortChange,
                viewMode = viewMode,
                onViewModeChange = onViewModeChange,
                isDark = isDark
            )
        }

        // Date Groups
        items(dayGroups, key = { it.dayKey }) { group ->
            val isCollapsed = collapsedGroups[group.dayKey] == true

            Column(modifier = Modifier.fillMaxWidth()) {
                SearchDateGroupHeader(
                    label = group.dayLabel,
                    count = group.items.size,
                    isExpanded = !isCollapsed,
                    onToggleExpand = { onToggleGroupCollapse(group.dayKey) },
                    isDark = isDark
                )

                AnimatedVisibility(
                    visible = !isCollapsed,
                    enter = expandVertically(tween(200)) + fadeIn(tween(150)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
                ) {
                    when (viewMode) {
                        SearchViewMode.MASONRY -> {
                            // Natural varied image heights, narrow 4dp gutters, rounded corners
                            DynamicMasonryGrid(
                                media = group.items,
                                columnsCount = 3,
                                onOpen = onOpenMedia
                            )
                        }
                        SearchViewMode.GRID -> {
                            SquareGrid(
                                media = group.items,
                                columnsCount = 3,
                                onOpen = onOpenMedia
                            )
                        }
                        SearchViewMode.LARGE_GRID -> {
                            SquareGrid(
                                media = group.items,
                                columnsCount = 2,
                                onOpen = onOpenMedia
                            )
                        }
                        SearchViewMode.LIST -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                group.items.forEach { item ->
                                    CompactMediaListRow(
                                        item = item,
                                        onOpen = { onOpenMedia(item) },
                                        isDark = isDark
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

@Composable


